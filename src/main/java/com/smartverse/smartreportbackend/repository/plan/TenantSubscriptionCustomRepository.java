package com.smartverse.smartreportbackend.repository.plan;

import com.smartverse.smartreportbackend_gen.entities.TenantSubscriptionEntity;
import com.smartverse.smartreportbackend_gen.repositories.TenantSubscriptionRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Primary
@Repository
public interface TenantSubscriptionCustomRepository extends TenantSubscriptionRepository {
    Optional<TenantSubscriptionEntity> findByTenant(String tenant);
}
