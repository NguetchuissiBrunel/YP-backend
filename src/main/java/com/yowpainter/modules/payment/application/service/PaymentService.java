package com.yowpainter.modules.payment.application.service;

import com.yowpainter.modules.auth.domain.model.AppUser;
import com.yowpainter.modules.auth.domain.port.out.AppUserRepositoryPort;
import com.yowpainter.modules.event.application.service.EventService;
import com.yowpainter.modules.shop.domain.model.OrderStatus;
import com.yowpainter.modules.shop.domain.model.Payment;
import com.yowpainter.modules.shop.domain.model.PaymentStatus;
import com.yowpainter.modules.shop.domain.port.out.PaymentRepositoryPort;
import com.yowpainter.modules.shop.application.service.ShopService;
import com.yowpainter.modules.payment.infrastructure.adapter.in.web.dto.PaymentResponse;
import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.subscription.application.service.SubscriptionService;
import com.yowpainter.shared.context.OrganizationContext;
import com.yowpainter.shared.kernel.adapter.dto.KernelInitiatePaymentRequestDto;
import com.yowpainter.shared.kernel.adapter.dto.KernelPaymentOrderResponseDto;
import com.yowpainter.shared.kernel.port.KernelPaymentOrderPort;
import com.yowpainter.shared.tenant.TenantTransactionExecutor;
import com.yowpainter.config.KernelProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PaymentService {

    private final PaymentRepositoryPort paymentRepository;
    private final AppUserRepositoryPort userRepository;
    private final ShopService shopService;
    private final EventService eventService;
    private final WalletService walletService;
    private final KernelPaymentOrderPort kernelPaymentOrderPort;
    private final TenantTransactionExecutor tenantTransactionExecutor;
    private final KernelProperties kernelProperties;
    private final com.yowpainter.modules.notification.application.service.NotificationService notificationService;
    private final com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort artistRepository;
    private final com.yowpainter.modules.auth.application.service.EmailService emailService;
    private final com.yowpainter.modules.subscription.application.service.SubscriptionService subscriptionService;
    private final com.yowpainter.modules.shop.application.service.InvoiceService invoiceService;
    private final com.yowpainter.modules.shop.domain.port.out.OrderRepositoryPort orderRepository;

    public PaymentService(
            PaymentRepositoryPort paymentRepository,
            AppUserRepositoryPort userRepository,
            ShopService shopService,
            EventService eventService,
            WalletService walletService,
            KernelPaymentOrderPort kernelPaymentOrderPort,
            TenantTransactionExecutor tenantTransactionExecutor,
            KernelProperties kernelProperties,
            com.yowpainter.modules.notification.application.service.NotificationService notificationService,
            com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort artistRepository,
            com.yowpainter.modules.auth.application.service.EmailService emailService,
            @org.springframework.context.annotation.Lazy com.yowpainter.modules.subscription.application.service.SubscriptionService subscriptionService,
            com.yowpainter.modules.shop.application.service.InvoiceService invoiceService,
            com.yowpainter.modules.shop.domain.port.out.OrderRepositoryPort orderRepository) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.shopService = shopService;
        this.eventService = eventService;
        this.walletService = walletService;
        this.kernelPaymentOrderPort = kernelPaymentOrderPort;
        this.tenantTransactionExecutor = tenantTransactionExecutor;
        this.kernelProperties = kernelProperties;
        this.notificationService = notificationService;
        this.artistRepository = artistRepository;
        this.emailService = emailService;
        this.subscriptionService = subscriptionService;
        this.invoiceService = invoiceService;
        this.orderRepository = orderRepository;
    }

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.backend-url:http://localhost:8090}")
    private String backendUrl;

    @Value("${app.payment.provider:MYCOOLPAY}")
    private String paymentProvider;

    @Value("${app.payment.method:MOBILE_MONEY}")
    private String paymentMethod;

    /**
     * Historique de paiements d'un acheteur, tous tenants confondus.
     * <p>
     * La table payment vit dans les schemas tenant : un acheteur ayant achete
     * chez plusieurs artistes a des lignes dans plusieurs schemas. On balaye
     * donc les tenants actifs, comme ShopService#getMyPurchases.
     * </p>
     */
    public List<PaymentResponse> getPaymentHistory(String email) {
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouve"));

        List<PaymentResponse> history = new java.util.ArrayList<>();
        for (Artist artist : artistRepository.findByStatus("ACTIVE")) {
            if (artist.getOrganizationId() == null) {
                continue;
            }
            try {
                OrganizationContext.setOrganizationId(artist.getOrganizationId());
                history.addAll(tenantTransactionExecutor.execute(
                        () -> paymentRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                                .map(this::mapToPaymentResponse)
                                .collect(Collectors.toList())));
            } catch (Exception ex) {
                log.error("Echec de lecture des paiements pour le tenant {}", artist.getOrganizationId(), ex);
            } finally {
                OrganizationContext.clear();
            }
        }
        history.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return history;
    }

    /**
     * Initie un encaissement via le module billing du kernel core
     * (POST /api/payments/orders).
     * <p>
     * Volontairement <strong>non</strong> {@code @Transactional} : le schema
     * tenant est resolu a l'ouverture de la transaction a partir de
     * {@link OrganizationContext}. Il faut donc positionner le contexte
     * <em>avant</em>, puis passer par {@link TenantTransactionExecutor}. L'appel
     * HTTP au kernel est fait hors transaction pour ne pas tenir une connexion
     * ouverte pendant un aller-retour reseau.
     * </p>
     *
     * @param artistSlug slug de l'artiste proprietaire de la boutique/evenement
     *                   (c'est lui qui porte l'organisation kernel et le schema)
     */
    public PaymentInitiationResult initiatePayment(
            UUID referenceId, String referenceType, BigDecimal amount,
            String artistSlug, String userEmail, String phoneNumber) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Montant de paiement invalide: " + amount);
        }

        Artist artist = artistRepository.findBySlug(artistSlug)
                .orElseThrow(() -> new IllegalArgumentException("Artiste non trouve pour le tenant: " + artistSlug));
        AppUser user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouve"));

        UUID organizationId = artist.getOrganizationId();
        if (organizationId == null) {
            throw new IllegalStateException("L'artiste " + artistSlug + " n'a pas d'organisation kernel provisionnee");
        }

        String idempotencyKey = buildIdempotencyKey(referenceType, referenceId);
        String currency = resolveCurrency();

        OrganizationContext.setOrganizationId(organizationId);
        try {
            // 1. Reserver/reprendre la ligne locale AVANT d'appeler le kernel :
            //    en cas d'echec reseau on garde une trace du paiement tente.
            Payment payment = tenantTransactionExecutor.execute(() -> {
                Payment existing = paymentRepository.findByReferenceId(referenceId).orElse(null);
                if (existing != null) {
                    if (existing.getStatus() == PaymentStatus.SUCCEEDED) {
                        throw new IllegalStateException("Ce paiement a deja ete regle");
                    }
                    existing.setAmount(amount);
                    existing.setPhoneNumber(phoneNumber);
                    existing.setStatus(PaymentStatus.PENDING);
                    return paymentRepository.save(existing);
                }
                return paymentRepository.save(Payment.builder()
                        .userId(user.getId())
                        .referenceId(referenceId)
                        .referenceType(referenceType)
                        .amount(amount)
                        .currency(currency)
                        .status(PaymentStatus.PENDING)
                        .tenantId(artistSlug)
                        .phoneNumber(phoneNumber)
                        .idempotencyKey(idempotencyKey)
                        .provider(paymentProvider)
                        .method(paymentMethod)
                        .build());
            });

            // 2. Appel kernel hors transaction. La cle d'idempotence rend le
            //    rejeu sur (referenceType, referenceId) sans effet de bord.
            KernelPaymentOrderResponseDto order = kernelPaymentOrderPort.initiatePayment(
                    new KernelInitiatePaymentRequestDto(
                            kernelProperties.clientId(),
                            "YOWPAINTER_" + referenceType,
                            idempotencyKey,
                            amount,
                            currency,
                            paymentProvider,
                            paymentMethod,
                            phoneNumber,
                            buildDescription(referenceType, referenceId),
                            // Retour navigateur APRES paiement : une page frontend
                            // (et non l'endpoint JSON du backend), qui verifie le
                            // statut puis renvoie vers le billet/la commande.
                            frontendUrl + "/payment/return?ref=" + referenceId + "&type=" + referenceType
                    ),
                    organizationId
            );

            // 3. Rattacher l'ordre kernel a la ligne locale.
            UUID paymentId = payment.getId();
            tenantTransactionExecutor.execute(() -> {
                Payment toUpdate = paymentRepository.findByReferenceId(referenceId).orElseThrow();
                toUpdate.setKernelOrderId(order.id());
                toUpdate.setProviderReference(order.providerReference());
                toUpdate.setRedirectUrl(order.redirectUrl());
                if (order.isFailed()) {
                    toUpdate.setStatus(PaymentStatus.FAILED);
                }
                paymentRepository.save(toUpdate);
            });

            log.info("Ordre de paiement kernel {} cree pour {} {} (montant {})",
                    order.id(), referenceType, referenceId, amount);

            return new PaymentInitiationResult(paymentId, order.id(), order.status(), order.redirectUrl());
        } finally {
            OrganizationContext.clear();
        }
    }

    /** La devise du kernel fait foi ; XAF par defaut si non configuree. */
    private String resolveCurrency() {
        String configured = kernelProperties.defaultCurrency();
        return (configured == null || configured.isBlank()) ? "XAF" : configured;
    }

    private String buildIdempotencyKey(String referenceType, UUID referenceId) {
        return "yowpainter-" + referenceType.toLowerCase() + "-" + referenceId;
    }

    private String buildDescription(String referenceType, UUID referenceId) {
        String shortRef = referenceId.toString().substring(0, 8);
        return switch (referenceType) {
            case "ORDER" -> "Commande YowPainter #" + shortRef;
            case "RESERVATION" -> "Reservation YowPainter #" + shortRef;
            case "SUBSCRIPTION" -> "Abonnement YowPainter #" + shortRef;
            default -> "Paiement YowPainter #" + shortRef;
        };
    }

    /**
     * Reinterroge le kernel pour un paiement donne et applique son issue.
     * <p>
     * C'est le point d'entree du <strong>polling frontend</strong> : apres avoir
     * ete redirige vers la page hebergee du PSP (MyCoolPay), le client interroge
     * cette methode jusqu'a obtenir {@code SUCCEEDED}/{@code FAILED}. On ne
     * conclut jamais depuis un callback : la seule source de verite est le kernel
     * ({@code POST /api/payments/orders/{id}/refresh}). Idempotent.
     * </p>
     *
     * @param expectedUserId si non nul, verifie que le paiement appartient bien a
     *                       cet utilisateur (l'acheteur qui consulte son statut)
     * @return le statut resultant ({@code PENDING} tant que le kernel n'a pas
     *         tranche, ou s'il est injoignable)
     */
    public PaymentStatus refreshAndProcess(UUID referenceId, UUID expectedUserId) {
        Artist artist = locateArtistForPayment(referenceId)
                .orElseThrow(() -> new IllegalArgumentException("Paiement non trouve pour la reference: " + referenceId));
        UUID organizationId = artist.getOrganizationId();

        Payment payment;
        OrganizationContext.setOrganizationId(organizationId);
        try {
            payment = tenantTransactionExecutor.execute(() -> paymentRepository.findByReferenceId(referenceId)
                    .orElseThrow(() -> new IllegalArgumentException("Paiement non trouve pour la reference: " + referenceId)));
        } finally {
            OrganizationContext.clear();
        }

        if (expectedUserId != null && !expectedUserId.equals(payment.getUserId())) {
            throw new SecurityException("Ce paiement n'appartient pas a l'utilisateur courant");
        }
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return PaymentStatus.SUCCEEDED;
        }
        if (payment.getStatus() == PaymentStatus.FAILED) {
            return PaymentStatus.FAILED;
        }
        if (payment.getKernelOrderId() == null) {
            return PaymentStatus.PENDING;
        }

        KernelPaymentOrderResponseDto order;
        try {
            order = kernelPaymentOrderPort.refreshPayment(payment.getKernelOrderId(), organizationId);
        } catch (Exception ex) {
            // Kernel injoignable : on ne tranche pas, le client re-essaiera.
            log.warn("Refresh kernel impossible pour {} : {}", referenceId, ex.getMessage());
            return PaymentStatus.PENDING;
        }

        if (order.isSucceeded()) {
            processSuccessfulPayment(order.providerReference(), referenceId.toString());
            return PaymentStatus.SUCCEEDED;
        }
        if (order.isFailed()) {
            processFailedPayment(order.providerReference(), referenceId.toString(), order.status());
            return PaymentStatus.FAILED;
        }
        return PaymentStatus.PENDING;
    }

    /**
     * Traite l'issue favorable d'un paiement.
     * <p>
     * <strong>Le corps du callback n'est jamais cru sur parole</strong> : il ne
     * sert qu'a designer le paiement a re-verifier. Le statut et le montant sont
     * systematiquement reconfirmes aupres du kernel
     * (POST /api/payments/orders/{id}/refresh) avant tout effet de bord.
     * </p>
     */
    public void processSuccessfulPayment(String providerReference, String externalReference) {
        UUID referenceId = UUID.fromString(externalReference);

        log.info("Processing successful payment for reference: {}", referenceId);

        // La table payment n'existe que dans les schemas tenant : il faut resoudre
        // l'artiste (donc l'organisation, donc le schema) avant tout acces.
        Artist artist = locateArtistForPayment(referenceId)
                .orElseThrow(() -> new IllegalArgumentException("Paiement non trouve pour la reference: " + referenceId));
        UUID organizationId = artist.getOrganizationId();

        OrganizationContext.setOrganizationId(organizationId);

        try {
            Payment payment = tenantTransactionExecutor.execute(() -> paymentRepository.findByReferenceId(referenceId)
                    .orElseThrow(() -> new IllegalArgumentException("Paiement non trouve pour la reference: " + referenceId)));

            if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
                log.warn("Payment already processed for reference: {}", referenceId);
                return;
            }

            // VERIFICATION DE SECURITE : le kernel est la seule source de verite.
            if (!verifyPaymentWithKernel(payment, organizationId)) {
                log.error("Payment verification failed for reference: {}. Possible fraud attempt.", referenceId);
                markPaymentFailed(referenceId, providerReference, "VERIFICATION_FAILED");
                return;
            }

            tenantTransactionExecutor.execute(() -> {
                Payment toUpdate = paymentRepository.findByReferenceId(referenceId).orElseThrow();
                toUpdate.setStatus(PaymentStatus.SUCCEEDED);
                toUpdate.setProviderReference(providerReference);
                paymentRepository.save(toUpdate);
            });

            AppUser user = userRepository.findById(payment.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouve"));

            String referenceType = payment.getReferenceType();
            
            if ("ORDER".equals(referenceType) || "RESERVATION".equals(referenceType)) {
                if ("ORDER".equals(referenceType)) {
                    shopService.updateOrderStatus(referenceId, OrderStatus.PAID);
                    
                    // Create local invoice and update payment reference
                    try {
                        tenantTransactionExecutor.execute(() -> {
                            var orderOpt = orderRepository.findById(referenceId);
                            if (orderOpt.isEmpty()) {
                                return;
                            }
                            var invoice = invoiceService.createInvoice(orderOpt.get());
                            invoiceService.markAsPaid(invoice);
                            Payment toUpdate = paymentRepository.findByReferenceId(referenceId).orElseThrow();
                            toUpdate.setInvoiceId(invoice.getId());
                            paymentRepository.save(toUpdate);
                            log.info("Successfully created and paid invoice {} for order {}", invoice.getInvoiceNumber(), referenceId);
                        });
                    } catch (Exception ex) {
                        log.error("Failed to generate and mark paid invoice for order {}: {}", referenceId, ex.getMessage(), ex);
                    }

                    notificationService.createNotification(payment.getUserId(), "Votre commande #" + referenceId.toString().substring(0, 8) + " a été payée avec succès !");
                    emailService.sendPaymentConfirmation(user.getEmail(), referenceId.toString().substring(0, 8), payment.getAmount());
                } else {
                    eventService.confirmPaidReservation(referenceId);
                    notificationService.createNotification(payment.getUserId(), "Votre réservation a été confirmée !");
                    emailService.sendPaymentConfirmation(user.getEmail(), "Réservation #" + referenceId.toString().substring(0, 8), payment.getAmount());
                }
                
                // Calcul de la commission dynamique basée sur l'abonnement
                var sub = subscriptionService.getSubscriptionForArtist(artist.getEmail());
                java.math.BigDecimal rate = sub.getPlan().getCommissionRate();
                java.math.BigDecimal commission = payment.getAmount().multiply(rate);
                java.math.BigDecimal netAmount = payment.getAmount().subtract(commission);
                
                // Créditer le portefeuille
                walletService.creditWallet(
                    artist, 
                    netAmount, 
                    com.yowpainter.modules.payment.domain.model.WalletTransactionType.SALE, 
                    payment.getId(), 
                    "Vente " + (referenceType.equals("ORDER") ? "oeuvre" : "billet") + " #" + referenceId.toString().substring(0, 8)
                );
                
                emailService.sendNewSaleNotification(artist.getEmail(), referenceId.toString().substring(0, 8), netAmount);
                
            } else if ("SUBSCRIPTION".equals(referenceType)) {
                com.yowpainter.modules.subscription.domain.model.SubscriptionPlan plan = deducePlanFromAmount(payment.getAmount());
                subscriptionService.confirmUpgrade(referenceId, plan);
                notificationService.createNotification(payment.getUserId(), "Votre abonnement " + plan.name() + " a été activé avec succès !");
                emailService.sendPaymentConfirmation(user.getEmail(), "Abonnement " + plan.name(), payment.getAmount());
            }
        } finally {
            com.yowpainter.shared.context.OrganizationContext.clear();
        }
    }

    public void processFailedPayment(String providerReference, String externalReference, String status) {
        UUID referenceId = UUID.fromString(externalReference);
        log.warn("Processing failed payment for reference: {} with status: {}", referenceId, status);

        Artist artist = locateArtistForPayment(referenceId)
                .orElseThrow(() -> new IllegalArgumentException("Paiement non trouve pour la reference: " + referenceId));

        OrganizationContext.setOrganizationId(artist.getOrganizationId());
        try {
            markPaymentFailed(referenceId, providerReference, status);
        } finally {
            OrganizationContext.clear();
        }
    }

    /**
     * Marque un paiement en echec. Suppose {@link OrganizationContext} deja
     * positionne sur le tenant proprietaire du paiement.
     */
    private void markPaymentFailed(UUID referenceId, String providerReference, String status) {
        Payment payment = tenantTransactionExecutor.execute(() -> {
            Payment toUpdate = paymentRepository.findByReferenceId(referenceId)
                    .orElseThrow(() -> new IllegalArgumentException("Paiement non trouve pour la reference: " + referenceId));
            if (toUpdate.getStatus() == PaymentStatus.SUCCEEDED) {
                return null;
            }
            toUpdate.setStatus(PaymentStatus.FAILED);
            toUpdate.setProviderReference(providerReference);
            return paymentRepository.save(toUpdate);
        });

        if (payment == null) {
            log.warn("Attempt to fail an already SUCCEEDED payment: {}", referenceId);
            return;
        }

        notificationService.createNotification(payment.getUserId(), "Le paiement pour votre "
                + ("ORDER".equals(payment.getReferenceType()) ? "commande" : "réservation")
                + " a échoué. (" + status + ")");
    }

    /**
     * Retrouve l'artiste (donc le schema tenant) portant le paiement d'une
     * reference donnee. Le callback PSP arrive sans contexte d'organisation :
     * on balaye les tenants actifs, comme le fait deja KernelCommerceService
     * pour retrouver une commande.
     */
    private Optional<Artist> locateArtistForPayment(UUID referenceId) {
        for (Artist artist : artistRepository.findByStatus("ACTIVE")) {
            if (artist.getOrganizationId() == null) {
                continue;
            }
            try {
                OrganizationContext.setOrganizationId(artist.getOrganizationId());
                boolean found = tenantTransactionExecutor.execute(
                        () -> paymentRepository.findByReferenceId(referenceId).isPresent());
                if (found) {
                    return Optional.of(artist);
                }
            } catch (Exception ex) {
                log.debug("Tenant {} ignore pendant la recherche du paiement {}: {}",
                        artist.getOrganizationId(), referenceId, ex.getMessage());
            } finally {
                OrganizationContext.clear();
            }
        }
        return Optional.empty();
    }

    private com.yowpainter.modules.subscription.domain.model.SubscriptionPlan deducePlanFromAmount(BigDecimal amount) {
        if (amount.compareTo(new BigDecimal("25000")) >= 0) return com.yowpainter.modules.subscription.domain.model.SubscriptionPlan.ELITE;
        if (amount.compareTo(new BigDecimal("10000")) >= 0) return com.yowpainter.modules.subscription.domain.model.SubscriptionPlan.PRO;
        return com.yowpainter.modules.subscription.domain.model.SubscriptionPlan.FREE;
    }

    /**
     * Confirme aupres du kernel qu'un paiement a bien ete encaisse, pour le bon
     * montant et la bonne devise.
     * <p>
     * C'est le seul rempart contre un callback forge : l'endpoint
     * /api/payment/callback est public par nature (un PSP ne s'authentifie pas
     * aupres de nous), son contenu est donc traite comme une simple notification
     * a verifier. En cas de doute on refuse.
     * </p>
     */
    private boolean verifyPaymentWithKernel(Payment payment, UUID organizationId) {
        if (payment.getKernelOrderId() == null) {
            log.error("Paiement {} sans ordre kernel associe : verification impossible, rejet.", payment.getId());
            return false;
        }
        try {
            KernelPaymentOrderResponseDto order = kernelPaymentOrderPort.refreshPayment(
                    payment.getKernelOrderId(),
                    organizationId);

            if (!order.isSucceeded()) {
                log.warn("Le kernel ne confirme pas le paiement {} (statut kernel: {}).",
                        payment.getId(), order.status());
                return false;
            }
            if (order.amount() == null || order.amount().compareTo(payment.getAmount()) != 0) {
                log.error("Montant divergent pour le paiement {} : local={} kernel={}. Rejet.",
                        payment.getId(), payment.getAmount(), order.amount());
                return false;
            }
            if (order.currency() != null && !order.currency().equalsIgnoreCase(payment.getCurrency())) {
                log.error("Devise divergente pour le paiement {} : local={} kernel={}. Rejet.",
                        payment.getId(), payment.getCurrency(), order.currency());
                return false;
            }
            return true;
        } catch (Exception ex) {
            // Un kernel injoignable ne doit jamais valider un paiement.
            log.error("Verification kernel impossible pour le paiement {} : {}. Rejet.",
                    payment.getId(), ex.getMessage());
            return false;
        }
    }

    private PaymentResponse mapToPaymentResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .referenceId(payment.getReferenceId())
                .referenceType(payment.getReferenceType())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
