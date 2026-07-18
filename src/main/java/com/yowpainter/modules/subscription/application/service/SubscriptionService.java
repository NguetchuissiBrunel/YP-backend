package com.yowpainter.modules.subscription.application.service;

import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort;
import com.yowpainter.modules.subscription.domain.model.Subscription;
import com.yowpainter.modules.subscription.domain.model.SubscriptionPlan;
import com.yowpainter.modules.subscription.domain.port.out.SubscriptionRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class SubscriptionService {

    private final SubscriptionRepositoryPort subscriptionRepository;
    private final ArtistRepositoryPort artistRepository;
    private final com.yowpainter.modules.payment.application.service.PaymentService paymentService;

    public SubscriptionService(
            SubscriptionRepositoryPort subscriptionRepository,
            ArtistRepositoryPort artistRepository,
            @org.springframework.context.annotation.Lazy com.yowpainter.modules.payment.application.service.PaymentService paymentService) {
        this.subscriptionRepository = subscriptionRepository;
        this.artistRepository = artistRepository;
        this.paymentService = paymentService;
    }

    public Subscription getSubscriptionForArtist(String email) {
        Artist artist = artistRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Artiste non trouvé"));
        return subscriptionRepository.findByArtistId(artist.getId())
                .orElseGet(() -> createDefaultSubscription(artist.getId()));
    }

    @Transactional
    public Subscription createDefaultSubscription(UUID artistId) {
        return subscriptionRepository.save(Subscription.builder()
                .artistId(artistId)
                .plan(SubscriptionPlan.FREE)
                .startDate(LocalDateTime.now())
                .isActive(true)
                .build());
    }

    @Transactional
    public void upgradePlan(String email, SubscriptionPlan plan) {
        Artist artist = artistRepository.findByEmail(email).orElseThrow();
        Subscription sub = subscriptionRepository.findByArtistId(artist.getId())
                .orElseGet(() -> createDefaultSubscription(artist.getId()));
        
        sub.setPlan(plan);
        sub.setStartDate(LocalDateTime.now());
        // Simuler 30 jours
        sub.setEndDate(LocalDateTime.now().plusDays(30));
        subscriptionRepository.save(sub);
    }

    /**
     * Pas de {@code @Transactional} ici : PaymentService doit positionner le
     * contexte d'organisation avant d'ouvrir sa transaction tenant.
     */
    public com.yowpainter.modules.payment.application.service.PaymentInitiationResult initiateSubscriptionUpgrade(
            String email, SubscriptionPlan plan, String phoneNumber) {
        Artist artist = artistRepository.findByEmail(email).orElseThrow();

        // L'ID de l'artiste sert de reference : la cle d'idempotence derivee
        // garantit qu'une relance ne cree pas un second encaissement.
        return paymentService.initiatePayment(
                artist.getId(),
                "SUBSCRIPTION",
                plan.getPrice(),
                artist.getSlug(),
                email,
                phoneNumber
        );
    }

    @Transactional
    public void confirmUpgrade(UUID artistId, SubscriptionPlan plan) {
        Subscription sub = subscriptionRepository.findByArtistId(artistId)
                .orElseGet(() -> createDefaultSubscription(artistId));
        
        sub.setPlan(plan);
        sub.setStartDate(LocalDateTime.now());
        sub.setEndDate(LocalDateTime.now().plusDays(30));
        sub.setActive(true);
        subscriptionRepository.save(sub);
    }

    @Transactional
    public void cancelSubscription(String email) {
        Artist artist = artistRepository.findByEmail(email).orElseThrow();
        subscriptionRepository.findByArtistId(artist.getId()).ifPresent(sub -> {
            sub.setActive(false);
            subscriptionRepository.save(sub);
        });
    }
}
