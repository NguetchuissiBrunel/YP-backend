package com.yowpainter.modules.artwork.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort;
import com.yowpainter.modules.artwork.domain.model.Artwork;
import com.yowpainter.modules.artwork.domain.model.ArtworkStyle;
import com.yowpainter.modules.artwork.domain.model.ArtworkTechnique;
import com.yowpainter.shared.context.OrganizationContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
@Slf4j
public class ArtworkRepositoryCustomImpl implements ArtworkRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Artwork> findPublicArtworks() {
        return entityManager.createQuery(
                "SELECT a FROM Artwork a WHERE a.status IN (com.yowpainter.modules.artwork.domain.model.ArtworkStatus.PUBLISHED, com.yowpainter.modules.artwork.domain.model.ArtworkStatus.ON_SALE, com.yowpainter.modules.artwork.domain.model.ArtworkStatus.SOLD)", 
                Artwork.class).getResultList();
    }

    @Override
    public List<Artwork> searchPublicArtworks(String query) {
        return entityManager.createQuery(
                "SELECT a FROM Artwork a WHERE a.status IN (com.yowpainter.modules.artwork.domain.model.ArtworkStatus.PUBLISHED, com.yowpainter.modules.artwork.domain.model.ArtworkStatus.ON_SALE, com.yowpainter.modules.artwork.domain.model.ArtworkStatus.SOLD) AND (LOWER(a.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(a.description) LIKE LOWER(CONCAT('%', :query, '%')))", 
                Artwork.class)
                .setParameter("query", query)
                .getResultList();
    }

    @Override
    public List<Artwork> findFeaturedArtworks() {
        return findPublicArtworks();
    }

    @Override
    public List<ArtworkStyle> findDistinctStyles() {
        return entityManager.createQuery("SELECT DISTINCT a.style FROM Artwork a WHERE a.style IS NOT NULL", ArtworkStyle.class).getResultList();
    }

    @Override
    public List<ArtworkTechnique> findDistinctTechniques() {
        return entityManager.createQuery("SELECT DISTINCT a.technique FROM Artwork a WHERE a.technique IS NOT NULL", ArtworkTechnique.class).getResultList();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> findDistinctTags() {
        return entityManager.createNativeQuery("SELECT DISTINCT jsonb_array_elements_text(a.tags) FROM artwork a WHERE a.tags IS NOT NULL").getResultList();
    }
}
