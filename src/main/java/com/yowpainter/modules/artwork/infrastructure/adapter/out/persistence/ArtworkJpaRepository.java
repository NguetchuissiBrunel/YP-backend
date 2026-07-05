package com.yowpainter.modules.artwork.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.artwork.domain.model.Artwork;
import com.yowpainter.modules.artwork.domain.model.ArtworkStatus;
import com.yowpainter.modules.artwork.domain.model.ArtworkStyle;
import com.yowpainter.modules.artwork.domain.model.ArtworkTechnique;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ArtworkJpaRepository extends JpaRepository<Artwork, UUID> {

    List<Artwork> findByArtistId(UUID artistId);

    @Query("SELECT a FROM Artwork a WHERE a.status IN (com.yowpainter.modules.artwork.domain.model.ArtworkStatus.PUBLISHED, com.yowpainter.modules.artwork.domain.model.ArtworkStatus.ON_SALE)")
    List<Artwork> findPublicArtworks();

    @Query("SELECT a FROM Artwork a WHERE a.artistId = :artistId AND a.status IN (com.yowpainter.modules.artwork.domain.model.ArtworkStatus.PUBLISHED, com.yowpainter.modules.artwork.domain.model.ArtworkStatus.ON_SALE)")
    List<Artwork> findPublicArtworksByArtistId(@Param("artistId") UUID artistId);

    @Query("SELECT a FROM Artwork a WHERE a.status IN (com.yowpainter.modules.artwork.domain.model.ArtworkStatus.PUBLISHED, com.yowpainter.modules.artwork.domain.model.ArtworkStatus.ON_SALE) ORDER BY a.likeCount DESC")
    List<Artwork> findFeaturedArtworks();

    @Query("SELECT a FROM Artwork a WHERE a.status IN (com.yowpainter.modules.artwork.domain.model.ArtworkStatus.PUBLISHED, com.yowpainter.modules.artwork.domain.model.ArtworkStatus.ON_SALE) AND (LOWER(a.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(a.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Artwork> searchPublicArtworks(@Param("query") String query);

    @Query("SELECT a FROM Artwork a WHERE a.artistId = :artistId AND a.status IN (com.yowpainter.modules.artwork.domain.model.ArtworkStatus.PUBLISHED, com.yowpainter.modules.artwork.domain.model.ArtworkStatus.ON_SALE) AND (LOWER(a.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(a.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Artwork> searchPublicArtworksByArtistId(@Param("artistId") UUID artistId, @Param("query") String query);

    @Query("SELECT DISTINCT a.style FROM Artwork a WHERE a.style IS NOT NULL")
    List<ArtworkStyle> findDistinctStyles();

    @Query("SELECT DISTINCT a.technique FROM Artwork a WHERE a.technique IS NOT NULL")
    List<ArtworkTechnique> findDistinctTechniques();

    @Query(value = "SELECT DISTINCT jsonb_array_elements_text(tags) FROM artwork WHERE tags IS NOT NULL", nativeQuery = true)
    List<String> findDistinctTags();

    long countByStatus(ArtworkStatus status);
}
