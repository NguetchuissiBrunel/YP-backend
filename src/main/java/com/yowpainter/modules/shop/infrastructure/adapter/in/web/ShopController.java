package com.yowpainter.modules.shop.infrastructure.adapter.in.web;

import com.yowpainter.modules.payment.application.service.PaymentService;
import com.yowpainter.modules.shop.application.service.ShopService;
import com.yowpainter.modules.shop.domain.model.OrderStatus;
import com.yowpainter.modules.shop.infrastructure.adapter.in.web.dto.OrderCreateRequest;
import com.yowpainter.modules.shop.infrastructure.adapter.in.web.dto.OrderResponse;
import com.yowpainter.modules.shop.infrastructure.adapter.in.web.dto.ProductCreateRequest;
import com.yowpainter.modules.shop.infrastructure.adapter.in.web.dto.ProductResponse;
import com.yowpainter.shared.security.AuthenticatedUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/shop")
@RequiredArgsConstructor
@Tag(name = "Shop & Orders", description = "Gestion de la boutique, commandes et inventaire")
public class ShopController {

    private final ShopService shopService;
    private final PaymentService paymentService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @PostMapping("/products")
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Mettre un produit / oeuvre en vente (Artiste)")
    public ResponseEntity<ProductResponse> createProduct(
            Authentication authentication,
            @Valid @RequestBody ProductCreateRequest request) {
        String email = authenticatedUserResolver.requireEmail(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(shopService.createProduct(email, request));
    }

    @GetMapping("/v1/public/{artistSlug}/products")
    @SecurityRequirements
    @Operation(summary = "Lister le catalogue de ventes d'une boutique (tenant spécifique)")
    public ResponseEntity<List<ProductResponse>> getProductsByArtist(@PathVariable String artistSlug) {
        return ResponseEntity.ok(shopService.getProductsByArtistSlug(artistSlug));
    }

    @GetMapping("/v1/public/products")
    @SecurityRequirements
    @Operation(summary = "Lister tous les produits en vente sur la plateforme (tous artistes confondus)")
    public ResponseEntity<List<ProductResponse>> getGlobalProducts() {
        return ResponseEntity.ok(shopService.getAllPublicProducts());
    }

    @PostMapping("/v1/public/{artistSlug}/orders")
    @PreAuthorize("hasRole('BUYER')")
    @Operation(summary = "Passer une commande dans une boutique spécifique")
    public ResponseEntity<OrderResponse> placeOrder(
            @PathVariable String artistSlug,
            Authentication authentication,
            @Valid @RequestBody OrderCreateRequest request) {
        String email = authenticatedUserResolver.requireEmail(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(shopService.placeOrder(email, artistSlug, request));
    }

    @PostMapping("/orders/{id}/checkout")
    @PreAuthorize("hasRole('BUYER')")
    @Operation(summary = "Initier le paiement Mobile Money (MOMO/Orange) pour une commande")
    public ResponseEntity<Map<String, String>> checkoutOrder(
            @PathVariable UUID id,
            @RequestParam String phoneNumber,
            Authentication authentication) {
        String email = authenticatedUserResolver.requireEmail(authentication);

        OrderResponse order = shopService.getOrderById(id);

        String paymentReference = paymentService.initiateMobileMoneyPayment(
                id,
                "ORDER",
                order.getTotalAmount(),
                "public",
                email,
                phoneNumber
        );

        return ResponseEntity.ok(Map.of("paymentReference", paymentReference));
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "Details d'une commande")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable UUID id) {
        return ResponseEntity.ok(shopService.getOrderById(id));
    }

    @GetMapping("/orders/my-sales")
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Lister les commandes RECUES (Artiste)")
    public ResponseEntity<List<OrderResponse>> getMySales(Authentication authentication) {
        String email = authenticatedUserResolver.requireEmail(authentication);
        return ResponseEntity.ok(shopService.getMySales(email));
    }

    @GetMapping("/orders/my-purchases")
    @PreAuthorize("hasRole('BUYER')")
    @Operation(summary = "Lister mes commandes PASSEES (Acheteur)")
    public ResponseEntity<List<OrderResponse>> getMyPurchases(Authentication authentication) {
        String email = authenticatedUserResolver.requireEmail(authentication);
        return ResponseEntity.ok(shopService.getMyPurchases(email));
    }

    @PatchMapping("/orders/{id}/status")
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Mettre a jour le statut d'une commande (SHIPPED, DELIVERED...)")
    public ResponseEntity<Void> updateOrderStatus(@PathVariable UUID id, @RequestParam OrderStatus status) {
        shopService.updateOrderStatus(id, status);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/inventory")
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Consulter son inventaire de produits (Artiste)")
    public ResponseEntity<List<ProductResponse>> getInventory(Authentication authentication) {
        String email = authenticatedUserResolver.requireEmail(authentication);
        return ResponseEntity.ok(shopService.getInventory(email));
    }
}
