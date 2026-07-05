package com.yowpainter.modules.artwork.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.artwork.domain.model.ArtworkLike;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ArtworkLikeJpaRepository extends JpaRepository<ArtworkLike, java.util.UUID> {

    @org.springframework.data.jpa.repository.Query("SELECT l FROM ArtworkLike l WHERE l.artwork.id = :artworkId AND l.user.id = :userId")
    java.util.Optional<ArtworkLike> findByArtworkIdAndUserId(@org.springframework.data.repository.query.Param("artworkId") UUID artworkId, @org.springframework.data.repository.query.Param("userId") UUID userId);
}
