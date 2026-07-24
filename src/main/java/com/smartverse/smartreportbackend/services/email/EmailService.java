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
                        @Value("${RESEND_KEY}") String resendKey,
                        @Value("${RESEND_FROM:SmartReport <no-reply@smartverse.com.br>}") String from) {
        this.resend = builder
                .baseUrl("https://api.resend.com")
                .defaultHeader("Authorization", "Bearer " + resendKey)
                .build();
        this.from = from;
    }

    public void sendEmail(String to, String subject, String html, String idempotencyKey) {
        try {
            var response = resend.post()
                    .uri("/emails")
                    .header("Idempotency-Key", idempotencyKey)
                    .body(new ResendEmailRequest(from, to, subject, html))
                    .retrieve()
                    .body(ResendEmailResponse.class);
            if (response == null || response.id() == null || response.id().isBlank()) {
                throw new ServiceException(HttpStatus.BAD_GATEWAY,
                        "Resend retornou uma resposta inválida");
            }
        } catch (RestClientResponseException exception) {
            throw new ServiceException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível enviar o e-mail de confirmação");
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
