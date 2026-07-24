package com.smartverse.smartreportbackend.services.userconfiguration;

import com.smartverse.smartreportbackend_gen.authorization.tenant.TenantContext;
import com.smartverse.smartreportbackend.config.database.TenantSchemaInterceptor;
import com.smartverse.smartreportbackend.config.security.repository.AuthenticationRepository;
import com.smartverse.smartreportbackend_gen.converters.UserConfigurationDTOConverter;
import com.smartverse.smartreportbackend_gen.dtos.UserConfigurationDTO;
import com.smartverse.smartreportbackend_gen.entities.UserConfigurationEntity;
import com.smartverse.smartreportbackend_gen.enums.Language;
import com.smartverse.smartreportbackend_gen.enums.Theme;
import com.smartverse.smartreportbackend_gen.repositories.UserConfigurationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserConfigurationService extends com.smartverse.smartreportbackend_gen.services.UserConfigurationService {


    @Autowired
    private TenantSchemaInterceptor tenantSchemaInterceptor;

    @Autowired
    private AuthenticationRepository authenticationRepository;

    @Autowired
    private UserConfigurationRepository userConfigurationRepository;

    @Autowired
    private UserConfigurationDTOConverter userConfigurationDTOConverter;

    @Override
    @Transactional
    public UserConfigurationDTO update(UserConfigurationDTO obj, UUID id) {
        var updated = super.update(obj, id);
        updateMaster(dtoConverter.toEntity(updated, null));
        return updated;
    }


    public UserConfigurationDTO saveUserConfiguration(UUID hash) {
        var oldTEnant = TenantContext.getCurrentTenant();
        TenantContext.setCurrentTenant("admin");
        tenantSchemaInterceptor.switchSchema();

        var user = authenticationRepository.findById(hash);

        var userConfiguration = new UserConfigurationEntity();

        user.ifPresent(item -> {
            userConfiguration.setHash(hash);
            userConfiguration.setName(item.getName());
            userConfiguration.setEmail(item.getEmail());
            userConfiguration.setLang(Language.PORTUGUESE);
            userConfiguration.setTheme(Theme.LIGHT);
        });

        TenantContext.setCurrentTenant(oldTEnant);
        tenantSchemaInterceptor.switchSchema();
        userConfigurationRepository.save(userConfiguration);

        return userConfigurationDTOConverter.toDTO(userConfiguration, null);
    }
    @Transactional
    public void updateMaster(UserConfigurationEntity entity) {
        var oldTEnant = TenantContext.getCurrentTenant();
        TenantContext.setCurrentTenant("admin");
        tenantSchemaInterceptor.switchSchema();

        var user = authenticationRepository.findById(entity.getHash());

        user.ifPresent(item -> {
            item.setName(entity.getName());
            item.setEmail(entity.getEmail());

            authenticationRepository.save(item);
        });

    }
}
