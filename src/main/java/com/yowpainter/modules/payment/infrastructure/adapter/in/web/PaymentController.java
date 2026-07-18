package com.yowpainter.modules.payment.infrastructure.adapter.in.web;

import com.yowpainter.modules.payment.application.service.PaymentService;
import com.yowpainter.modules.payment.infrastructure.adapter.in.web.dto.PaymentResponse;
import com.yowpainter.modules.shop.domain.model.PaymentStatus;
import com.yowpainter.shared.security.AuthenticatedUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Paiements — encaissement délégué au Kernel Core (module billing)")
public class PaymentController {

    private final PaymentService paymentService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    /**
     * Notification d'issue de paiement emise par le kernel.
     * <p>
     * Endpoint volontairement public : l'emetteur ne s'authentifie pas aupres de
     * nous. Le corps recu n'est donc <strong>jamais</strong> une preuve de
     * paiement — il ne sert qu'a designer la reference a re-verifier. Le statut
     * et le montant reels sont systematiquement reconfirmes aupres du kernel
     * (voir PaymentService#verifyPaymentWithKernel) avant le moindre effet de
     * bord. Un callback forge est donc sans effet.
     * </p>
     */
    @PostMapping("/callback")
    @Operation(summary = "Notification de paiement du Kernel (contenu vérifié auprès du Kernel avant traitement)")
    public ResponseEntity<String> handlePaymentCallback(@RequestBody Map<String, String> payload) {
        log.info("Received payment callback: {}", payload);

        String status = payload.get("status");
        String reference = payload.get("reference");
        String externalReference = payload.get("external_reference");

        if (externalReference == null || externalReference.isBlank()) {
            log.warn("Callback de paiement sans external_reference — ignoré.");
            return ResponseEntity.ok("Ignored");
        }

        try {
            if ("SUCCESSFUL".equals(status)) {
                paymentService.processSuccessfulPayment(reference, externalReference);
            } else {
                paymentService.processFailedPayment(reference, externalReference, status);
            }
        } catch (Exception ex) {
            // Ne jamais exposer le detail interne a un appelant non authentifie.
            log.error("Echec du traitement du callback de paiement pour {} : {}", externalReference, ex.getMessage());
            return ResponseEntity.ok("Received");
        }

        return ResponseEntity.ok("Received");
    }

    @GetMapping("/history")
    @Operation(summary = "Consulter son historique de paiements")
    public ResponseEntity<List<PaymentResponse>> getPaymentHistory(Authentication authentication) {
        String email = authenticatedUserResolver.requireEmail(authentication);
        return ResponseEntity.ok(paymentService.getPaymentHistory(email));
    }

    /**
     * Statut d'un paiement, rafraîchi auprès du Kernel.
     * <p>
     * Interrogé par le frontend (polling) après la redirection vers la page de
     * paiement du PSP : dès que le Kernel confirme l'encaissement, le backend
     * applique l'issue (crédit du portefeuille, confirmation de la réservation)
     * et renvoie {@code SUCCEEDED}, ce qui permet à l'UI de rediriger vers le
     * billet. Tant que le paiement est en cours, renvoie {@code PENDING}.
     * </p>
     *
     * @param referenceId identifiant métier (id de réservation ou de commande)
     */
    @GetMapping("/status/{referenceId}")
    @Operation(summary = "Statut d'un paiement (rafraîchi auprès du Kernel)")
    public ResponseEntity<Map<String, String>> getPaymentStatus(
            @PathVariable UUID referenceId,
            Authentication authentication) {
        UUID userId = authenticatedUserResolver.requireUserId(authentication);
        PaymentStatus status = paymentService.refreshAndProcess(referenceId, userId);
        return ResponseEntity.ok(Map.of(
                "referenceId", referenceId.toString(),
                "status", status.name()));
    }
}
