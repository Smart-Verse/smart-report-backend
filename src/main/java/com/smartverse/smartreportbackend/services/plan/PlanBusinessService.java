package com.smartverse.smartreportbackend.services.plan;

import com.potatotech.authorization.exception.ServiceException;
import com.potatotech.authorization.tenant.TenantContext;
import com.smartverse.smartreportbackend.config.migration.DBMigration;
import com.smartverse.smartreportbackend.repository.plan.*;
import com.smartverse.smartreportbackend_gen.dtos.ApiUsageHistoryItemDTO;
import com.smartverse.smartreportbackend_gen.dtos.PlanOptionDTO;
import com.smartverse.smartreportbackend_gen.endpoints.GetApiUsageHistoryOutput;
import com.smartverse.smartreportbackend_gen.endpoints.GetPlanOverviewOutput;
import com.smartverse.smartreportbackend_gen.entities.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.function.Supplier;

@Service
public class PlanBusinessService {
    private static final String ADMIN_TENANT = "admin";
    private static final String DEFAULT_PLAN = "FREE";

    private final SubscriptionPlanCustomRepository plans;
    private final TenantSubscriptionCustomRepository subscriptions;
    private final ApiMonthlyUsageCustomRepository usages;
    private final DBMigration migration;
    private final TransactionTemplate transactions;

    public PlanBusinessService(SubscriptionPlanCustomRepository plans,
                               TenantSubscriptionCustomRepository subscriptions,
                               ApiMonthlyUsageCustomRepository usages,
                               DBMigration migration,
                               TransactionTemplate transactions) {
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.usages = usages;
        this.migration = migration;
        this.transactions = transactions;
    }

    public GetPlanOverviewOutput overview() {
        var tenant = requireTenant();
        return inAdmin(() -> transactions.execute(status -> buildOverview(tenant)));
    }

    public GetApiUsageHistoryOutput history() {
        var tenant = requireTenant();
        return inAdmin(() -> transactions.execute(status -> {
            var output = new GetApiUsageHistoryOutput();
            output.history = usages.findAllByTenantOrderByPeriodDesc(tenant).stream()
                    .map(usage -> {
                        var item = new ApiUsageHistoryItemDTO();
                        item.period = usage.getPeriod();
                        item.amount = usage.getAmount();
                        return item;
                    })
                    .toList();
            return output;
        }));
    }

    public void consumeApiRequest(String tenant) {
        inAdmin(() -> transactions.execute(status -> {
            var plan = currentPlan(tenant);
            var period = YearMonth.now().toString();
            var usage = usages.findByTenantAndPeriod(tenant, period).orElseGet(() -> {
                var created = new ApiMonthlyUsageEntity();
                created.setTenant(tenant);
                created.setPeriod(period);
                created.setAmount(0);
                return created;
            });
            var limit = plan.getApiMonthlyLimit();
            if (limit != null && usage.getAmount() >= limit) {
                throw new ServiceException(HttpStatus.PAYMENT_REQUIRED,
                        "Monthly API consumption limit reached");
            }
            usage.setAmount(usage.getAmount() + 1);
            usages.save(usage);
            return null;
        }));
    }

    private GetPlanOverviewOutput buildOverview(String tenant) {
        var current = currentPlan(tenant);
        var period = YearMonth.now().toString();
        var used = usages.findByTenantAndPeriod(tenant, period).map(ApiMonthlyUsageEntity::getAmount).orElse(0);
        var output = new GetPlanOverviewOutput();
        output.currentPlan = toDTO(current, true);
        output.plans = plans.findAllByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(plan -> toDTO(plan, plan.getCode().equals(current.getCode())))
                .toList();
        output.apiUsed = used;
        output.apiRemaining = current.getApiMonthlyLimit() == null
                ? null
                : Math.max(0, current.getApiMonthlyLimit() - used);
        return output;
    }

    private SubscriptionPlanEntity currentPlan(String tenant) {
        var subscription = subscriptions.findByTenant(tenant).orElseGet(() -> {
            var created = new TenantSubscriptionEntity();
            created.setTenant(tenant);
            created.setPlanCode(DEFAULT_PLAN);
            created.setStartedAt(LocalDateTime.now());
            return subscriptions.save(created);
        });
        return plans.findByCodeAndActiveTrue(subscription.getPlanCode())
                .orElseThrow(() -> new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Subscription plan is not configured"));
    }

    private PlanOptionDTO toDTO(SubscriptionPlanEntity plan, boolean current) {
        var dto = new PlanOptionDTO();
        dto.id = plan.getId();
        dto.code = plan.getCode();
        dto.name = plan.getName();
        dto.description = plan.getDescription();
        dto.monthlyPrice = plan.getMonthlyPrice();
        dto.apiMonthlyLimit = plan.getApiMonthlyLimit();
        dto.customPlan = plan.isCustomPlan();
        dto.currentPlan = current;
        return dto;
    }

    private String requireTenant() {
        var tenant = TenantContext.getCurrentTenant();
        if (tenant == null || tenant.isBlank()) {
            throw new ServiceException(HttpStatus.FORBIDDEN, "A tenant is required");
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
}
