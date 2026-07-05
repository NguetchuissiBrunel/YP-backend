package com.yowpainter.modules.news.domain.port.out;

import com.yowpainter.modules.news.domain.model.News;
import java.util.UUID;

public interface NewsRepositoryPort {

    News save(News news);
    java.util.Optional<News> findById(UUID id);
    java.util.List<News> findByArtistIdOrderByPublishedAtDesc(UUID artistId);
    void delete(News news);
}
