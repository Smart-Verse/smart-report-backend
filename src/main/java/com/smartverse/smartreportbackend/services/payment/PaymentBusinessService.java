package com.smartverse.smartreportbackend.services.payment;

import com.smartverse.smartreportbackend.config.migration.DBMigration;
import com.smartverse.smartreportbackend.repository.payment.BillingDiscountCustomRepository;
import com.smartverse.smartreportbackend.repository.payment.PaymentEventInboxCustomRepository;
import com.smartverse.smartreportbackend.repository.payment.SubscriptionPaymentCustomRepository;
import com.smartverse.smartreportbackend.repository.plan.SubscriptionPlanCustomRepository;
import com.smartverse.smartreportbackend.repository.plan.TenantSubscriptionCustomRepository;
import com.smartverse.smartreportbackend_gen.authorization.exception.ServiceException;
import com.smartverse.smartreportbackend_gen.authorization.tenant.TenantContext;
import com.smartverse.smartreportbackend_gen.dtos.PaymentConfirmedEventDTO;
import com.smartverse.smartreportbackend_gen.dtos.PaymentHistoryItemDTO;
import com.smartverse.smartreportbackend_gen.endpoints.CreatePaymentLinkOutput;
import com.smartverse.smartreportbackend_gen.endpoints.GetPaymentHistoryOutput;
import com.smartverse.smartreportbackend_gen.entities.PaymentEventInboxEntity;
import com.smartverse.smartreportbackend_gen.entities.SubscriptionPaymentEntity;
import com.smartverse.smartreportbackend_gen.entities.TenantSubscriptionEntity;
import com.smartverse.smartreportbackend_gen.enums.BillingCycle;
import com.smartverse.smartreportbackend_gen.enums.PaymentStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class PaymentBusinessService {
    private static final String ADMIN_TENANT = "admin";

    private final SmartPaymentClient client;
    private final BillingDiscountCustomRepository discounts;
    private final SubscriptionPaymentCustomRepository payments;
    private final PaymentEventInboxCustomRepository inbox;
    private final SubscriptionPlanCustomRepository plans;
    private final TenantSubscriptionCustomRepository subscriptions;
    private final DBMigration migration;
    private final TransactionTemplate transactions;

    public PaymentBusinessService(SmartPaymentClient client,
                                  BillingDiscountCustomRepository discounts,
                                  SubscriptionPaymentCustomRepository payments,
                                  PaymentEventInboxCustomRepository inbox,
                                  SubscriptionPlanCustomRepository plans,
                                  TenantSubscriptionCustomRepository subscriptions,
                                  DBMigration migration,
                                  TransactionTemplate transactions) {
        this.client = client;
        this.discounts = discounts;
        this.payments = payments;
        this.inbox = inbox;
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.migration = migration;
        this.transactions = transactions;
    }

    public CreatePaymentLinkOutput createLink(String planCode, BillingCycle cycle, String authorization) {
        var tenant = requireTenant();
        if (authorization == null || authorization.isBlank()) {
            throw new ServiceException(HttpStatus.UNAUTHORIZED, "Token de autorização obrigatório");
        }
        var payment = inAdmin(() -> transactions.execute(status -> preparePayment(tenant, planCode, cycle)));
        var provider = client.createLink(authorization, payment.getAmountCents(), payment.getId());
        inAdmin(() -> transactions.execute(status -> {
            var stored = payments.findById(payment.getId()).orElseThrow();
            stored.setOrderNsu(provider.orderNsu());
            stored.setUpdatedAt(LocalDateTime.now());
            payments.save(stored);
            return null;
        }));
        var output = new CreatePaymentLinkOutput();
        output.url = provider.url();
        output.orderNsu = provider.orderNsu();
        output.status = provider.status();
        output.reused = provider.reused();
        return output;
    }

    public GetPaymentHistoryOutput history() {
        var tenant = requireTenant();
        return inAdmin(() -> transactions.execute(status -> {
            var output = new GetPaymentHistoryOutput();
            output.payments = payments.findAllByTenantOrderByCreatedAtDesc(tenant).stream()
                    .map(this::toHistoryItem)
                    .toList();
            return output;
        }));
    }

    public void receive(PaymentConfirmedEventDTO event) {
        validate(event);
        var alreadyProcessed = inAdmin(() -> transactions.execute(status -> registerEvent(event)));
        if (alreadyProcessed) return;
        try {
            inAdmin(() -> transactions.execute(status -> {
                applyPayment(event);
                return null;
            }));
        } catch (RuntimeException exception) {
            inAdmin(() -> transactions.execute(status -> {
                var stored = inbox.findByPaymentId(event.paymentId).orElseThrow();
                stored.setStatus("FAILED");
                stored.setFailureReason(safeMessage(exception));
                inbox.save(stored);
                return null;
            }));
            throw exception;
        }
    }

    public void retryFailedEvents() {
        var failedEvents = inAdmin(() -> transactions.execute(status -> inbox.findAllByStatus("FAILED")));
        failedEvents.forEach(stored -> {
            var event = new PaymentConfirmedEventDTO();
            event.paymentId = stored.getPaymentId();
            event.clientId = stored.getClientId();
            event.service = "REPORT";
            event.orderNsu = stored.getOrderNsu();
            event.transactionNsu = stored.getTransactionNsu();
            event.amount = stored.getAmount();
            event.paidAmount = stored.getPaidAmount();
            event.paidAt = stored.getPaidAt();
            try { receive(event); } catch (RuntimeException ignored) { }
        });
    }

    private SubscriptionPaymentEntity preparePayment(String tenant, String planCode, BillingCycle cycle) {
        if (planCode == null || planCode.isBlank() || cycle == null || "FREE".equalsIgnoreCase(planCode)) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "Plano e ciclo pagos são obrigatórios");
        }
        var plan = plans.findByCodeAndActiveTrue(planCode.toUpperCase())
                .orElseThrow(() -> new ServiceException(HttpStatus.NOT_FOUND, "Plano não encontrado"));
        if (plan.isCustomPlan() || plan.getMonthlyPrice() == null || plan.getMonthlyPrice() <= 0) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "Plano indisponível para checkout");
        }
        var discount = discounts.findByBillingCycleAndActiveTrue(cycle)
                .orElseThrow(() -> new ServiceException(HttpStatus.BAD_REQUEST, "Ciclo de cobrança indisponível"));
        return payments.findFirstByTenantAndPlanCodeAndBillingCycleAndStatusOrderByCreatedAtDesc(
                        tenant, plan.getCode(), cycle, PaymentStatus.PENDING)
                .orElseGet(() -> {
                    var monthly = BigDecimal.valueOf(plan.getMonthlyPrice());
                    var base = monthly.multiply(BigDecimal.valueOf(discount.getMonths()));
                    var multiplier = BigDecimal.ONE.subtract(
                            BigDecimal.valueOf(discount.getDiscountPercentage()).movePointLeft(2));
                    var total = base.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
                    var created = new SubscriptionPaymentEntity();
                    created.setTenant(tenant);
                    created.setPlanCode(plan.getCode());
                    created.setBillingCycle(cycle);
                    created.setMonths(discount.getMonths());
                    created.setBaseAmountCents(base.movePointRight(2).intValueExact());
                    created.setDiscountPercentage(discount.getDiscountPercentage());
                    created.setAmountCents(total.movePointRight(2).intValueExact());
                    created.setStatus(PaymentStatus.PENDING);
                    created.setCreatedAt(LocalDateTime.now());
                    created.setUpdatedAt(LocalDateTime.now());
                    return payments.save(created);
                });
    }

    private boolean registerEvent(PaymentConfirmedEventDTO event) {
        var existing = inbox.findByPaymentId(event.paymentId);
        if (existing.isPresent()) return "PROCESSED".equals(existing.get().getStatus());
        var entity = new PaymentEventInboxEntity();
        entity.setPaymentId(event.paymentId);
        entity.setClientId(event.clientId);
        entity.setOrderNsu(event.orderNsu);
        entity.setTransactionNsu(event.transactionNsu);
        entity.setStatus("RECEIVED");
        entity.setAmount(event.amount);
        entity.setPaidAmount(event.paidAmount);
        entity.setPaidAt(event.paidAt);
        entity.setReceivedAt(LocalDateTime.now());
        inbox.save(entity);
        return false;
    }

    private void applyPayment(PaymentConfirmedEventDTO event) {
        var payment = payments.findById(event.clientId)
                .orElseThrow(() -> new ServiceException(HttpStatus.NOT_FOUND, "Cobrança não encontrada"));
        if (payment.getStatus() == PaymentStatus.PAID) {
            markEventProcessed(event.paymentId);
            return;
        }
        if (payment.getOrderNsu() == null || !payment.getOrderNsu().equals(event.orderNsu)
                || !payment.getAmountCents().equals(event.amount)
                || event.paidAmount < payment.getAmountCents()) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "Pagamento não corresponde à cobrança");
        }

        var now = event.paidAt;
        var lastCoverageEnd = payments.findAllByTenantAndStatusOrderByCoverageEndAtDesc(
                        payment.getTenant(), PaymentStatus.PAID).stream()
                .map(SubscriptionPaymentEntity::getCoverageEndAt)
                .filter(value -> value != null && value.isAfter(now))
                .findFirst().orElse(now);
        var subscription = subscriptions.findByTenant(payment.getTenant()).orElseGet(() -> {
            var created = new TenantSubscriptionEntity();
            created.setTenant(payment.getTenant());
            created.setPlanCode("FREE");
            created.setStartedAt(now);
            return created;
        });
        var start = subscription.getExpiresAt() != null && subscription.getExpiresAt().isAfter(lastCoverageEnd)
                ? subscription.getExpiresAt() : lastCoverageEnd;
        var end = start.plusMonths(payment.getMonths());

        payment.setStatus(PaymentStatus.PAID);
        payment.setTransactionNsu(event.transactionNsu);
        payment.setPaidAt(now);
        payment.setCoverageStartAt(start);
        payment.setCoverageEndAt(end);
        payment.setUpdatedAt(LocalDateTime.now());
        payments.save(payment);

        if (!start.isAfter(now)) {
            subscription.setPlanCode(payment.getPlanCode());
            subscription.setBillingCycle(payment.getBillingCycle());
            subscription.setStartedAt(start);
            subscription.setExpiresAt(end);
            subscriptions.save(subscription);
        }
        markEventProcessed(event.paymentId);
    }

    private void markEventProcessed(UUID paymentId) {
        var stored = inbox.findByPaymentId(paymentId).orElseThrow();
        stored.setStatus("PROCESSED");
        stored.setFailureReason(null);
        stored.setProcessedAt(LocalDateTime.now());
        inbox.save(stored);
    }

    private PaymentHistoryItemDTO toHistoryItem(SubscriptionPaymentEntity payment) {
        var dto = new PaymentHistoryItemDTO();
        dto.id = payment.getId();
        dto.planCode = payment.getPlanCode();
        dto.billingCycle = payment.getBillingCycle();
        dto.amountCents = payment.getAmountCents();
        dto.status = payment.getStatus();
        dto.createdAt = payment.getCreatedAt();
        dto.paidAt = payment.getPaidAt();
        dto.coverageStartAt = payment.getCoverageStartAt();
        dto.coverageEndAt = payment.getCoverageEndAt();
        return dto;
    }

    private void validate(PaymentConfirmedEventDTO event) {
        if (event == null || event.paymentId == null || event.clientId == null
                || event.orderNsu == null || event.transactionNsu == null
                || event.amount == null || event.amount <= 0 || event.paidAmount == null
                || event.paidAmount <= 0 || event.paidAt == null || !"REPORT".equals(event.service)) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "Evento de pagamento inválido");
        }
    }

    private String requireTenant() {
        var tenant = TenantContext.getCurrentTenant();
        if (tenant == null || tenant.isBlank() || ADMIN_TENANT.equalsIgnoreCase(tenant)) {
            throw new ServiceException(HttpStatus.FORBIDDEN, "Tenant de cliente obrigatório");
        }
        return tenant;
    }

    private <T> T inAdmin(Supplier<T> operation) {
        var previous = TenantContext.getCurrentTenant();
        try {
            TenantContext.setCurrentTenant(ADMIN_TENANT);
            migration.loadMigrateTenants(ADMIN_TENANT);
            return operation.get();
        } finally {
            TenantContext.setCurrentTenant(previous);
        }
    }

    private String safeMessage(RuntimeException exception) {
        var message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName()
                : message.substring(0, Math.min(message.length(), 1000));
    }
}
