package com.yowpainter.modules.news.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.news.domain.model.News;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface NewsJpaRepository extends JpaRepository<News, java.util.UUID> {

    java.util.List<News> findByArtistIdOrderByPublishedAtDesc(UUID artistId);
}
