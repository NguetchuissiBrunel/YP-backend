package com.yowpainter.modules.shop.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // Peut correspondre à une shop_order ou à une event_reservation
    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;
    
    @Column(name = "reference_type", nullable = false)
    private String referenceType; // "ORDER" ou "RESERVATION"

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    @Builder.Default
    private String currency = "XAF";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "provider_reference")
    private String providerReference;

    /**
     * Identifiant de l'ordre de paiement cote kernel (PaymentOrderResponse.id).
     * C'est la cle qui permet de reinterroger le kernel pour verifier l'issue
     * reelle d'un paiement.
     */
    @Column(name = "kernel_order_id")
    private String kernelOrderId;

    /**
     * Cle d'idempotence transmise au kernel. Stable pour un meme referenceId :
     * un acheteur qui relance son checkout ne cree pas un second encaissement.
     */
    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    /** MYCOOLPAY | STRIPE */
    @Column(name = "provider")
    private String provider;

    /** MOBILE_MONEY | CARD */
    @Column(name = "method")
    private String method;

    /** URL de redirection PSP renvoyee par le kernel, le cas echeant. */
    @Column(name = "redirect_url", length = 1024)
    private String redirectUrl;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;


    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
