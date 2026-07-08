package com.yowpainter.modules.payment.infrastructure.adapter.in.web;

import com.yowpainter.modules.payment.application.service.PaymentService;
import com.yowpainter.modules.payment.infrastructure.adapter.in.web.dto.PaymentResponse;
import com.yowpainter.shared.security.AuthenticatedUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Gestion des paiements — intégration Kernel à venir")
public class PaymentController {

    private final PaymentService paymentService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @PostMapping("/callback")
    @Operation(summary = "Point d'entrée pour les callbacks de paiement (Kernel — à venir)")
    public ResponseEntity<String> handlePaymentCallback(@RequestBody Map<String, String> payload) {
        log.info("Received payment callback: {}", payload);

        String status = payload.get("status");
        String reference = payload.get("reference");
        String externalReference = payload.get("external_reference");

        if ("SUCCESSFUL".equals(status)) {
            paymentService.processSuccessfulPayment(reference, externalReference);
        } else {
            paymentService.processFailedPayment(reference, externalReference, status);
        }

        return ResponseEntity.ok("Received");
    }

    @GetMapping("/history")
    @Operation(summary = "Consulter son historique de paiements")
    public ResponseEntity<List<PaymentResponse>> getPaymentHistory(Authentication authentication) {
        String email = authenticatedUserResolver.requireEmail(authentication);
        return ResponseEntity.ok(paymentService.getPaymentHistory(email));
    }
}
