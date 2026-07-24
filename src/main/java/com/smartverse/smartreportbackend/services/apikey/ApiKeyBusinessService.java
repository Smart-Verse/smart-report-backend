package com.smartverse.smartreportbackend.services.apikey;

import com.smartverse.smartreportbackend_gen.authorization.exception.ServiceException;
import com.smartverse.smartreportbackend_gen.authorization.tenant.TenantContext;
import com.smartverse.smartreportbackend.config.migration.DBMigration;
import com.smartverse.smartreportbackend.repository.apikey.ApiKeyCustomRepository;
import com.smartverse.smartreportbackend_gen.dtos.ApiKeySummaryDTO;
import com.smartverse.smartreportbackend_gen.dtos.CreatedApiKeyDTO;
import com.smartverse.smartreportbackend_gen.entities.ApiKeyEntity;
import com.smartverse.smartreportbackend_gen.services.ApiKeyService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class ApiKeyBusinessService extends ApiKeyService {
    public static final String HEADER_NAME = "X-API-Key";
    private static final String ADMIN_TENANT = "admin";
    private static final String KEY_MARKER = "sr_live_";

    private final ApiKeyCustomRepository customRepository;
    private final DBMigration dbMigration;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyBusinessService(ApiKeyCustomRepository customRepository, DBMigration dbMigration) {
        this.customRepository = customRepository;
        this.dbMigration = dbMigration;
    }

    public CreatedApiKeyDTO create(String requestedName, LocalDateTime expiresAt) {
        var tenant = requireCurrentTenant();
        var name = requestedName == null ? "" : requestedName.trim();
        if (name.isEmpty() || name.length() > 120) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "API key name is required and must have at most 120 characters");
        }
        if (expiresAt != null && !expiresAt.isAfter(LocalDateTime.now())) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "Expiration must be in the future");
        }

        var prefix = randomToken(9);
        var plainTextKey = KEY_MARKER + prefix + "_" + randomToken(32);
        var entity = new ApiKeyEntity();
        entity.setTenant(tenant);
        entity.setName(name);
        entity.setKeyPrefix(prefix);
        entity.setSecretHash(hash(plainTextKey));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setExpiresAt(expiresAt);
        entity.setActive(true);

        var saved = inAdminTenant(() -> customRepository.save(entity));
        var output = new CreatedApiKeyDTO();
        output.id = saved.getId();
        output.name = saved.getName();
        output.apiKey = plainTextKey;
        output.keyPrefix = saved.getKeyPrefix();
        output.createdAt = saved.getCreatedAt();
        output.expiresAt = saved.getExpiresAt();
        return output;
    }

    public List<ApiKeySummaryDTO> list() {
        var tenant = requireCurrentTenant();
        return inAdminTenant(() -> customRepository.findAllByTenantOrderByCreatedAtDesc(tenant).stream()
                .map(this::toSummary)
                .toList());
    }

    public boolean revoke(UUID id) {
        var tenant = requireCurrentTenant();
        return inAdminTenant(() -> {
            var entity = customRepository.findByIdAndTenant(id, tenant)
                    .orElseThrow(() -> new ServiceException(HttpStatus.NOT_FOUND, "API key not found"));
            entity.setActive(false);
            customRepository.save(entity);
            return true;
        });
    }

    public AuthenticatedKey authenticate(String plainTextKey) {
        if (plainTextKey == null || !plainTextKey.startsWith(KEY_MARKER)) throw unauthorized();
        var firstSeparator = plainTextKey.indexOf('_', KEY_MARKER.length());
        if (firstSeparator < 0) throw unauthorized();
        var prefix = plainTextKey.substring(KEY_MARKER.length(), firstSeparator);
        var legacySeparator = plainTextKey.indexOf('_', firstSeparator + 1);
        var legacyPrefix = legacySeparator < 0 ? null : plainTextKey.substring(KEY_MARKER.length(), legacySeparator);

        return inAdminTenant(() -> {
            // Chaves atuais usam sr_live_<prefix>_<segredo>. Algumas chaves
            // emitidas anteriormente usam sr_live_<prefix>_A_<segredo>; o
            // segundo formato continua válido após a atualização.
            var entity = customRepository.findByKeyPrefixAndActiveTrue(prefix)
                    .or(() -> legacyPrefix == null
                            ? java.util.Optional.empty()
                            : customRepository.findByKeyPrefixAndActiveTrue(legacyPrefix))
                    .orElseThrow(this::unauthorized);
            if (!MessageDigest.isEqual(entity.getSecretHash().getBytes(StandardCharsets.US_ASCII),
                    hash(plainTextKey).getBytes(StandardCharsets.US_ASCII))) throw unauthorized();
            if (entity.getExpiresAt() != null && !entity.getExpiresAt().isAfter(LocalDateTime.now())) {
                entity.setActive(false);
                customRepository.save(entity);
                throw unauthorized();
            }
            entity.setLastUsedAt(LocalDateTime.now());
            customRepository.save(entity);
            return new AuthenticatedKey(entity.getTenant(), entity.getId());
        });
    }

    private ApiKeySummaryDTO toSummary(ApiKeyEntity entity) {
        var output = new ApiKeySummaryDTO();
        output.id = entity.getId();
        output.name = entity.getName();
        output.keyPrefix = entity.getKeyPrefix();
        output.active = entity.isActive();
        output.createdAt = entity.getCreatedAt();
        output.lastUsedAt = entity.getLastUsedAt();
        output.expiresAt = entity.getExpiresAt();
        return output;
    }

    private <T> T inAdminTenant(Supplier<T> operation) {
        var previousTenant = TenantContext.getCurrentTenant();
        try {
            TenantContext.setCurrentTenant(ADMIN_TENANT);
            dbMigration.loadMigrateTenants(ADMIN_TENANT);
            return operation.get();
        } finally {
            TenantContext.setCurrentTenant(previousTenant);
        }
    }

    private String requireCurrentTenant() {
        var tenant = TenantContext.getCurrentTenant();
        if (tenant == null || tenant.isBlank() || ADMIN_TENANT.equalsIgnoreCase(tenant)) {
            throw new ServiceException(HttpStatus.FORBIDDEN, "A customer tenant is required");
        }
        return tenant;
    }

    private String randomToken(int byteCount) {
        var bytes = new byte[byteCount];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private ServiceException unauthorized() {
        return new ServiceException(HttpStatus.UNAUTHORIZED, "Invalid or expired API key");
    }

    public record AuthenticatedKey(String tenant, UUID id) {}
}
