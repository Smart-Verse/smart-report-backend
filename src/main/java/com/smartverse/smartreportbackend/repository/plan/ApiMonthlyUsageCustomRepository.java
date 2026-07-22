package com.smartverse.smartreportbackend.repository.plan;

import com.smartverse.smartreportbackend_gen.entities.ApiMonthlyUsageEntity;
import com.smartverse.smartreportbackend_gen.repositories.ApiMonthlyUsageRepository;
import jakarta.persistence.LockModeType;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Primary
@Repository
public interface ApiMonthlyUsageCustomRepository extends ApiMonthlyUsageRepository {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ApiMonthlyUsageEntity> findByTenantAndPeriod(String tenant, String period);

    List<ApiMonthlyUsageEntity> findAllByTenantOrderByPeriodDesc(String tenant);
}
