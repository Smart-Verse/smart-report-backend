package com.smartverse.smartreportbackend.services.email;

import com.smartverse.smartreportbackend.common.FileCommon;
import com.smartverse.smartreportbackend_gen.authorization.exception.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class EmailService {

    private final RestClient resend;
    private final String from;

    public EmailService(RestClient.Builder builder,
                        @Value("${resend.api-key}") String apiKey,
                        @Value("${resend.from}") String from) {
        this.resend = builder
                .baseUrl("https://api.resend.com")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.from = from;
    }

    public String sendEmail(String to, String subject, String html, String idempotencyKey) {
        try {
            var response = resend.post()
                    .uri("/emails")
                    .header("Idempotency-Key", idempotencyKey)
                    .body(new ResendEmailRequest(from, to, subject, html))
                    .retrieve()
                    .body(ResendEmailResponse.class);
            if (response == null || response.id() == null || response.id().isBlank()) {
                throw new ServiceException(HttpStatus.BAD_GATEWAY, "Resend returned an invalid response");
            }
            return response.id();
        } catch (RestClientResponseException exception) {
            throw new ServiceException(HttpStatus.BAD_GATEWAY,
                    "Unable to send confirmation email through Resend");
        }
    }

    public String loadModel(String modelName) {
        return FileCommon.loadMod(modelName);
    }

    private record ResendEmailRequest(String from, String to, String subject, String html) {
    }

    private record ResendEmailResponse(String id) {
    }
}
