package com.yowpainter.modules.artwork.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.artwork.domain.model.ArtworkLike;
import com.yowpainter.modules.artwork.domain.port.out.ArtworkLikeRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ArtworkLikeRepositoryAdapter implements ArtworkLikeRepositoryPort {

    private final ArtworkLikeJpaRepository jpaRepository;

    @Override
    public java.util.Optional<ArtworkLike> findByArtworkIdAndUserId(UUID artworkId, UUID userId) {
        return jpaRepository.findByArtworkIdAndUserId(artworkId, userId);
    }

    @Override
    public ArtworkLike save(ArtworkLike like) {
        return jpaRepository.save(like);
    }

    @Override
    public void delete(ArtworkLike like) {
        jpaRepository.delete(like);
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }
}
