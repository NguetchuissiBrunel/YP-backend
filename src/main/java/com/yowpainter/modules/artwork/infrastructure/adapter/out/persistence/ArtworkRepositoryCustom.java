package com.yowpainter.modules.artwork.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.artwork.domain.model.Artwork;
import com.yowpainter.modules.artwork.domain.model.ArtworkStyle;
import com.yowpainter.modules.artwork.domain.model.ArtworkTechnique;

import java.util.List;

public interface ArtworkRepositoryCustom {
    List<Artwork> findPublicArtworks();
    List<Artwork> searchPublicArtworks(String query);
    List<Artwork> findFeaturedArtworks();
    List<ArtworkStyle> findDistinctStyles();
    List<ArtworkTechnique> findDistinctTechniques();
    List<String> findDistinctTags();
}
