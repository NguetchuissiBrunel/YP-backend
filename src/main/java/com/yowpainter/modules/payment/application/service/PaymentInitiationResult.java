package com.yowpainter.modules.payment.application.service;

import java.util.UUID;

/**
 * Resultat d'une initiation de paiement.
 *
 * @param paymentId     identifiant de la ligne de paiement locale
 * @param kernelOrderId identifiant de l'ordre de paiement cote kernel
 * @param status        statut renvoye par le kernel (generalement PENDING)
 * @param redirectUrl   URL de redirection PSP a suivre par le client, si le
 *                      moyen de paiement en exige une (typiquement CARD/Stripe).
 *                      Nul en Mobile Money, ou la validation se fait sur le
 *                      telephone du payeur.
 */
public record PaymentInitiationResult(
        UUID paymentId,
        String kernelOrderId,
        String status,
        String redirectUrl
) {
}
