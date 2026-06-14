package com.yowpainter.modules.news.infrastructure.adapter.in.web.dto;

import com.yowpainter.modules.news.domain.model.NewsMediaType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NewsCreateRequest {

    private String title;

    @NotBlank(message = "Le commentaire est obligatoire")
    private String comment;

    private String imageUrl;

    private String videoUrl;

    @NotNull(message = "Le type de media est requis")
    private NewsMediaType mediaType;

    @AssertTrue(message = "imageUrl est requis pour mediaType IMAGE")
    public boolean isImageUrlValid() {
        if (mediaType == NewsMediaType.IMAGE) {
            return imageUrl != null && !imageUrl.isBlank();
        }
        return true;
    }

    @AssertTrue(message = "videoUrl est requis pour mediaType VIDEO")
    public boolean isVideoUrlValid() {
        if (mediaType == NewsMediaType.VIDEO) {
            return videoUrl != null && !videoUrl.isBlank();
        }
        return true;
    }
}
