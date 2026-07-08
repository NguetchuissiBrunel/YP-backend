package com.yowpainter.modules.artwork.domain.port.out;

import com.yowpainter.modules.artwork.domain.model.Artwork;
import com.yowpainter.modules.artwork.domain.model.ArtworkStatus;
import com.yowpainter.modules.artwork.domain.model.ArtworkStyle;
import com.yowpainter.modules.artwork.domain.model.ArtworkTechnique;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArtworkRepositoryPort {

    Artwork save(Artwork artwork);

    <S extends Artwork> List<S> saveAll(Iterable<S> artworks);

    Optional<Artwork> findById(UUID id);

    List<Artwork> findAllById(Iterable<UUID> ids);

    void deleteAll(Iterable<? extends Artwork> artworks);

    List<Artwork> findByArtistId(UUID artistId);

    List<Artwork> findPublicArtworks();

    List<Artwork> findPublicArtworksByArtistId(UUID artistId);

    List<Artwork> findFeaturedArtworks();

    List<Artwork> searchPublicArtworks(String query);

    List<Artwork> searchPublicArtworksByArtistId(UUID artistId, String query);

    List<ArtworkStyle> findDistinctStyles();

    List<ArtworkTechnique> findDistinctTechniques();

    List<String> findDistinctTags();

    long count();

    long countByStatus(ArtworkStatus status);
}
