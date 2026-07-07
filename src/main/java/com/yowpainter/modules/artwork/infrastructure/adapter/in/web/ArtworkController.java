package com.yowpainter.modules.artwork.infrastructure.adapter.in.web;

import com.yowpainter.modules.artwork.infrastructure.adapter.in.web.dto.ArtworkCreateRequest;
import com.yowpainter.modules.artwork.infrastructure.adapter.in.web.dto.ArtworkResponse;
import com.yowpainter.modules.artwork.infrastructure.adapter.in.web.dto.CommentRequest;
import com.yowpainter.modules.artwork.infrastructure.adapter.in.web.dto.CommentResponse;
import com.yowpainter.modules.artwork.domain.model.ArtworkStatus;
import com.yowpainter.modules.artwork.domain.model.ArtworkStyle;
import com.yowpainter.modules.artwork.domain.model.ArtworkTechnique;
import com.yowpainter.modules.artwork.application.service.ArtworkService;
import com.yowpainter.shared.security.AuthenticatedUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import com.yowpainter.modules.artwork.infrastructure.adapter.in.web.dto.ArtworkImageUploadResponse;
import com.yowpainter.modules.artwork.infrastructure.adapter.in.web.dto.ArtworkVideoUploadResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Artworks", description = "Gestion complete des oeuvres (Public & Artist)")
public class ArtworkController {

    private final ArtworkService artworkService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @GetMapping("/v1/public/{artistSlug}/artworks")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Lister les oeuvres d'une boutique (tenant spécifique)")
    public ResponseEntity<List<ArtworkResponse>> getAllPublicArtworks(@PathVariable String artistSlug) {
        return ResponseEntity.ok(artworkService.getPublicArtworksByArtistSlug(artistSlug));
    }

    @GetMapping("/v1/public/{artistSlug}/artworks/{id}")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Voir les details d'une oeuvre dans une boutique")
    public ResponseEntity<ArtworkResponse> getArtwork(@PathVariable String artistSlug, @PathVariable UUID id) {
        return ResponseEntity.ok(artworkService.getArtworkByIdAndArtistSlug(id, artistSlug));
    }

    @GetMapping("/v1/public/{artistSlug}/artworks/search")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Rechercher des oeuvres dans une boutique spécifique")
    public ResponseEntity<List<ArtworkResponse>> searchArtworks(@PathVariable String artistSlug, @RequestParam String q) {
        return ResponseEntity.ok(artworkService.searchArtworksByArtistSlug(artistSlug, q));
    }

    @PostMapping(value = "/artworks/images/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Uploader une image d'oeuvre via le kernel (mode kernel uniquement)")
    public ResponseEntity<ArtworkImageUploadResponse> uploadArtworkImage(
            Authentication authentication,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(artworkService.uploadArtworkImage(
                        authenticatedUserResolver.requireEmail(authentication),
                        file
                ));
    }

    @PostMapping(value = "/artworks/videos/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Uploader une vidéo d'oeuvre via le kernel")
    public ResponseEntity<ArtworkVideoUploadResponse> uploadArtworkVideo(
            Authentication authentication,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(artworkService.uploadArtworkVideo(
                        authenticatedUserResolver.requireEmail(authentication),
                        file
                ));
    }

    @PostMapping("/artworks")
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Créer une oeuvre (Artiste)")
    public ResponseEntity<ArtworkResponse> createArtwork(
            Authentication authentication,
            @Valid @RequestBody ArtworkCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(artworkService.createArtwork(
                        authenticatedUserResolver.requireEmail(authentication),
                        request
                ));
    }

    @GetMapping("/artworks/me")
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Lister mes oeuvres (Artiste - Dashboard)")
    public ResponseEntity<List<ArtworkResponse>> getMyArtworks(Authentication authentication) {
        return ResponseEntity.ok(artworkService.getMyArtworks(
                authenticatedUserResolver.requireEmail(authentication)
        ));
    }

    @GetMapping("/public/artworks/latest")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Recuperer les oeuvres récentes (tous artistes confondus)")
    public ResponseEntity<List<ArtworkResponse>> getLatestArtworks() {
        return ResponseEntity.ok(artworkService.getPublicArtworks());
    }

    @GetMapping("/public/artworks/featured")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Recuperer les oeuvres mises en avant")
    public ResponseEntity<List<ArtworkResponse>> getFeatured() {
        return ResponseEntity.ok(artworkService.getFeaturedArtworks());
    }

    @PostMapping("/v1/public/{artistSlug}/artworks/{id}/like")
    @PreAuthorize("hasAnyRole('ARTIST', 'BUYER')")
    @Operation(summary = "Liker ou unliker une oeuvre dans une boutique spécifique")
    public ResponseEntity<Void> toggleLike(
            @PathVariable String artistSlug,
            @PathVariable UUID id,
            Authentication authentication) {
        artworkService.toggleLike(artistSlug, id, authenticatedUserResolver.requireEmail(authentication));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/v1/public/{artistSlug}/artworks/{id}/comments")
    @PreAuthorize("hasAnyRole('ARTIST', 'BUYER')")
    @Operation(summary = "Ajouter un commentaire sur une oeuvre spécifique")
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable String artistSlug,
            @PathVariable UUID id,
            Authentication authentication,
            @RequestBody CommentRequest request) {
        return ResponseEntity.ok(artworkService.addComment(
                artistSlug,
                id,
                authenticatedUserResolver.requireEmail(authentication),
                request
        ));
    }

    @GetMapping("/v1/public/{artistSlug}/artworks/{id}/comments")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Lister les commentaires d'une oeuvre spécifique")
    public ResponseEntity<List<CommentResponse>> getComments(
            @PathVariable String artistSlug,
            @PathVariable UUID id) {
        return ResponseEntity.ok(artworkService.getComments(artistSlug, id));
    }

    @PutMapping("/artworks/{id}")
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Modifier une oeuvre (Artiste proprietaire)")
    public ResponseEntity<ArtworkResponse> updateArtwork(
            @PathVariable UUID id,
            Authentication authentication,
            @Valid @RequestBody ArtworkCreateRequest request) {
        return ResponseEntity.ok(artworkService.updateArtwork(
                id,
                authenticatedUserResolver.requireEmail(authentication),
                request
        ));
    }

    @PatchMapping("/artworks/{id}/status")
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Changer l'etat d'une oeuvre (PUBLISHED, ON_SALE, ARCHIVED...)")
    public ResponseEntity<Void> updateStatus(
            @PathVariable UUID id,
            Authentication authentication,
            @RequestParam ArtworkStatus status) {
        artworkService.updateStatus(id, authenticatedUserResolver.requireEmail(authentication), status);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/artworks/bulk-delete")
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Suppression groupée d'oeuvres")
    public ResponseEntity<Void> bulkDelete(@RequestBody List<UUID> ids, Authentication authentication) {
        artworkService.bulkDeleteArtworks(ids, authenticatedUserResolver.requireEmail(authentication));
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/artworks/bulk-status")
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Mise à jour groupée du statut")
    public ResponseEntity<Void> bulkUpdateStatus(
            @RequestBody List<UUID> ids,
            @RequestParam ArtworkStatus status,
            Authentication authentication) {
        artworkService.bulkUpdateStatus(ids, authenticatedUserResolver.requireEmail(authentication), status);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/public/artworks/metadata/styles")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Lister les styles disponibles pour les filtres")
    public ResponseEntity<List<ArtworkStyle>> getStyles() {
        return ResponseEntity.ok(artworkService.getStyles());
    }

    @GetMapping("/public/artworks/metadata/techniques")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Lister les techniques disponibles pour les filtres")
    public ResponseEntity<List<ArtworkTechnique>> getTechniques() {
        return ResponseEntity.ok(artworkService.getTechniques());
    }
}
