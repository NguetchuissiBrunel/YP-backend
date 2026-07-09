package com.yowpainter.modules.payment.application.service;

import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort;
import com.yowpainter.modules.payment.domain.model.WalletTransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service de retrait (payout) vers les artistes.
 * <p>
 * L'intégration Mobile Money a été retirée.
 * Un nouveau fournisseur de paiement sera branché ici.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutService {

    private final WalletService walletService;
    private final ArtistRepositoryPort artistRepository;

    @Transactional
    public String requestPayout(String artistEmail, BigDecimal amount) {
        Artist artist = artistRepository.findByEmail(artistEmail)
                .orElseThrow(() -> new IllegalArgumentException("Artiste non trouvé"));

        if (artist.getPayoutPhone() == null || artist.getPayoutPhone().isEmpty()) {
            throw new IllegalStateException("Numéro de retrait non configuré pour cet artiste");
        }

        log.info("Payout requested for artist {} ({}), amount: {}. Payment provider not yet configured.",
                artist.getArtistName(), artist.getPayoutPhone(), amount);

        // 1. Débiter le portefeuille localement d'abord (Sécurité)
        UUID payoutId = UUID.randomUUID();
        walletService.debitWallet(
            artist,
            amount,
            WalletTransactionType.WITHDRAWAL,
            payoutId,
            "Retrait vers " + artist.getPayoutPhone()
        );

        // 2. Envoi vers le fournisseur de paiement — à implémenter
        throw new UnsupportedOperationException(
                "Le virement Mobile Money n'est pas encore disponible. " +
                "Un nouveau système de paiement sera bientôt intégré.");
    }
}
