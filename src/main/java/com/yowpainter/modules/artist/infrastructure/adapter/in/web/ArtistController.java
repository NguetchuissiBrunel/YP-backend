package com.yowpainter.modules.artist.infrastructure.adapter.in.web;

import com.yowpainter.modules.artist.infrastructure.adapter.in.web.dto.ArtistAnalyticsResponse;
import com.yowpainter.modules.artist.infrastructure.adapter.in.web.dto.ArtistResponse;
import com.yowpainter.modules.artist.infrastructure.adapter.in.web.dto.ArtistUpdateRequest;
import com.yowpainter.modules.artist.application.service.ArtistService;
import com.yowpainter.shared.security.AuthenticatedUserResolver;
import com.yowpainter.shared.security.KernelAccessTokenResolver;
import com.yowpainter.modules.artist.domain.model.Artist;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Artists", description = "Endpoints de gestion des profils artistes et recherche")
public class ArtistController {

    private final ArtistService artistService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @GetMapping("/public/artists/{slug}")
    @Operation(summary = "Recuperer le profil public d'un artiste par son slug")
    public ResponseEntity<ArtistResponse> getArtistBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(artistService.getArtistBySlug(slug));
    }

    @GetMapping("/public/artists/search")
    @Operation(summary = "Rechercher des artistes par nom ou slug")
    public ResponseEntity<List<ArtistResponse>> searchArtists(@RequestParam String q) {
        return ResponseEntity.ok(artistService.searchArtists(q));
    }

    @GetMapping("/artist/me")
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Recuperer mon propre profil (Artiste connecte)")
    public ResponseEntity<ArtistResponse> getMyProfile(Authentication authentication) {
        Artist artist = authenticatedUserResolver.requireArtist(authentication);
        String accessToken = KernelAccessTokenResolver.requireAccessToken(authentication);
        return ResponseEntity.ok(artistService.getMyProfileWithSync(artist, accessToken));
    }

    @PutMapping("/artist/me")
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Mettre a jour mon profil artiste")
    public ResponseEntity<ArtistResponse> updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody ArtistUpdateRequest request) {
        return ResponseEntity.ok(artistService.updateArtist(
                authenticatedUserResolver.requireEmail(authentication),
                request
        ));
    }

    @GetMapping("/artist/me/analytics")
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Recuperer les statistiques de mon dashboard")
    public ResponseEntity<ArtistAnalyticsResponse> getMyAnalytics(Authentication authentication) {
        return ResponseEntity.ok(artistService.getArtistAnalytics(
                authenticatedUserResolver.requireEmail(authentication)
        ));
    }

    @GetMapping("/public/artists/id/{id}")
    @Operation(summary = "Recuperer un artiste par son ID")
    public ResponseEntity<ArtistResponse> getArtistById(@PathVariable UUID id) {
        return ResponseEntity.ok(artistService.getArtistById(id));
    }
}
