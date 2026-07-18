package com.yowpainter.modules.payment.application.service;

import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.payment.domain.model.Wallet;
import com.yowpainter.modules.payment.domain.model.WalletTransaction;
import com.yowpainter.modules.payment.domain.model.WalletTransactionType;
import com.yowpainter.modules.payment.domain.port.out.WalletRepositoryPort;
import com.yowpainter.modules.payment.domain.port.out.WalletTransactionRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import com.yowpainter.shared.kernel.port.KernelWalletPort;
import com.yowpainter.shared.kernel.KernelBootstrapAdminSession;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepositoryPort walletRepository;
    private final WalletTransactionRepositoryPort transactionRepository;
    private final KernelWalletPort kernelWalletPort;
    private final KernelBootstrapAdminSession bootstrapAdminSession;
    private final com.yowpainter.shared.tenant.TenantTransactionExecutor tenantTransactionExecutor;

    public Wallet getWalletForArtist(Artist artist) {
        return walletRepository.findByArtistId(artist.getId())
                .orElseGet(() -> createWallet(artist));
    }

    @Transactional
    public Wallet createWallet(Artist artist) {
        log.info("Creating new wallet for artist: {}", artist.getArtistName());
        Wallet wallet = Wallet.builder()
                .artist(artist)
                .balance(BigDecimal.ZERO)
                .currency("XAF")
                .build();
        return walletRepository.save(wallet);
    }

    @Transactional
    public Wallet createBlockchainWallet(Artist artist, String accessToken) {
        log.info("Creating new blockchain wallet for artist: {}", artist.getArtistName());
        
        com.yowpainter.shared.kernel.adapter.dto.KernelGeneratedWalletResponseDto generated = null;
        try {
            generated = kernelWalletPort.createWallet(artist.getOrganizationId(), artist.getArtistName() + " Wallet", accessToken);
        } catch (Exception e) {
            log.error("Failed to create wallet on kernel blockchain: {}", e.getMessage());
        }

        Wallet wallet = walletRepository.findByArtistId(artist.getId())
                .orElseGet(() -> Wallet.builder()
                        .artist(artist)
                        .balance(BigDecimal.ZERO)
                        .currency("XAF")
                        .build());

        if (generated != null) {
            wallet.setBlockchainWalletId(generated.id());
            wallet.setBlockchainPublicKey(generated.publicKey());
            wallet.setBlockchainPrivateKey(generated.privateKey());
            wallet.setBlockchainFingerprint(generated.fingerprint());
        }

        return walletRepository.save(wallet);
    }

    @Transactional
    public void creditWallet(Artist artist, BigDecimal amount, WalletTransactionType type, UUID referenceId, String description) {
        Wallet wallet = getWalletForArtist(artist);

        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        WalletTransaction transaction = WalletTransaction.builder()
                .wallet(wallet)
                .amount(amount)
                .type(type)
                .referenceId(referenceId)
                .description(description)
                .build();
        transactionRepository.save(transaction);

        scheduleBlockchainAnchoring(wallet, transaction);

        log.info("Credited {} to wallet of artist {} (Type: {})", amount, artist.getArtistName(), type);
    }

    @Transactional
    public void debitWallet(Artist artist, BigDecimal amount, WalletTransactionType type, UUID referenceId, String description) {
        Wallet wallet = getWalletForArtist(artist);

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Solde insuffisant dans le portefeuille");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);

        WalletTransaction transaction = WalletTransaction.builder()
                .wallet(wallet)
                .amount(amount.negate())
                .type(type)
                .referenceId(referenceId)
                .description(description)
                .build();
        transactionRepository.save(transaction);

        scheduleBlockchainAnchoring(wallet, transaction);

        log.info("Debited {} from wallet of artist {} (Type: {})", amount, artist.getArtistName(), type);
    }

    /**
     * Donnees necessaires a l'ancrage, capturees pendant la transaction.
     * <p>
     * L'ancrage s'execute apres le commit, hors session Hibernate : on ne peut
     * plus y traverser d'association paresseuse (wallet.getArtist()).
     * </p>
     */
    private record AnchorCommand(
            UUID transactionId,
            String transactionType,
            BigDecimal amount,
            UUID referenceId,
            UUID organizationId,
            String publicKey,
            String privateKey
    ) {}

    /**
     * Programme l'ancrage blockchain <strong>apres le commit</strong>.
     * <p>
     * Ancrer pendant la transaction avait deux defauts : un aller-retour reseau
     * tenait une connexion DB ouverte, et surtout un rollback ulterieur laissait
     * sur la chaine une transaction correspondant a un mouvement annule en base
     * (cas du retrait dans PayoutService). La chaine etant irreversible, elle ne
     * doit refleter que des mouvements definitivement committes.
     * </p>
     */
    private void scheduleBlockchainAnchoring(Wallet wallet, WalletTransaction transaction) {
        if (wallet.getBlockchainWalletId() == null) {
            log.warn("Wallet has no blockchain wallet ID, skipping blockchain anchoring.");
            return;
        }

        AnchorCommand command = new AnchorCommand(
                transaction.getId(),
                transaction.getType().name(),
                transaction.getAmount(),
                transaction.getReferenceId(),
                wallet.getArtist().getOrganizationId(),
                wallet.getBlockchainPublicKey(),
                wallet.getBlockchainPrivateKey()
        );

        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            anchorOnBlockchain(command);
            return;
        }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        anchorOnBlockchain(command);
                    }
                });
    }

    private void anchorOnBlockchain(AnchorCommand anchor) {
        try {
            String adminToken = bootstrapAdminSession.getBootstrapAdminAccessToken();
            String payload = String.format("{\"transactionId\":\"%s\",\"type\":\"%s\",\"amount\":%f,\"referenceId\":\"%s\"}",
                    anchor.transactionId(), anchor.transactionType(), anchor.amount().doubleValue(), anchor.referenceId());

            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            String payloadHashVal = hexString.toString();

            String signature = kernelWalletPort.signPayload(anchor.privateKey(), payloadHashVal, adminToken);

            var command = new com.yowpainter.shared.kernel.adapter.dto.KernelSubmitTransactionRequestDto(
                    anchor.organizationId(),
                    "COMOPS_MAIN",
                    anchor.transactionType(),
                    "YOWPAINTER_WALLET",
                    anchor.transactionId().toString(),
                    payload,
                    payloadHashVal,
                    anchor.publicKey(),
                    signature
            );

            var txResponse = kernelWalletPort.submitTransaction(command, adminToken);

            // Le commit a deja eu lieu : il faut une nouvelle transaction pour
            // rattacher les references de chaine au mouvement.
            tenantTransactionExecutor.execute(() -> transactionRepository.findById(anchor.transactionId())
                    .ifPresent(tx -> {
                        tx.setBlockchainTxId(txResponse.id());
                        tx.setBlockchainTxHash(txResponse.transactionHash());
                        transactionRepository.save(tx);
                    }));

            log.info("Successfully anchored transaction {} on blockchain with txHash: {}",
                    anchor.transactionId(), txResponse.transactionHash());
        } catch (Exception ex) {
            log.error("Failed to anchor transaction {} on blockchain: {}", anchor.transactionId(), ex.getMessage(), ex);
        }
    }

    public List<WalletTransaction> getTransactionHistory(UUID artistId) {
        Wallet wallet = walletRepository.findByArtistId(artistId)
                .orElseThrow(() -> new IllegalArgumentException("Portefeuille non trouvé"));
        return transactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId());
    }
}

