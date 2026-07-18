package com.yowpainter.modules.payment.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "wallet_transaction", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletTransactionType type;

    @Column(name = "reference_id")
    private UUID referenceId;

    private String description;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "blockchain_tx_id")
    private UUID blockchainTxId;

    @Column(name = "blockchain_tx_hash")
    private String blockchainTxHash;

}
