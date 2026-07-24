package com.smartverse.smartreportbackend.services.payment;

import com.smartverse.smartreportbackend_gen.authorization.exception.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class SmartPaymentClient {
    private final RestClient client;

    public SmartPaymentClient(RestClient.Builder builder,
                              @Value("${PAYMENT_SERVICE_BASE_URL:https://app.smartverse.com.br/api/payment-service}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    @SuppressWarnings("unchecked")
    public ProviderResponse createLink(String authorization, int amountCents, UUID clientId) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("service", "REPORT");
        payload.put("value", amountCents);
        payload.put("client_id", clientId);
        try {
            var response = client.post()
                    .uri("/paymentLink")
                    .header("Authorization", authorization)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                throw invalidResponse();
            }
            var url = stringValue(response.get("url"));
            var orderNsu = stringValue(response.get("order_nsu"));
            if (orderNsu == null) orderNsu = stringValue(response.get("orderNsu"));
            var status = stringValue(response.get("status"));
            var reused = Boolean.TRUE.equals(response.get("reused"));
            if (url == null || orderNsu == null || status == null) {
                throw invalidResponse();
            }
            return new ProviderResponse(url, orderNsu, status, reused);
        } catch (RestClientException exception) {
            throw new ServiceException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível criar o link de pagamento");
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private ServiceException invalidResponse() {
        return new ServiceException(HttpStatus.BAD_GATEWAY,
                "O serviço de pagamento retornou uma resposta inválida");
    }

    public record ProviderResponse(String url, String orderNsu, String status, boolean reused) {
    }
}
