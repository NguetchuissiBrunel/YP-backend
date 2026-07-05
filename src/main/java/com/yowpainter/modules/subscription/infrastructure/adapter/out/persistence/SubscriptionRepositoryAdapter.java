package com.yowpainter.modules.subscription.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.subscription.domain.model.Subscription;
import com.yowpainter.modules.subscription.domain.port.out.SubscriptionRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SubscriptionRepositoryAdapter implements SubscriptionRepositoryPort {

    private final SubscriptionJpaRepository jpaRepository;

    @Override
    public Subscription save(Subscription subscription) {
        return jpaRepository.save(subscription);
    }

    @Override
    public java.util.Optional<Subscription> findByArtistId(UUID artistId) {
        return jpaRepository.findByArtistId(artistId);
    }
}
