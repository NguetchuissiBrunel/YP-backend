package com.yowpainter.modules.artwork.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.artwork.domain.model.ArtworkComment;
import com.yowpainter.modules.artwork.domain.port.out.ArtworkCommentRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ArtworkCommentRepositoryAdapter implements ArtworkCommentRepositoryPort {

    private final ArtworkCommentJpaRepository jpaRepository;

    @Override
    public ArtworkComment save(ArtworkComment comment) {
        return jpaRepository.save(comment);
    }

    @Override
    public java.util.List<ArtworkComment> findByArtworkIdOrderByCreatedAtDesc(UUID artworkId) {
        return jpaRepository.findByArtworkIdOrderByCreatedAtDesc(artworkId);
    }
}
