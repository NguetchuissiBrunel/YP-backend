package com.yowpainter.shared.kernel.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Reponse des endpoints /api/payments/orders du kernel core.
 * <p>
 * Miroir de {@code PaymentOrderResponse}. Attention : {@code id} est une chaine
 * cote kernel (et non un UUID formate), on la conserve telle quelle.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KernelPaymentOrderResponseDto(
    String id,
    String tenantId,
    String clientId,
    String serviceCode,
    BigDecimal amount,
    String currency,
    String provider,
    String method,
    String payerReference,
    String status,
    String providerReference,
    String redirectUrl,
    Instant createdAt,
    Instant updatedAt
) {
    /**
     * Le kernel n'expose pas d'enum de statut sur PaymentOrderResponse : on
     * tolere les variantes rencontrees plutot que de dependre d'une valeur exacte.
     */
    public boolean isSucceeded() {
        return status != null && switch (status.toUpperCase()) {
            case "SUCCESS", "SUCCEEDED", "COMPLETED", "PAID" -> true;
            default -> false;
        };
    }

    public boolean isFailed() {
        return status != null && switch (status.toUpperCase()) {
            case "FAILED", "CANCELLED", "CANCELED", "EXPIRED", "REJECTED" -> true;
            default -> false;
        };
    }
}
