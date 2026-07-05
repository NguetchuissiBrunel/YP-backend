package com.yowpainter.modules.payment.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.payment.domain.model.WalletTransaction;
import com.yowpainter.modules.payment.domain.port.out.WalletTransactionRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WalletTransactionRepositoryAdapter implements WalletTransactionRepositoryPort {

    private final WalletTransactionJpaRepository jpaRepository;

    @Override
    public WalletTransaction save(WalletTransaction transaction) {
        return jpaRepository.save(transaction);
    }

    @Override
    public java.util.List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId) {
        return jpaRepository.findByWalletIdOrderByCreatedAtDesc(walletId);
    }
}
