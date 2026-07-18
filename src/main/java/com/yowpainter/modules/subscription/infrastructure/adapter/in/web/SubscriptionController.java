package com.yowpainter.modules.subscription.infrastructure.adapter.in.web;

import com.yowpainter.modules.subscription.application.service.SubscriptionService;
import com.yowpainter.modules.subscription.domain.model.Subscription;
import com.yowpainter.modules.subscription.domain.model.SubscriptionPlan;
import com.yowpainter.shared.security.AuthenticatedUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/subscription")
@RequiredArgsConstructor
@Tag(name = "Subscriptions", description = "Gestion des forfaits SaaS pour les artistes")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @GetMapping("/plans")
    @Operation(summary = "Lister les forfaits disponibles")
    public ResponseEntity<List<Map<String, Object>>> getPlans() {
        return ResponseEntity.ok(Arrays.stream(SubscriptionPlan.values())
                .map(p -> Map.of(
                        "name", p.name(),
                        "price", p.getPrice(),
                        "currency", p.getCurrency(),
                        "features", p == SubscriptionPlan.FREE ? List.of("5 artworks") : List.of("Unlimited artworks")
                )).collect(Collectors.toList()));
    }

    @GetMapping("/my-plan")
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Consulter mon forfait actuel")
    public ResponseEntity<Subscription> getMyPlan(Authentication authentication) {
        String email = authenticatedUserResolver.requireEmail(authentication);
        return ResponseEntity.ok(subscriptionService.getSubscriptionForArtist(email));
    }

    @PostMapping("/upgrade/checkout")
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Initier le paiement d'un forfait via le Kernel (Mobile Money / carte)")
    public ResponseEntity<Map<String, String>> checkoutUpgrade(
            Authentication authentication,
            @RequestParam SubscriptionPlan plan,
            @RequestParam String phoneNumber) {
        String email = authenticatedUserResolver.requireEmail(authentication);
        var result = subscriptionService.initiateSubscriptionUpgrade(email, plan, phoneNumber);

        Map<String, String> payload = new java.util.HashMap<>();
        payload.put("paymentReference", result.kernelOrderId());
        payload.put("paymentId", String.valueOf(result.paymentId()));
        payload.put("status", result.status());
        if (result.redirectUrl() != null) {
            payload.put("redirectUrl", result.redirectUrl());
        }
        return ResponseEntity.ok(payload);
    }

    @DeleteMapping("/cancel")
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Resilier son abonnement")
    public ResponseEntity<Void> cancelSubscription(Authentication authentication) {
        String email = authenticatedUserResolver.requireEmail(authentication);
        subscriptionService.cancelSubscription(email);
        return ResponseEntity.ok().build();
    }
}
