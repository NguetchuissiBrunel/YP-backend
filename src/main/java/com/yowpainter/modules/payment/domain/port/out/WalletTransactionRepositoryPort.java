package com.yowpainter.modules.payment.domain.port.out;

import com.yowpainter.modules.payment.domain.model.WalletTransaction;
import java.util.UUID;

public interface WalletTransactionRepositoryPort {

    WalletTransaction save(WalletTransaction transaction);
    java.util.Optional<WalletTransaction> findById(UUID id);
    java.util.List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId);
}
