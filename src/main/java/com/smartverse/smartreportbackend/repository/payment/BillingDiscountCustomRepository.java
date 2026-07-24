package com.smartverse.smartreportbackend.repository.payment;

import com.smartverse.smartreportbackend_gen.entities.BillingDiscountEntity;
import com.smartverse.smartreportbackend_gen.enums.BillingCycle;
import com.smartverse.smartreportbackend_gen.repositories.BillingDiscountRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Primary
@Repository
public interface BillingDiscountCustomRepository extends BillingDiscountRepository {
    Optional<BillingDiscountEntity> findByBillingCycleAndActiveTrue(BillingCycle billingCycle);
}
