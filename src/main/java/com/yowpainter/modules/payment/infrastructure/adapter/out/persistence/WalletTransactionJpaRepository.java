package com.yowpainter.modules.payment.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.payment.domain.model.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface WalletTransactionJpaRepository extends JpaRepository<WalletTransaction, java.util.UUID> {

    @org.springframework.data.jpa.repository.Query("SELECT t FROM WalletTransaction t WHERE t.wallet.id = :walletId ORDER BY t.createdAt DESC")
    java.util.List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(@org.springframework.data.repository.query.Param("walletId") UUID walletId);
}
