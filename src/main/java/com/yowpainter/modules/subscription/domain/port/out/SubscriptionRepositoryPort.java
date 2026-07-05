package com.yowpainter.modules.subscription.domain.port.out;

import com.yowpainter.modules.subscription.domain.model.Subscription;
import java.util.UUID;

public interface SubscriptionRepositoryPort {

    Subscription save(Subscription subscription);
    java.util.Optional<Subscription> findByArtistId(UUID artistId);
}
