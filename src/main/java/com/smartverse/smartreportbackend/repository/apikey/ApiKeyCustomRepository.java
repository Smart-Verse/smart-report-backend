package com.smartverse.smartreportbackend.repository.apikey;

import com.smartverse.smartreportbackend_gen.entities.ApiKeyEntity;
import com.smartverse.smartreportbackend_gen.repositories.ApiKeyRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Primary
@Repository
public interface ApiKeyCustomRepository extends ApiKeyRepository {
    List<ApiKeyEntity> findAllByTenantOrderByCreatedAtDesc(String tenant);
    Optional<ApiKeyEntity> findByIdAndTenant(UUID id, String tenant);
    Optional<ApiKeyEntity> findByKeyPrefixAndActiveTrue(String keyPrefix);
}
