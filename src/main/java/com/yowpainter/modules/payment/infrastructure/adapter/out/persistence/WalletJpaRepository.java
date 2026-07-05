package com.yowpainter.modules.payment.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.payment.domain.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface WalletJpaRepository extends JpaRepository<Wallet, java.util.UUID> {

    java.util.Optional<Wallet> findByArtist_Id(UUID artistId);
}
