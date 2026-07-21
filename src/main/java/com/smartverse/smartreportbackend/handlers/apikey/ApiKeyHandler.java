package com.smartverse.smartreportbackend.handlers.apikey;

import com.smartverse.smartreportbackend.services.apikey.ApiKeyBusinessService;
import com.smartverse.smartreportbackend_gen.endpoints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
public class ApiKeyHandler implements CreateApiKey, GetApiKeys, RevokeApiKey {
    private final ApiKeyBusinessService service;

    public ApiKeyHandler(ApiKeyBusinessService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<CreateApiKeyOutput> createApiKey(CreateApiKeyInput input) {
        var response = new CreateApiKeyOutput();
        response.output = service.create(input.name, input.expiresAt);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<GetApiKeysOutput> getApiKeys() {
        var response = new GetApiKeysOutput();
        response.output = service.list();
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<RevokeApiKeyOutput> revokeApiKey(RevokeApiKeyInput input) {
        var response = new RevokeApiKeyOutput();
        response.result = service.revoke(input.id);
        return ResponseEntity.ok(response);
    }
}
