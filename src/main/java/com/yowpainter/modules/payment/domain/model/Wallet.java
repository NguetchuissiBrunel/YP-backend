package com.yowpainter.modules.payment.domain.model;

import com.yowpainter.modules.artist.domain.model.Artist;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "wallet", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "artist_id", referencedColumnName = "id", nullable = false)
    private Artist artist;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(length = 10)
    @Builder.Default
    private String currency = "XAF";

    @UpdateTimestamp
    @Column(name = "last_updated_at")
    private LocalDateTime lastUpdatedAt;

    @Column(name = "blockchain_wallet_id")
    private UUID blockchainWalletId;

    @Column(name = "blockchain_public_key")
    private String blockchainPublicKey;

    @Column(name = "blockchain_private_key")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String blockchainPrivateKey;

    @Column(name = "blockchain_fingerprint")
    private String blockchainFingerprint;

}
