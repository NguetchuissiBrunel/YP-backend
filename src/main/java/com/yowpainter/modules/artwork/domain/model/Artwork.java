package com.yowpainter.modules.artwork.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "artwork")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Artwork {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Lien vers l'artiste (tenant actuel). Pas de @ManyToOne avec l'entite Artist 
    // car on est dans le meme schema, mais garder un UUID est plus simple pour l'isolation.
    @Column(name = "artist_id", nullable = false)
    private UUID artistId;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private ArtworkTechnique technique;

    @Enumerated(EnumType.STRING)
    private ArtworkStyle style;

    // Ex: "100x80 cm"
    private String dimensions;

    // Stockage JSONB dans PostgreSQL pour les tags
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    private List<String> tags;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArtworkStatus status;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private int viewCount = 0;

    @Column(name = "like_count", nullable = false)
    @Builder.Default
    private int likeCount = 0;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // Relation avec les images
    @OneToMany(mappedBy = "artwork", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ArtworkImage> images = new ArrayList<>();

    // Relation avec les videos
    @OneToMany(mappedBy = "artwork", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ArtworkVideo> videos = new ArrayList<>();

    // Helper method pour gerer la relation bidirectionnelle
    public void addImage(ArtworkImage image) {
        images.add(image);
        image.setArtwork(this);
    }

    // Helper method pour les videos
    public void addVideo(ArtworkVideo video) {
        videos.add(video);
        video.setArtwork(this);
    }
}
