package com.yowpainter.modules.news.infrastructure.adapter.in.web.dto;

import com.yowpainter.modules.news.domain.model.NewsMediaType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class NewsResponse {
    private UUID id;
    private UUID artistId;
    private String title;
    private String comment;
    private String imageUrl;
    private String videoUrl;
    private NewsMediaType mediaType;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
}
