package com.yowpainter.modules.payment.application.service;

import com.yowpainter.modules.shop.domain.model.Payment;
import com.yowpainter.modules.shop.domain.model.PaymentStatus;
import com.yowpainter.modules.shop.domain.port.out.PaymentRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service de polling des paiements en attente.
 * <p>
 * L'intégration CamPay a été retirée. Ce scheduler sera réactivé
 * dès que les endpoints de paiement Kernel seront disponibles.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentPollingService {

    private final PaymentRepositoryPort paymentRepository;
    private final PaymentService paymentService;
    private final com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort artistRepository;

    /**
     * S'exécute toutes les 10 minutes pour vérifier les paiements en attente.
     * Actuellement désactivé — sera réactivé avec l'intégration Kernel-Paiement.
     */
    @Scheduled(fixedDelay = 600000) // 10 minutes
    public void pollPendingPayments() {
        log.debug("Payment polling skipped: no payment provider configured yet (Kernel payment integration pending).");

        List<com.yowpainter.modules.artist.domain.model.Artist> activeArtists;
        try {
            activeArtists = artistRepository.findByStatus("ACTIVE");
        } catch (Exception e) {
            log.error("Failed to query active artists for payment polling", e);
            return;
        }

        for (com.yowpainter.modules.artist.domain.model.Artist artist : activeArtists) {
            if (artist.getOrganizationId() == null) {
                continue;
            }
            try {
                com.yowpainter.shared.context.OrganizationContext.setOrganizationId(artist.getOrganizationId());
                LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
                List<Payment> pendingPayments = paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, cutoff);
                if (!pendingPayments.isEmpty()) {
                    log.info("Found {} pending payments for tenant {} — will be processed once Kernel payment endpoints are available.",
                            pendingPayments.size(), artist.getOrganizationId());
                    // TODO: appeler checkAndUpdatePaymentStatus(payment) avec l'API Kernel
                }
            } catch (Exception e) {
                log.error("Failed to poll payments for tenant {}", artist.getOrganizationId(), e);
            } finally {
                com.yowpainter.shared.context.OrganizationContext.clear();
            }
        }
    }
}
