package com.smartverse.smartreportbackend.messaging.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartverse.smartreportbackend.services.payment.PaymentBusinessService;
import com.smartverse.smartreportbackend_gen.dtos.PaymentConfirmedEventDTO;
import com.smartverse.smartreportbackend_gen.messaging.sub.PaymentConfirmedSub;
import org.springframework.stereotype.Component;

@Component
public class PaymentConfirmedListener extends PaymentConfirmedSub {
    private final ObjectMapper mapper;
    private final PaymentBusinessService payments;

    public PaymentConfirmedListener(ObjectMapper mapper, PaymentBusinessService payments) {
        this.mapper = mapper;
        this.payments = payments;
    }

    @Override
    protected void onMessage(String message) {
        try {
            payments.receive(mapper.readValue(message, PaymentConfirmedEventDTO.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSON de pagamento confirmado inválido", exception);
        }
    }
}
