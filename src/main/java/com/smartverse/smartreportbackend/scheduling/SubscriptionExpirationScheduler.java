package com.smartverse.smartreportbackend.scheduling;

import com.smartverse.smartreportbackend.services.plan.PlanBusinessService;
import com.smartverse.smartreportbackend.services.payment.PaymentBusinessService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionExpirationScheduler {
    private final PlanBusinessService plans;
    private final PaymentBusinessService payments;

    public SubscriptionExpirationScheduler(PlanBusinessService plans, PaymentBusinessService payments) {
        this.plans = plans;
        this.payments = payments;
    }

    @Scheduled(fixedDelayString = "${SUBSCRIPTION_EXPIRATION_INTERVAL_MS:60000}")
    public void expireSubscriptions() {
        plans.expireSubscriptions();
        payments.retryFailedEvents();
    }
}
