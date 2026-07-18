package com.yowpainter.shared.kernel.port;

import com.yowpainter.shared.kernel.adapter.dto.KernelInitiatePaymentRequestDto;
import com.yowpainter.shared.kernel.adapter.dto.KernelPaymentOrderResponseDto;

import java.util.UUID;

/**
 * Port sortant vers le module billing du kernel core (ordres de paiement).
 * <p>
 * A ne pas confondre avec {@link KernelPaymentPort}, qui cible le controleur
 * legacy /api/paiement (enregistrement comptable d'un paiement deja encaisse).
 * Ce port-ci pilote l'encaissement lui-meme via un PSP (MyCoolPay / Stripe).
 * </p>
 */
public interface KernelPaymentOrderPort {

    // Ces endpoints sont "server-to-server" : ils s'authentifient avec les
    // en-tetes X-Client-Id / X-Api-Key du backend (envoyes systematiquement par
    // KernelHttpClient). Aucun jeton porteur n'est requis. On ne declenche donc
    // PAS de login admin bootstrap ici : le bearer eventuellement present dans
    // le RequestContext (jeton de l'acheteur pendant un checkout) est transmis
    // en bonus, mais n'est jamais obligatoire.

    /** POST /api/payments/orders */
    KernelPaymentOrderResponseDto initiatePayment(KernelInitiatePaymentRequestDto command, UUID organizationId);

    /**
     * POST /api/payments/orders/{id}/refresh
     * <p>
     * Force le kernel a reinterroger le PSP et renvoie le statut a jour. C'est
     * la seule source de verite sur l'issue d'un paiement : ne jamais faire
     * confiance au corps d'un callback.
     * </p>
     */
    KernelPaymentOrderResponseDto refreshPayment(String orderId, UUID organizationId);

    /** GET /api/payments/orders/{id} */
    KernelPaymentOrderResponseDto getPayment(String orderId, UUID organizationId);
}
