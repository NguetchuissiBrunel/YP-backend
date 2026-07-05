package com.yowpainter.modules.subscription.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.subscription.domain.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SubscriptionJpaRepository extends JpaRepository<Subscription, java.util.UUID> {

    java.util.Optional<Subscription> findByArtistId(UUID artistId);
}
