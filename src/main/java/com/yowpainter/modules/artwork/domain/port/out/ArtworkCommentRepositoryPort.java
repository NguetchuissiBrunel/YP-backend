package com.yowpainter.modules.artwork.domain.port.out;

import com.yowpainter.modules.artwork.domain.model.ArtworkComment;
import java.util.UUID;

public interface ArtworkCommentRepositoryPort {

    ArtworkComment save(ArtworkComment comment);
    java.util.List<ArtworkComment> findByArtworkIdOrderByCreatedAtDesc(UUID artworkId);
}
