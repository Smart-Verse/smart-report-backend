package com.smartverse.smartreportbackend.repository.plan;

import com.smartverse.smartreportbackend_gen.entities.SubscriptionPlanEntity;
import com.smartverse.smartreportbackend_gen.repositories.SubscriptionPlanRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Primary
@Repository
public interface SubscriptionPlanCustomRepository extends SubscriptionPlanRepository {
    Optional<SubscriptionPlanEntity> findByCodeAndActiveTrue(String code);
    List<SubscriptionPlanEntity> findAllByActiveTrueOrderByDisplayOrderAsc();
}
