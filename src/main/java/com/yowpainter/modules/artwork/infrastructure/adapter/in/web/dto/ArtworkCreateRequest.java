package com.yowpainter.modules.artwork.infrastructure.adapter.in.web.dto;

import com.yowpainter.modules.artwork.domain.model.ArtworkStyle;
import com.yowpainter.modules.artwork.domain.model.ArtworkTechnique;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArtworkCreateRequest {

    @NotBlank(message = "Le titre est requis")
    private String title;

    private String description;

    @NotNull(message = "La technique est requise")
    private ArtworkTechnique technique;

    @NotNull(message = "Le style est requis")
    private ArtworkStyle style;

    private String dimensions;

    // Représenté sous forme de liste de strings pour faciliter l'API REST
    private List<String> tags;

    // Dans un vrai flow on upload les images d'abord et on envoie les URLs ici, 
    // ou on envoie les images en multipart. Ici on simule l'envoi d'URLs existantes.
    private List<String> imageUrls;

    private List<String> videoUrls;
}
