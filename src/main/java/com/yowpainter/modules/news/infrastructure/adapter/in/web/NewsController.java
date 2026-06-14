package com.yowpainter.modules.news.infrastructure.adapter.in.web;

import com.yowpainter.modules.news.application.service.NewsService;
import com.yowpainter.modules.news.infrastructure.adapter.in.web.dto.NewsCreateRequest;
import com.yowpainter.modules.news.infrastructure.adapter.in.web.dto.NewsResponse;
import com.yowpainter.shared.security.AuthenticatedUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "News", description = "Actualites des artistes")
public class NewsController {

    private final NewsService newsService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @GetMapping("/v1/public/{artistSlug}/news")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Lister les actualites d'un artiste par slug")
    public ResponseEntity<List<NewsResponse>> getNewsByArtistSlug(@PathVariable String artistSlug) {
        return ResponseEntity.ok(newsService.getNewsByArtistSlug(artistSlug));
    }

    @GetMapping("/news/me")
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Lister mes actualites (Artiste)")
    public ResponseEntity<List<NewsResponse>> getMyNews(Authentication authentication) {
        String email = authenticatedUserResolver.requireEmail(authentication);
        return ResponseEntity.ok(newsService.getMyNews(email));
    }

    @PostMapping("/news")
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Creer une actualite (Artiste)")
    public ResponseEntity<NewsResponse> createNews(
            Authentication authentication,
            @Valid @RequestBody NewsCreateRequest request) {
        String email = authenticatedUserResolver.requireEmail(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(newsService.createNews(email, request));
    }

    @PutMapping("/news/{id}")
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Modifier une actualite (Artiste proprietaire)")
    public ResponseEntity<NewsResponse> updateNews(
            @PathVariable UUID id,
            Authentication authentication,
            @Valid @RequestBody NewsCreateRequest request) {
        String email = authenticatedUserResolver.requireEmail(authentication);
        return ResponseEntity.ok(newsService.updateNews(id, email, request));
    }

    @DeleteMapping("/news/{id}")
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Supprimer une actualite (Artiste proprietaire)")
    public ResponseEntity<Void> deleteNews(@PathVariable UUID id, Authentication authentication) {
        String email = authenticatedUserResolver.requireEmail(authentication);
        newsService.deleteNews(id, email);
        return ResponseEntity.noContent().build();
    }
}
