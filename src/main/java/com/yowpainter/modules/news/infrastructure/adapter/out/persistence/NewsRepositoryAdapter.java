package com.yowpainter.modules.news.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.news.domain.model.News;
import com.yowpainter.modules.news.domain.port.out.NewsRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NewsRepositoryAdapter implements NewsRepositoryPort {

    private final NewsJpaRepository jpaRepository;

    @Override
    public News save(News news) {
        return jpaRepository.save(news);
    }

    @Override
    public java.util.Optional<News> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public java.util.List<News> findByArtistIdOrderByPublishedAtDesc(UUID artistId) {
        return jpaRepository.findByArtistIdOrderByPublishedAtDesc(artistId);
    }

    @Override
    public void delete(News news) {
        jpaRepository.delete(news);
    }
}
