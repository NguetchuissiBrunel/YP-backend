package com.yowpainter.shared.kernel.adapter.dto;

import java.math.BigDecimal;

/**
 * Corps de POST /api/payments/orders (kernel core, module billing).
 * <p>
 * Miroir de {@code InitiatePaymentRequest} tel qu'expose par le swagger du
 * kernel deployé.
 * </p>
 *
 * @param clientId       identifiant du client applicatif appelant (X-Client-Id)
 * @param serviceCode    code du service metier a l'origine du paiement
 * @param idempotencyKey cle d'idempotence : rejouer la meme cle ne cree pas un
 *                       second ordre de paiement cote kernel
 * @param provider       {@code MYCOOLPAY} ou {@code STRIPE}
 * @param method         {@code MOBILE_MONEY} ou {@code CARD}
 * @param payerReference numero Mobile Money du payeur, ou reference carte
 * @param callbackUrl    URL notifiee par le kernel a l'issue du paiement
 */
public record KernelInitiatePaymentRequestDto(
    String clientId,
    String serviceCode,
    String idempotencyKey,
    BigDecimal amount,
    String currency,
    String provider,
    String method,
    String payerReference,
    String description,
    String callbackUrl
) {
    public static final String PROVIDER_MYCOOLPAY = "MYCOOLPAY";
    public static final String PROVIDER_STRIPE = "STRIPE";
    public static final String METHOD_MOBILE_MONEY = "MOBILE_MONEY";
    public static final String METHOD_CARD = "CARD";
}
