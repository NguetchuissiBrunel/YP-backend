package com.yowpainter.modules.payment.infrastructure.adapter.out.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class CampayClient {

    private final RestClient restClient;
    private final String appUsername;
    private final String appPassword;
    private final String tokenUrl;
    private final String collectUrl;
    private final String transactionUrl;
    private final String withdrawUrl;

    public CampayClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.payment.campay.base-url}") String baseUrl,
            @Value("${app.payment.campay.app-username}") String appUsername,
            @Value("${app.payment.campay.app-password}") String appPassword,
            @Value("${app.payment.campay.token-url}") String tokenUrl,
            @Value("${app.payment.campay.collect-url}") String collectUrl,
            @Value("${app.payment.campay.transaction-url}") String transactionUrl,
            @Value("${app.payment.campay.withdraw-url}") String withdrawUrl
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.appUsername = appUsername;
        this.appPassword = appPassword;
        this.tokenUrl = tokenUrl;
        this.collectUrl = collectUrl;
        this.transactionUrl = transactionUrl;
        this.withdrawUrl = withdrawUrl;
    }

    public String getToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", appUsername);
        form.add("password", appPassword);

        TokenResponse response = restClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);

        if (response == null || response.token == null || response.token.isBlank()) {
            throw new IllegalStateException("Impossible d'obtenir le token CamPay");
        }
        return response.token;
    }

    public CollectResponse collect(String token, CollectRequest request) {
        return restClient.post()
                .uri(collectUrl)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(CollectResponse.class);
    }

    public TransactionStatusResponse checkTransactionStatus(String token, String providerReference) {
        return restClient.get()
                .uri(transactionUrl + providerReference + "/")
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(TransactionStatusResponse.class);
    }

    public WithdrawalResponse withdraw(String token, WithdrawalRequest request) {
        return restClient.post()
                .uri(withdrawUrl)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(WithdrawalResponse.class);
    }

    @Data
    private static class TokenResponse {
        private String token;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CollectRequest {
        private String amount;
        private String from;
        private String description;

        @JsonProperty("external_reference")
        private String external_reference;

        private String currency;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CollectResponse {
        private String reference;

        public String getReference() {
            return reference;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionStatusResponse {
        private String status;
        private String amount;

        public String getStatus() {
            return status;
        }

        public String getAmount() {
            return amount;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WithdrawalRequest {
        private String amount;
        private String to;
        private String description;

        @JsonProperty("external_reference")
        private String external_reference;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WithdrawalResponse {
        private String reference;

        public String getReference() {
            return reference;
        }
    }
}
