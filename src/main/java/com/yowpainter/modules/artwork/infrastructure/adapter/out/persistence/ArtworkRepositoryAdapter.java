package com.yowpainter.modules.artwork.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.artwork.domain.model.Artwork;
import com.yowpainter.modules.artwork.domain.model.ArtworkStatus;
import com.yowpainter.modules.artwork.domain.model.ArtworkStyle;
import com.yowpainter.modules.artwork.domain.model.ArtworkTechnique;
import com.yowpainter.modules.artwork.domain.port.out.ArtworkRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ArtworkRepositoryAdapter implements ArtworkRepositoryPort {

    private final ArtworkJpaRepository jpaRepository;

    @Override
    public Artwork save(Artwork artwork) {
        return jpaRepository.save(artwork);
    }

    @Override
    public <S extends Artwork> List<S> saveAll(Iterable<S> artworks) {
        return jpaRepository.saveAll(artworks);
    }

    @Override
    public Optional<Artwork> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Artwork> findAllById(Iterable<UUID> ids) {
        return jpaRepository.findAllById(ids);
    }

    @Override
    public void deleteAll(Iterable<? extends Artwork> artworks) {
        jpaRepository.deleteAll(artworks);
    }

    @Override
    public List<Artwork> findByArtistId(UUID artistId) {
        return jpaRepository.findByArtistId(artistId);
    }

    @Override
    public List<Artwork> findPublicArtworks() {
        return jpaRepository.findPublicArtworks();
    }

    @Override
    public List<Artwork> findPublicArtworksByArtistId(UUID artistId) {
        return jpaRepository.findPublicArtworksByArtistId(artistId);
    }

    @Override
    public List<Artwork> findFeaturedArtworks() {
        return jpaRepository.findFeaturedArtworks();
    }

    @Override
    public List<Artwork> searchPublicArtworks(String query) {
        return jpaRepository.searchPublicArtworks(query);
    }

    @Override
    public List<Artwork> searchPublicArtworksByArtistId(UUID artistId, String query) {
        return jpaRepository.searchPublicArtworksByArtistId(artistId, query);
    }

    @Override
    public List<ArtworkStyle> findDistinctStyles() {
        return jpaRepository.findDistinctStyles();
    }

    @Override
    public List<ArtworkTechnique> findDistinctTechniques() {
        return jpaRepository.findDistinctTechniques();
    }

    @Override
    public List<String> findDistinctTags() {
        return jpaRepository.findDistinctTags();
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }

    @Override
    public long countByStatus(ArtworkStatus status) {
        return jpaRepository.countByStatus(status);
    }
}
