package com.yowpainter.modules.artwork.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.artwork.domain.model.ArtworkComment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ArtworkCommentJpaRepository extends JpaRepository<ArtworkComment, java.util.UUID> {

    @org.springframework.data.jpa.repository.Query("SELECT c FROM ArtworkComment c WHERE c.artwork.id = :artworkId ORDER BY c.createdAt DESC")
    java.util.List<ArtworkComment> findByArtworkIdOrderByCreatedAtDesc(@org.springframework.data.repository.query.Param("artworkId") UUID artworkId);
}
