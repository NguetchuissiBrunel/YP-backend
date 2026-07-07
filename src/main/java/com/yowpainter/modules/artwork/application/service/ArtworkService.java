package com.yowpainter.modules.artwork.application.service;

import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort;
import com.yowpainter.modules.artwork.infrastructure.adapter.in.web.dto.*;
import com.yowpainter.modules.artwork.domain.model.*;
import com.yowpainter.modules.artwork.domain.port.out.ArtworkCommentRepositoryPort;
import com.yowpainter.modules.artwork.domain.port.out.ArtworkLikeRepositoryPort;
import com.yowpainter.modules.artwork.domain.port.out.ArtworkRepositoryPort;
import com.yowpainter.modules.auth.domain.model.AppUser;
import com.yowpainter.modules.auth.domain.port.out.AppUserRepositoryPort;
import com.yowpainter.modules.shop.domain.port.out.ProductRepositoryPort;
import com.yowpainter.modules.notification.application.service.NotificationService;
import com.yowpainter.shared.context.OrganizationContext;
import com.yowpainter.shared.context.RequestContext;
import com.yowpainter.shared.kernel.port.KernelFilePort;
import com.yowpainter.shared.tenant.TenantTransactionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArtworkService {

    private final ArtworkRepositoryPort artworkRepository;
    private final ArtistRepositoryPort artistRepository;
    private final AppUserRepositoryPort userRepository;
    private final ArtworkLikeRepositoryPort likeRepository;
    private final ArtworkCommentRepositoryPort commentRepository;
    private final ProductRepositoryPort productRepository;
    private final NotificationService notificationService;
    private final KernelFilePort kernelFilePort;
    private final TenantTransactionExecutor tenantTransactionExecutor;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.core.env.Environment environment;

    @org.springframework.beans.factory.annotation.Value("${app.artwork.video.max-size:52428800}")
    private long maxVideoSize;

    @org.springframework.beans.factory.annotation.Value("${app.artwork.video.allowed-formats:mp4,mov,webm,ogg}")
    private String allowedVideoFormats;

    @org.springframework.beans.factory.annotation.Value("${app.artwork.video.allowed-mime-types:video/mp4,video/quicktime,video/webm,video/ogg}")
    private String allowedVideoMimeTypes;

    @org.springframework.beans.factory.annotation.Value("${app.artwork.video.max-count:3}")
    private int maxVideoCount;

    private boolean isTestProfile() {
        if (environment == null) {
            return true;
        }
        return java.util.Arrays.asList(environment.getActiveProfiles()).contains("test");
    }

    public ArtworkImageUploadResponse uploadArtworkImage(String artistEmail, MultipartFile file) {
        Artist artist = artistRepository.findByEmail(artistEmail)
                .orElseThrow(() -> new IllegalArgumentException("Artiste introuvable"));
        if (artist.getOrganizationId() == null) {
            throw new IllegalStateException("Organisation kernel manquante pour cet artiste");
        }
        try {
            KernelFilePort.FileView uploaded = kernelFilePort.upload(
                    new KernelFilePort.UploadFileCommand(
                            artist.getOrganizationId(),
                            file.getBytes(),
                            file.getOriginalFilename(),
                            file.getContentType(),
                            "ARTWORK_IMAGE"
                    ),
                    RequestContext.accessToken()
            );
            return ArtworkImageUploadResponse.builder()
                    .fileId(uploaded.id())
                    .imageUrl(com.yowpainter.shared.utils.UrlSanitizer.sanitizeFileUrl(uploaded.downloadUrl()))
                    .build();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Impossible de lire le fichier image", ex);
        }
    }

    public ArtworkVideoUploadResponse uploadArtworkVideo(String artistEmail, MultipartFile file) {
        Artist artist = artistRepository.findByEmail(artistEmail)
                .orElseThrow(() -> new IllegalArgumentException("Artiste introuvable"));
        if (artist.getOrganizationId() == null) {
            throw new IllegalStateException("Organisation kernel manquante pour cet artiste");
        }

        // 1. Validation de la taille
        if (file.getSize() > maxVideoSize) {
            throw new IllegalArgumentException("La taille de la vidéo dépasse la limite autorisée de " + (maxVideoSize / 1024 / 1024) + " Mo");
        }

        // 2. Validation de l'extension
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.contains(".")) {
            throw new IllegalArgumentException("Format de fichier vidéo invalide");
        }
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        List<String> allowedFormatsList = Arrays.asList(allowedVideoFormats.split(","));
        if (!allowedFormatsList.contains(extension)) {
            throw new IllegalArgumentException("Format vidéo non supporté. Formats autorisés : " + allowedVideoFormats);
        }

        // 3. Validation du type MIME
        String contentType = file.getContentType();
        if (contentType == null) {
            throw new IllegalArgumentException("Type MIME vidéo manquant");
        }
        List<String> allowedMimeTypesList = Arrays.asList(allowedVideoMimeTypes.split(","));
        if (!allowedMimeTypesList.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Type MIME non supporté : " + contentType);
        }

        try {
            KernelFilePort.FileView uploaded = kernelFilePort.upload(
                    new KernelFilePort.UploadFileCommand(
                            artist.getOrganizationId(),
                            file.getBytes(),
                            filename,
                            contentType,
                            "ARTWORK_VIDEO"
                    ),
                    RequestContext.accessToken()
            );
            return ArtworkVideoUploadResponse.builder()
                    .fileId(uploaded.id())
                    .videoUrl(com.yowpainter.shared.utils.UrlSanitizer.sanitizeFileUrl(uploaded.downloadUrl()))
                    .build();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Impossible de lire le fichier vidéo", ex);
        }
    }

    @Transactional
    public ArtworkResponse createArtwork(String artistEmail, ArtworkCreateRequest request) {
        Artist artist = artistRepository.findByEmail(artistEmail)
                .orElseThrow(() -> new IllegalArgumentException("Artiste introuvable"));

        Artwork artwork = Artwork.builder()
                .artistId(artist.getId())
                .organizationId(artist.getOrganizationId())
                .title(request.getTitle())
                .description(request.getDescription())
                .technique(request.getTechnique())
                .style(request.getStyle())
                .dimensions(request.getDimensions())
                .tags(request.getTags())
                .status(ArtworkStatus.DRAFT)
                .publishedAt(null)
                .build();

        addImagesToArtwork(artwork, request.getImageUrls());
        addVideosToArtwork(artwork, request.getVideoUrls());
        return mapToResponse(artworkRepository.save(artwork));
    }

    @Transactional(readOnly = true)
    public List<ArtworkResponse> getPublicArtworks() {
        if (isTestProfile() || OrganizationContext.getOrganizationId() != null) {
            return artworkRepository.findPublicArtworks().stream()
                    .map(this::mapToResponse)
                    .sorted(Comparator.comparing(ArtworkResponse::getPublishedAt, Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder())))
                    .collect(Collectors.toList());
        }

        List<Artist> activeArtists = artistRepository.findByStatus("ACTIVE");
        List<ArtworkResponse> allResponses = new ArrayList<>();
        for (Artist artist : activeArtists) {
            if (artist.getOrganizationId() == null) continue;
            try {
                OrganizationContext.setOrganizationId(artist.getOrganizationId());
                List<ArtworkResponse> tenantResponses = tenantTransactionExecutor.execute(() -> 
                    artworkRepository.findPublicArtworks().stream()
                            .map(this::mapToResponse)
                            .collect(Collectors.toList())
                );
                allResponses.addAll(tenantResponses);
            } catch (Exception e) {
                log.error("Failed to fetch public artworks for tenant: {}", artist.getOrganizationId(), e);
            } finally {
                OrganizationContext.clear();
            }
        }
        return allResponses.stream()
                .sorted(Comparator.comparing(ArtworkResponse::getPublishedAt, Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ArtworkResponse> getPublicArtworksByArtistSlug(String slug) {
        Artist artist = artistRepository.findBySlug(slug).orElseThrow(() -> new IllegalArgumentException("Artiste non trouve"));
        if (artist.getOrganizationId() == null) {
            return List.of();
        }
        try {
            OrganizationContext.setOrganizationId(artist.getOrganizationId());
            return tenantTransactionExecutor.execute(() ->
                artworkRepository.findPublicArtworksByArtistId(artist.getId()).stream()
                        .map(this::mapToResponse)
                        .collect(Collectors.toList())
            );
        } finally {
            OrganizationContext.clear();
        }
    }

    @Transactional(readOnly = true)
    public List<ArtworkResponse> getArtworksByArtistId(UUID artistId) {
        return artworkRepository.findByArtistId(artistId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ArtworkResponse> getMyArtworks(String artistEmail) {
        Artist artist = artistRepository.findByEmail(artistEmail).orElseThrow(() -> new IllegalArgumentException("Artiste non trouve"));
        return artworkRepository.findByArtistId(artist.getId()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ArtworkResponse getArtworkById(UUID id) {
        Artwork artwork = artworkRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Oeuvre non trouvee"));
        artwork.setViewCount(artwork.getViewCount() + 1);
        artworkRepository.save(artwork);
        return mapToResponse(artwork);
    }

    @Transactional
    public ArtworkResponse getArtworkByIdAndArtistSlug(UUID id, String artistSlug) {
        Artist artist = artistRepository.findBySlug(artistSlug)
                .orElseThrow(() -> new IllegalArgumentException("Artiste non trouve"));
        if (artist.getOrganizationId() == null) {
            throw new IllegalArgumentException("Organisation de l'artiste manquante");
        }
        try {
            OrganizationContext.setOrganizationId(artist.getOrganizationId());
            return tenantTransactionExecutor.execute(() -> {
                Artwork artwork = artworkRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Oeuvre non trouvee"));
                artwork.setViewCount(artwork.getViewCount() + 1);
                artworkRepository.save(artwork);
                return mapToResponse(artwork);
            });
        } finally {
            OrganizationContext.clear();
        }
    }

    @Transactional(readOnly = true)
    public List<ArtworkResponse> searchArtworks(String query) {
        if (isTestProfile() || OrganizationContext.getOrganizationId() != null) {
            return artworkRepository.searchPublicArtworks(query).stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
        }

        List<Artist> activeArtists = artistRepository.findByStatus("ACTIVE");
        List<ArtworkResponse> allResponses = new ArrayList<>();
        for (Artist artist : activeArtists) {
            if (artist.getOrganizationId() == null) continue;
            try {
                OrganizationContext.setOrganizationId(artist.getOrganizationId());
                List<ArtworkResponse> tenantResponses = tenantTransactionExecutor.execute(() -> 
                    artworkRepository.searchPublicArtworks(query).stream()
                            .map(this::mapToResponse)
                            .collect(Collectors.toList())
                );
                allResponses.addAll(tenantResponses);
            } catch (Exception e) {
                log.error("Failed to search artworks for tenant: {}", artist.getOrganizationId(), e);
            } finally {
                OrganizationContext.clear();
            }
        }
        return allResponses;
    }

    @Transactional(readOnly = true)
    public List<ArtworkResponse> searchArtworksByArtistSlug(String slug, String query) {
        Artist artist = artistRepository.findBySlug(slug).orElseThrow(() -> new IllegalArgumentException("Artiste non trouve"));
        if (artist.getOrganizationId() == null) {
            return List.of();
        }
        try {
            OrganizationContext.setOrganizationId(artist.getOrganizationId());
            return tenantTransactionExecutor.execute(() ->
                artworkRepository.searchPublicArtworksByArtistId(artist.getId(), query).stream()
                        .map(this::mapToResponse)
                        .collect(Collectors.toList())
            );
        } finally {
            OrganizationContext.clear();
        }
    }

    @Transactional(readOnly = true)
    public List<ArtworkResponse> getFeaturedArtworks() {
        if (isTestProfile() || OrganizationContext.getOrganizationId() != null) {
            return artworkRepository.findFeaturedArtworks().stream()
                    .map(this::mapToResponse)
                    .sorted(Comparator.comparing(ArtworkResponse::getLikeCount).reversed())
                    .collect(Collectors.toList());
        }

        List<Artist> activeArtists = artistRepository.findByStatus("ACTIVE");
        List<ArtworkResponse> allResponses = new ArrayList<>();
        for (Artist artist : activeArtists) {
            if (artist.getOrganizationId() == null) continue;
            try {
                OrganizationContext.setOrganizationId(artist.getOrganizationId());
                List<ArtworkResponse> tenantResponses = tenantTransactionExecutor.execute(() -> 
                    artworkRepository.findFeaturedArtworks().stream()
                            .map(this::mapToResponse)
                            .collect(Collectors.toList())
                );
                allResponses.addAll(tenantResponses);
            } catch (Exception e) {
                log.error("Failed to fetch featured artworks for tenant: {}", artist.getOrganizationId(), e);
            } finally {
                OrganizationContext.clear();
            }
        }
        return allResponses.stream()
                .sorted(Comparator.comparing(ArtworkResponse::getLikeCount).reversed())
                .collect(Collectors.toList());
    }

    public void toggleLike(String artistSlug, UUID artworkId, String userEmail) {
        Artist artist = artistRepository.findBySlug(artistSlug)
                .orElseThrow(() -> new IllegalArgumentException("Artiste non trouve"));
        if (artist.getOrganizationId() == null) {
            throw new IllegalStateException("Organisation de l'artiste manquante");
        }
        try {
            OrganizationContext.setOrganizationId(artist.getOrganizationId());
            tenantTransactionExecutor.execute(() -> {
                Artwork artwork = artworkRepository.findById(artworkId)
                        .orElseThrow(() -> new IllegalArgumentException("Oeuvre non trouvee"));
                AppUser user = userRepository.findByEmail(userEmail)
                        .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouve"));

                Optional<ArtworkLike> existingLike = likeRepository.findByArtworkIdAndUserId(artworkId, user.getId());
                if (existingLike.isPresent()) {
                    likeRepository.delete(existingLike.get());
                    artwork.setLikeCount(Math.max(0, artwork.getLikeCount() - 1));
                } else {
                    likeRepository.save(ArtworkLike.builder().artwork(artwork).user(user).build());
                    artwork.setLikeCount(artwork.getLikeCount() + 1);
                    
                    // Notification pour l'artiste
                    notificationService.createNotification(
                        artwork.getArtistId(),
                        user.getFirstName() + " a aimé votre œuvre : " + artwork.getTitle()
                    );
                }
                artworkRepository.save(artwork);
            });
        } finally {
            OrganizationContext.clear();
        }
    }

    public CommentResponse addComment(String artistSlug, UUID artworkId, String userEmail, CommentRequest request) {
        Artist artist = artistRepository.findBySlug(artistSlug)
                .orElseThrow(() -> new IllegalArgumentException("Artiste non trouve"));
        if (artist.getOrganizationId() == null) {
            throw new IllegalStateException("Organisation de l'artiste manquante");
        }
        try {
            OrganizationContext.setOrganizationId(artist.getOrganizationId());
            return tenantTransactionExecutor.execute(() -> {
                Artwork artwork = artworkRepository.findById(artworkId)
                        .orElseThrow(() -> new IllegalArgumentException("Oeuvre non trouvee"));
                AppUser user = userRepository.findByEmail(userEmail)
                        .orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouve"));

                ArtworkComment comment = ArtworkComment.builder()
                        .artwork(artwork)
                        .user(user)
                        .content(request.getContent())
                        .build();

                comment = commentRepository.save(comment);

                // Notification pour l'artiste
                notificationService.createNotification(
                    artwork.getArtistId(),
                    user.getFirstName() + " a commenté votre œuvre : " + artwork.getTitle()
                );

                return mapToCommentResponse(comment);
            });
        } finally {
            OrganizationContext.clear();
        }
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getComments(String artistSlug, UUID artworkId) {
        Artist artist = artistRepository.findBySlug(artistSlug)
                .orElseThrow(() -> new IllegalArgumentException("Artiste non trouve"));
        if (artist.getOrganizationId() == null) {
            return List.of();
        }
        try {
            OrganizationContext.setOrganizationId(artist.getOrganizationId());
            return tenantTransactionExecutor.execute(() -> 
                commentRepository.findByArtworkIdOrderByCreatedAtDesc(artworkId).stream()
                        .map(this::mapToCommentResponse)
                        .collect(Collectors.toList())
            );
        } finally {
            OrganizationContext.clear();
        }
    }

    @Transactional
    public ArtworkResponse updateArtwork(UUID id, String artistEmail, ArtworkCreateRequest request) {
        Artwork artwork = artworkRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Oeuvre non trouvee"));
        
        // Verifier proprietaire
        Artist artist = artistRepository.findByEmail(artistEmail).orElseThrow();
        if (!artwork.getArtistId().equals(artist.getId())) {
            throw new IllegalArgumentException("Acces non autorise");
        }

        artwork.setTitle(request.getTitle());
        artwork.setDescription(request.getDescription());
        artwork.setTechnique(request.getTechnique());
        artwork.setStyle(request.getStyle());
        artwork.setDimensions(request.getDimensions());
        artwork.setTags(request.getTags());

        addImagesToArtwork(artwork, request.getImageUrls());
        addVideosToArtwork(artwork, request.getVideoUrls());

        return mapToResponse(artworkRepository.save(artwork));
    }

    @Transactional
    public void updateStatus(UUID id, String artistEmail, ArtworkStatus status) {
        Artwork artwork = artworkRepository.findById(id).orElseThrow();
        Artist artist = artistRepository.findByEmail(artistEmail).orElseThrow();
        if (!artwork.getArtistId().equals(artist.getId())) throw new IllegalArgumentException("Non autorise");
        
        artwork.setStatus(status);
        if (status == ArtworkStatus.PUBLISHED && artwork.getPublishedAt() == null) {
            artwork.setPublishedAt(LocalDateTime.now());
        }

        // Synchroniser avec le produit boutique si nécessaire
        productRepository.findByArtworkId(id).ifPresent(product -> {
            if (status == ArtworkStatus.SUSPENDED) {
                product.setActive(false);
            } else if (status == ArtworkStatus.ON_SALE) {
                product.setActive(true);
            }
            productRepository.save(product);
        });

        artworkRepository.save(artwork);
    }

    @Transactional
    public void bulkDeleteArtworks(List<UUID> ids, String artistEmail) {
        Artist artist = artistRepository.findByEmail(artistEmail).orElseThrow();
        List<Artwork> artworks = artworkRepository.findAllById(ids);
        for (Artwork artwork : artworks) {
            if (!artwork.getArtistId().equals(artist.getId())) {
                throw new IllegalArgumentException("Acces non autorise pour l'oeuvre: " + artwork.getTitle());
            }
        }
        artworkRepository.deleteAll(artworks);
    }

    @Transactional
    public void bulkUpdateStatus(List<UUID> ids, String artistEmail, ArtworkStatus status) {
        Artist artist = artistRepository.findByEmail(artistEmail).orElseThrow();
        List<Artwork> artworks = artworkRepository.findAllById(ids);
        for (Artwork artwork : artworks) {
            if (!artwork.getArtistId().equals(artist.getId())) {
                throw new IllegalArgumentException("Acces non autorise pour l'oeuvre: " + artwork.getTitle());
            }
            artwork.setStatus(status);
            if (status == ArtworkStatus.PUBLISHED && artwork.getPublishedAt() == null) {
                artwork.setPublishedAt(LocalDateTime.now());
            }

            productRepository.findByArtworkId(artwork.getId()).ifPresent(product -> {
                if (status == ArtworkStatus.SUSPENDED) {
                    product.setActive(false);
                } else if (status == ArtworkStatus.ON_SALE) {
                    product.setActive(true);
                }
                productRepository.save(product);
            });
        }
        artworkRepository.saveAll(artworks);
    }

    @Transactional(readOnly = true)
    public List<ArtworkStyle> getStyles() {
        if (isTestProfile() || OrganizationContext.getOrganizationId() != null) {
            return artworkRepository.findDistinctStyles();
        }

        List<Artist> activeArtists = artistRepository.findByStatus("ACTIVE");
        Set<ArtworkStyle> styles = new HashSet<>();
        for (Artist artist : activeArtists) {
            if (artist.getOrganizationId() == null) continue;
            try {
                OrganizationContext.setOrganizationId(artist.getOrganizationId());
                List<ArtworkStyle> tenantStyles = tenantTransactionExecutor.execute(() -> 
                    artworkRepository.findDistinctStyles()
                );
                styles.addAll(tenantStyles);
            } catch (Exception e) {
                log.error("Failed to get styles for tenant: {}", artist.getOrganizationId(), e);
            } finally {
                OrganizationContext.clear();
            }
        }
        return new ArrayList<>(styles);
    }

    @Transactional(readOnly = true)
    public List<ArtworkTechnique> getTechniques() {
        if (isTestProfile() || OrganizationContext.getOrganizationId() != null) {
            return artworkRepository.findDistinctTechniques();
        }

        List<Artist> activeArtists = artistRepository.findByStatus("ACTIVE");
        Set<ArtworkTechnique> techniques = new HashSet<>();
        for (Artist artist : activeArtists) {
            if (artist.getOrganizationId() == null) continue;
            try {
                OrganizationContext.setOrganizationId(artist.getOrganizationId());
                List<ArtworkTechnique> tenantTechniques = tenantTransactionExecutor.execute(() -> 
                    artworkRepository.findDistinctTechniques()
                );
                techniques.addAll(tenantTechniques);
            } catch (Exception e) {
                log.error("Failed to get techniques for tenant: {}", artist.getOrganizationId(), e);
            } finally {
                OrganizationContext.clear();
            }
        }
        return new ArrayList<>(techniques);
    }

    @Transactional(readOnly = true)
    public List<String> getSuggestions(String q) { 
        List<String> tags;
        if (isTestProfile() || OrganizationContext.getOrganizationId() != null) {
            tags = artworkRepository.findDistinctTags();
        } else {
            List<Artist> activeArtists = artistRepository.findByStatus("ACTIVE");
            Set<String> tagsSet = new HashSet<>();
            for (Artist artist : activeArtists) {
                if (artist.getOrganizationId() == null) continue;
                try {
                    OrganizationContext.setOrganizationId(artist.getOrganizationId());
                    List<String> tenantTags = tenantTransactionExecutor.execute(() -> 
                        artworkRepository.findDistinctTags()
                    );
                    tagsSet.addAll(tenantTags);
                } catch (Exception e) {
                    log.error("Failed to get suggestions for tenant: {}", artist.getOrganizationId(), e);
                } finally {
                    OrganizationContext.clear();
                }
            }
            tags = new ArrayList<>(tagsSet);
        }
        return tags.stream()
                .filter(t -> t.toLowerCase().contains(q.toLowerCase()))
                .collect(Collectors.toList());
    }

    private void addImagesToArtwork(Artwork artwork, List<String> imageUrls) {
        if (imageUrls == null) return;
        artwork.getImages().clear();
        for (int i = 0; i < imageUrls.size(); i++) {
            artwork.addImage(ArtworkImage.builder()
                    .imageUrl(imageUrls.get(i))
                    .isPrimary(i == 0)
                    .sortOrder(i)
                    .build());
        }
    }

    private void addVideosToArtwork(Artwork artwork, List<String> videoUrls) {
        if (videoUrls == null) return;
        if (videoUrls.size() > maxVideoCount) {
            throw new IllegalArgumentException("Le nombre maximal de vidéos par œuvre est de " + maxVideoCount);
        }
        artwork.getVideos().clear();
        for (int i = 0; i < videoUrls.size(); i++) {
            artwork.addVideo(ArtworkVideo.builder()
                    .videoUrl(videoUrls.get(i))
                    .sortOrder(i)
                    .build());
        }
    }

    private ArtworkResponse mapToResponse(Artwork artwork) {
        String artistName = artistRepository.findById(artwork.getArtistId())
                .map(Artist::getArtistName)
                .orElse("Artiste Inconnu");
        
        return ArtworkResponse.builder()
                .id(artwork.getId())
                .artistId(artwork.getArtistId())
                .artistName(artistName)
                .title(artwork.getTitle())
                .description(artwork.getDescription())
                .technique(artwork.getTechnique())
                .style(artwork.getStyle())
                .dimensions(artwork.getDimensions())
                .tags(artwork.getTags())
                .status(artwork.getStatus())
                .viewCount(artwork.getViewCount())
                .likeCount(artwork.getLikeCount())
                .imageUrls(artwork.getImages().stream()
                        .map(img -> com.yowpainter.shared.utils.UrlSanitizer.sanitizeFileUrl(img.getImageUrl()))
                        .collect(Collectors.toList()))
                .videoUrls(artwork.getVideos().stream()
                        .map(vid -> com.yowpainter.shared.utils.UrlSanitizer.sanitizeFileUrl(vid.getVideoUrl()))
                        .collect(Collectors.toList()))
                .publishedAt(artwork.getPublishedAt())
                .createdAt(artwork.getCreatedAt())
                .build();
    }

    private CommentResponse mapToCommentResponse(ArtworkComment comment) {
        CommentResponse res = new CommentResponse();
        res.setId(comment.getId());
        res.setUserName(comment.getUser().getFirstName() + " " + comment.getUser().getLastName());
        res.setUserAvatar(com.yowpainter.shared.utils.UrlSanitizer.sanitizeFileUrl(comment.getUser().getProfilePictureUrl()));
        res.setContent(comment.getContent());
        res.setCreatedAt(comment.getCreatedAt());
        return res;
    }
}
