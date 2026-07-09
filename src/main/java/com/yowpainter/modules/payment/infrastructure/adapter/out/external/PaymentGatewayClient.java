package com.yowpainter.modules.payment.infrastructure.adapter.out.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Stub du gateway de paiement Mobile Money — intégration Kernel à venir.
 * <p>
 * L'ancienne intégration a été retirée. Les endpoints de paiement seront fournis par le
 * <strong>Kernel Core (kernel-core.yowyob.com)</strong> et intégrés ici dès
 * qu'ils seront disponibles.
 * </p>
 * <p>
 * Toutes les méthodes lèvent {@link UnsupportedOperationException} avec un
 * message explicite tant que l'intégration Kernel-Paiement n'est pas effective.
 * </p>
 */
@Component
public class PaymentGatewayClient {

    // -----------------------------------------------------------------------
    // API publique — sera implémentée via les endpoints Kernel
    // -----------------------------------------------------------------------

    /**
     * Obtenir un token d'accès auprès du Kernel pour les opérations de paiement.
     * À implémenter via POST /api/payment/token (Kernel).
     */
    public String getToken() {
        throw new UnsupportedOperationException(
                "Paiement non disponible : les endpoints de paiement Kernel ne sont pas encore intégrés. " +
                "Veuillez patienter ou contacter l'équipe technique.");
    }

    /**
     * Initier une collecte (débit) Mobile Money.
     * À implémenter via POST /api/payment/collect (Kernel).
     */
    public CollectResponse collect(String token, CollectRequest request) {
        throw new UnsupportedOperationException(
                "Le paiement Mobile Money n'est pas encore disponible. " +
                "Les endpoints de paiement Kernel seront bientôt intégrés.");
    }

    /**
     * Vérifier le statut d'une transaction.
     * À implémenter via GET /api/payment/transactions/{ref} (Kernel).
     */
    public TransactionStatusResponse checkTransactionStatus(String token, String providerReference) {
        throw new UnsupportedOperationException(
                "La vérification de statut de paiement n'est pas encore disponible (intégration Kernel en attente).");
    }

    /**
     * Initier un retrait (payout) Mobile Money vers l'artiste.
     * À implémenter via POST /api/payment/withdraw (Kernel).
     */
    public WithdrawalResponse withdraw(String token, WithdrawalRequest request) {
        throw new UnsupportedOperationException(
                "Le retrait Mobile Money n'est pas encore disponible (intégration Kernel en attente).");
    }

    // -----------------------------------------------------------------------
    // DTOs — conservés pour compatibilité avec le code existant
    // -----------------------------------------------------------------------

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
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionStatusResponse {
        private String status;
        private String amount;
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
    }
}
