package com.smartverse.smartreportbackend.repository.payment;

import com.smartverse.smartreportbackend_gen.entities.PaymentEventInboxEntity;
import com.smartverse.smartreportbackend_gen.repositories.PaymentEventInboxRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Primary
@Repository
public interface PaymentEventInboxCustomRepository extends PaymentEventInboxRepository {
    Optional<PaymentEventInboxEntity> findByPaymentId(UUID paymentId);
    List<PaymentEventInboxEntity> findAllByStatus(String status);
}
