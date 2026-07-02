package com.yowpainter.modules.news.application.service;

import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort;
import com.yowpainter.modules.news.domain.model.News;
import com.yowpainter.modules.news.domain.port.out.NewsRepositoryPort;
import com.yowpainter.modules.news.infrastructure.adapter.in.web.dto.NewsCreateRequest;
import com.yowpainter.modules.news.infrastructure.adapter.in.web.dto.NewsResponse;
import com.yowpainter.shared.context.OrganizationContext;
import com.yowpainter.shared.tenant.TenantTransactionExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NewsService {

    private final NewsRepositoryPort newsRepository;
    private final ArtistRepositoryPort artistRepository;
    private final TenantTransactionExecutor tenantTransactionExecutor;

    @Transactional
    public NewsResponse createNews(String artistEmail, NewsCreateRequest request) {
        Artist artist = artistRepository.findByEmail(artistEmail)
                .orElseThrow(() -> new IllegalArgumentException("Artiste non trouve"));

        News news = News.builder()
                .artistId(artist.getId())
                .title(request.getTitle())
                .comment(request.getComment())
                .imageUrl(request.getImageUrl())
                .videoUrl(request.getVideoUrl())
                .mediaType(request.getMediaType())
                .build();

        return mapToResponse(newsRepository.save(news));
    }

    public List<NewsResponse> getMyNews(String artistEmail) {
        Artist artist = artistRepository.findByEmail(artistEmail)
                .orElseThrow(() -> new IllegalArgumentException("Artiste non trouve"));

        return newsRepository.findByArtistIdOrderByPublishedAtDesc(artist.getId()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<NewsResponse> getNewsByArtistSlug(String slug) {
        Artist artist = artistRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Artiste non trouve"));
        if (artist.getOrganizationId() == null) {
            return List.of();
        }
        try {
            OrganizationContext.setOrganizationId(artist.getOrganizationId());
            return tenantTransactionExecutor.execute(() ->
                    newsRepository.findByArtistIdOrderByPublishedAtDesc(artist.getId()).stream()
                            .map(this::mapToResponse)
                            .collect(Collectors.toList())
            );
        } finally {
            OrganizationContext.clear();
        }
    }

    @Transactional
    public NewsResponse updateNews(UUID id, String artistEmail, NewsCreateRequest request) {
        News news = newsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Actualite non trouvee"));
        Artist artist = artistRepository.findByEmail(artistEmail)
                .orElseThrow(() -> new IllegalArgumentException("Artiste non trouve"));

        if (!news.getArtistId().equals(artist.getId())) {
            throw new IllegalArgumentException("Non autorise");
        }

        news.setTitle(request.getTitle());
        news.setComment(request.getComment());
        news.setImageUrl(request.getImageUrl());
        news.setVideoUrl(request.getVideoUrl());
        news.setMediaType(request.getMediaType());

        return mapToResponse(newsRepository.save(news));
    }

    @Transactional
    public void deleteNews(UUID id, String artistEmail) {
        News news = newsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Actualite non trouvee"));
        Artist artist = artistRepository.findByEmail(artistEmail)
                .orElseThrow(() -> new IllegalArgumentException("Artiste non trouve"));

        if (!news.getArtistId().equals(artist.getId())) {
            throw new IllegalArgumentException("Non autorise");
        }

        newsRepository.delete(news);
    }

    private NewsResponse mapToResponse(News news) {
        return NewsResponse.builder()
                .id(news.getId())
                .artistId(news.getArtistId())
                .title(news.getTitle())
                .comment(news.getComment())
                .imageUrl(com.yowpainter.shared.utils.UrlSanitizer.sanitizeFileUrl(news.getImageUrl()))
                .videoUrl(com.yowpainter.shared.utils.UrlSanitizer.sanitizeFileUrl(news.getVideoUrl()))
                .mediaType(news.getMediaType())
                .publishedAt(news.getPublishedAt())
                .createdAt(news.getCreatedAt())
                .build();
    }
}
