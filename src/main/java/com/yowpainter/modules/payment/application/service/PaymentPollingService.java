package com.yowpainter.modules.payment.application.service;

import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort;
import com.yowpainter.modules.shop.domain.model.Payment;
import com.yowpainter.modules.shop.domain.model.PaymentStatus;
import com.yowpainter.modules.shop.domain.port.out.PaymentRepositoryPort;
import com.yowpainter.shared.context.OrganizationContext;
import com.yowpainter.shared.kernel.adapter.dto.KernelPaymentOrderResponseDto;
import com.yowpainter.shared.kernel.port.KernelPaymentOrderPort;
import com.yowpainter.shared.tenant.TenantTransactionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Rattrapage des paiements restes en attente.
 * <p>
 * Un callback peut se perdre (reseau, redemarrage, PSP muet). Le kernel expose
 * POST /api/payments/orders/{id}/refresh, qui force une reinterrogation du PSP :
 * on s'en sert comme filet de securite pour ne pas laisser une commande payee
 * bloquee en PENDING.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentPollingService {

    private final PaymentRepositoryPort paymentRepository;
    private final PaymentService paymentService;
    private final ArtistRepositoryPort artistRepository;
    private final KernelPaymentOrderPort kernelPaymentOrderPort;
    private final TenantTransactionExecutor tenantTransactionExecutor;

    /** Toutes les 10 minutes, on rattrape les paiements en attente depuis plus de 5 min. */
    @Scheduled(fixedDelay = 600000)
    public void pollPendingPayments() {
        List<Artist> activeArtists;
        try {
            activeArtists = artistRepository.findByStatus("ACTIVE");
        } catch (Exception e) {
            log.error("Failed to query active artists for payment polling", e);
            return;
        }

        for (Artist artist : activeArtists) {
            if (artist.getOrganizationId() == null) {
                continue;
            }
            List<Payment> pendingPayments;
            try {
                OrganizationContext.setOrganizationId(artist.getOrganizationId());
                LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
                pendingPayments = tenantTransactionExecutor.execute(
                        () -> paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, cutoff));
            } catch (Exception e) {
                log.error("Failed to poll payments for tenant {}", artist.getOrganizationId(), e);
                continue;
            } finally {
                OrganizationContext.clear();
            }

            // Hors contexte d'organisation : processSuccessfulPayment /
            // processFailedPayment resolvent eux-memes le tenant.
            for (Payment payment : pendingPayments) {
                refreshPayment(payment, artist);
            }
        }
    }

    private void refreshPayment(Payment payment, Artist artist) {
        if (payment.getKernelOrderId() == null) {
            log.warn("Paiement {} en attente sans ordre kernel : rien a rafraichir.", payment.getId());
            return;
        }
        try {
            KernelPaymentOrderResponseDto order = kernelPaymentOrderPort.refreshPayment(
                    payment.getKernelOrderId(),
                    artist.getOrganizationId());

            if (order.isSucceeded()) {
                log.info("Rattrapage : le kernel confirme le paiement {} (callback probablement perdu).", payment.getId());
                paymentService.processSuccessfulPayment(order.providerReference(), payment.getReferenceId().toString());
            } else if (order.isFailed()) {
                log.info("Rattrapage : le kernel signale l'echec du paiement {} ({}).", payment.getId(), order.status());
                paymentService.processFailedPayment(order.providerReference(), payment.getReferenceId().toString(), order.status());
            }
        } catch (Exception e) {
            log.error("Echec du rafraichissement du paiement {} aupres du kernel : {}", payment.getId(), e.getMessage());
        }
    }
}
