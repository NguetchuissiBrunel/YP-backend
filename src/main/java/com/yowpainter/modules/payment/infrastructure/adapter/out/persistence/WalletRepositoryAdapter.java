package com.yowpainter.modules.payment.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.payment.domain.model.Wallet;
import com.yowpainter.modules.payment.domain.port.out.WalletRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WalletRepositoryAdapter implements WalletRepositoryPort {

    private final WalletJpaRepository jpaRepository;

    @Override
    public Wallet save(Wallet wallet) {
        return jpaRepository.save(wallet);
    }

    @Override
    public java.util.Optional<Wallet> findByArtistId(UUID artistId) {
        return jpaRepository.findByArtist_Id(artistId);
    }
}
