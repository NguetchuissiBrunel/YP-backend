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
 * Le kernel core expose l'<em>encaissement</em> (POST /api/payments/orders) mais
 * aucun endpoint de <em>decaissement</em> vers un compte Mobile Money externe :
 * le retrait reste donc indisponible tant que le kernel ne l'expose pas.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutService {

    private final WalletService walletService;
    private final ArtistRepositoryPort artistRepository;

    /**
     * Demande de retrait.
     * <p>
     * L'ancienne implementation debitait le portefeuille <em>avant</em> de lever
     * l'exception d'indisponibilite. Le rollback annulait bien le debit en base,
     * mais l'ancrage blockchain declenche par le debit, lui, n'etait pas
     * reversible : chaque tentative gravait sur la chaine un retrait qui n'avait
     * jamais eu lieu. On echoue donc desormais avant tout mouvement.
     * </p>
     */
    public String requestPayout(String artistEmail, BigDecimal amount) {
        Artist artist = artistRepository.findByEmail(artistEmail)
                .orElseThrow(() -> new IllegalArgumentException("Artiste non trouvé"));

        if (artist.getPayoutPhone() == null || artist.getPayoutPhone().isEmpty()) {
            throw new IllegalStateException("Numéro de retrait non configuré pour cet artiste");
        }

        log.info("Payout requested for artist {} ({}), amount: {} — refusé : le kernel n'expose pas encore de décaissement.",
                artist.getArtistName(), artist.getPayoutPhone(), amount);

        throw new UnsupportedOperationException(
                "Le retrait vers Mobile Money n'est pas encore disponible : " +
                "le kernel core n'expose pas d'endpoint de décaissement. " +
                "Votre solde reste acquis et sera retirable dès son ouverture.");
    }
}
