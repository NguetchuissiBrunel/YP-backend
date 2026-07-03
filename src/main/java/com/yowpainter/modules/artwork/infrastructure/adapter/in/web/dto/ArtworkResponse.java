package com.yowpainter.modules.artwork.infrastructure.adapter.in.web.dto;

import com.yowpainter.modules.artwork.domain.model.ArtworkStatus;
import com.yowpainter.modules.artwork.domain.model.ArtworkStyle;
import com.yowpainter.modules.artwork.domain.model.ArtworkTechnique;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ArtworkResponse {
    private UUID id;
    private UUID artistId;
    private String artistName;
    private String title;
    private String description;
    private ArtworkTechnique technique;
    private ArtworkStyle style;
    private String dimensions;
    private List<String> tags;
    private ArtworkStatus status;
    private int viewCount;
    private int likeCount;
    private List<String> imageUrls;
    private List<String> videoUrls;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
}
