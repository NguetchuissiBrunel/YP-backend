package com.yowpainter.modules.artist.application.service;

import com.yowpainter.modules.artist.infrastructure.adapter.in.web.dto.ArtistAnalyticsResponse;
import com.yowpainter.modules.artist.infrastructure.adapter.in.web.dto.ArtistResponse;
import com.yowpainter.modules.artist.infrastructure.adapter.in.web.dto.ArtistUpdateRequest;
import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.yowpainter.modules.auth.application.port.out.KernelAuthPort;
import com.yowpainter.shared.tenant.TenantMigrationService;
import com.yowpainter.shared.kernel.KernelStatusResolver;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArtistService {

    private final ArtistRepositoryPort artistRepository;
    private final com.yowpainter.modules.artwork.domain.port.out.ArtworkRepositoryPort artworkRepository;
    private final com.yowpainter.modules.artwork.domain.port.out.ArtworkLikeRepositoryPort artworkLikeRepository;
    private final com.yowpainter.modules.shop.domain.port.out.OrderRepositoryPort orderRepository;
    private final com.yowpainter.modules.event.domain.port.out.EventRepositoryPort eventRepository;
    private final KernelAuthPort kernelAuthPort;
    private final TenantMigrationService tenantMigrationService;

    public ArtistResponse getArtistBySlug(String slug) {
        Artist artist = artistRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Artiste non trouve avec le slug: " + slug));
        return mapToResponse(artist);
    }

    public ArtistResponse getArtistById(UUID id) {
        Artist artist = artistRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Artiste non trouve avec l'ID: " + id));
        return mapToResponse(artist);
    }

    public List<ArtistResponse> searchArtists(String query) {
        // Simple search logic for MVP
        return artistRepository.findAll().stream()
                .filter(a -> a.getArtistName().toLowerCase().contains(query.toLowerCase()) || 
                             a.getSlug().toLowerCase().contains(query.toLowerCase()))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public ArtistResponse getArtistByEmail(String email) {
        Artist artist = artistRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Artiste non trouve avec l'email: " + email));
        return mapToResponse(artist);
    }

    public ArtistResponse toResponse(Artist artist) {
        return mapToResponse(artist);
    }

    @Transactional
    public ArtistResponse getMyProfileWithSync(Artist artist, String accessToken) {
        if (!"ACTIVE".equalsIgnoreCase(artist.getStatus())) {
            try {
                KernelAuthPort.KernelUserProfile profile = kernelAuthPort.me(accessToken);
                
                String oldStatus = artist.getStatus();
                String newStatus = KernelStatusResolver.determineStatusFromKernel(
                        profile.emailVerified(),
                        profile.registrationStatus(),
                        profile.accountStatus(),
                        profile.organizations(),
                        profile.actorId()
                );
                
                artist.setStatus(newStatus);
                
                if (profile.organizations() != null && !profile.organizations().isEmpty()) {
                    artist.setOrganizationId(profile.organizations().get(0).organizationId());
                }
                if (profile.actorId() != null) {
                    artist.setKernelActorId(profile.actorId());
                }
                if (profile.tenantId() != null) {
                    artist.setTenantId(profile.tenantId());
                }
                
                if ("ACTIVE".equalsIgnoreCase(newStatus) && !"ACTIVE".equalsIgnoreCase(oldStatus)) {
                    UUID orgId = artist.getOrganizationId();
                    if (orgId != null) {
                        tenantMigrationService.migrateTenant(orgId);
                    }
                }
                
                artist = artistRepository.save(artist);
            } catch (Exception ex) {
                log.warn("Failed to dynamically sync artist profile from Kernel: {}", ex.getMessage());
            }
        }
        return mapToResponse(artist);
    }

    @Transactional
    public ArtistResponse updateArtist(String email, ArtistUpdateRequest request) {
        Artist artist = artistRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Artiste non trouve"));

        artist.setFirstName(request.getFirstName());
        artist.setLastName(request.getLastName());
        artist.setArtistName(request.getArtistName());
        artist.setBio(request.getBio());
        artist.setBannerUrl(request.getBannerUrl());
        artist.setLocation(request.getLocation());

        return mapToResponse(artistRepository.save(artist));
    }

    public ArtistAnalyticsResponse getArtistAnalytics(String email) {
        long totalArtworks = artworkRepository.count();
        long publishedArtworks = artworkRepository.countByStatus(com.yowpainter.modules.artwork.domain.model.ArtworkStatus.PUBLISHED);
        long totalLikes = artworkLikeRepository.count();
        long totalSales = orderRepository.countByStatus(com.yowpainter.modules.shop.domain.model.OrderStatus.PAID);
        
        java.math.BigDecimal revenue = orderRepository.sumTotalAmountByStatus(com.yowpainter.modules.shop.domain.model.OrderStatus.PAID);
        double totalRevenue = revenue != null ? revenue.doubleValue() : 0.0;
        
        long upcomingEvents = eventRepository.countByStatusAndStartDateTimeAfter(
                com.yowpainter.modules.event.domain.model.EventStatus.PUBLISHED, 
                java.time.LocalDateTime.now()
        );

        return ArtistAnalyticsResponse.builder()
                .totalArtworks(totalArtworks)
                .publishedArtworks(publishedArtworks)
                .totalLikes(totalLikes)
                .totalSales(totalSales)
                .totalRevenue(totalRevenue)
                .upcomingEvents(upcomingEvents)
                .build();
    }

    private ArtistResponse mapToResponse(Artist artist) {
        return ArtistResponse.builder()
                .id(artist.getId())
                .firstName(artist.getFirstName())
                .lastName(artist.getLastName())
                .email(artist.getEmail())
                .artistName(artist.getArtistName())
                .slug(artist.getSlug())
                .bio(artist.getBio())
                .profilePictureUrl(com.yowpainter.shared.utils.UrlSanitizer.sanitizeFileUrl(artist.getProfilePictureUrl()))
                .bannerUrl(com.yowpainter.shared.utils.UrlSanitizer.sanitizeFileUrl(artist.getBannerUrl()))
                .location(artist.getLocation())
                .status(artist.getStatus())
                .build();
    }
}
