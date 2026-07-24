package com.smartverse.smartreportbackend.handlers.payment;

import com.smartverse.smartreportbackend.services.payment.PaymentBusinessService;
import com.smartverse.smartreportbackend_gen.endpoints.CreatePaymentLink;
import com.smartverse.smartreportbackend_gen.endpoints.CreatePaymentLinkInput;
import com.smartverse.smartreportbackend_gen.endpoints.CreatePaymentLinkOutput;
import com.smartverse.smartreportbackend_gen.endpoints.GetPaymentHistory;
import com.smartverse.smartreportbackend_gen.endpoints.GetPaymentHistoryOutput;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
public class PaymentHandler implements CreatePaymentLink, GetPaymentHistory {
    private final PaymentBusinessService payments;
    private final HttpServletRequest request;

    public PaymentHandler(PaymentBusinessService payments, HttpServletRequest request) {
        this.payments = payments;
        this.request = request;
    }

    @Override
    public ResponseEntity<CreatePaymentLinkOutput> createPaymentLink(CreatePaymentLinkInput input) {
        return ResponseEntity.ok(payments.createLink(
                input.planCode, input.billingCycle, request.getHeader("Authorization")));
    }

    @Override
    public ResponseEntity<GetPaymentHistoryOutput> getPaymentHistory() {
        return ResponseEntity.ok(payments.history());
    }
}
