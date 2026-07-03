package com.yowpainter.modules.artwork.infrastructure.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtworkVideoUploadResponse {
    private UUID fileId;
    private String videoUrl;
}
