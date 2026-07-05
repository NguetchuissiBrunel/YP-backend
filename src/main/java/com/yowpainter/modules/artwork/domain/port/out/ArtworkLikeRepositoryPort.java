package com.yowpainter.modules.artwork.domain.port.out;

import com.yowpainter.modules.artwork.domain.model.ArtworkLike;
import java.util.UUID;

public interface ArtworkLikeRepositoryPort {

    java.util.Optional<ArtworkLike> findByArtworkIdAndUserId(UUID artworkId, UUID userId);
    ArtworkLike save(ArtworkLike like);
    void delete(ArtworkLike like);
    long count();
}
