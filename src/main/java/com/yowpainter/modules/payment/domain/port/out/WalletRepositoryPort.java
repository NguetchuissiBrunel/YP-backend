package com.yowpainter.modules.payment.domain.port.out;

import com.yowpainter.modules.payment.domain.model.Wallet;
import java.util.UUID;

public interface WalletRepositoryPort {

    Wallet save(Wallet wallet);
    java.util.Optional<Wallet> findByArtistId(UUID artistId);
}
