package com.yowpainter.modules.admin.application.service;

import com.yowpainter.modules.admin.infrastructure.adapter.in.web.dto.ApproveArtistMfaRequest;
import com.yowpainter.modules.admin.infrastructure.adapter.in.web.dto.ApproveArtistMfaResponse;
import com.yowpainter.modules.admin.infrastructure.adapter.in.web.dto.ApproveArtistRequest;
import com.yowpainter.modules.admin.infrastructure.adapter.in.web.dto.ArtistApprovalResponse;
import com.yowpainter.modules.admin.infrastructure.adapter.in.web.dto.PendingArtistResponse;
import com.yowpainter.modules.admin.infrastructure.adapter.in.web.dto.RejectArtistRequest;
import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.artist.domain.model.ArtistRegistrationStatus;
import com.yowpainter.modules.artist.domain.model.PendingProvisionSession;
import com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort;
import com.yowpainter.modules.artist.domain.port.out.PendingProvisionSessionRepositoryPort;
import com.yowpainter.modules.auth.application.service.KernelArtistProvisioningService;
import com.yowpainter.shared.kernel.KernelBootstrapAdminSession;
import com.yowpainter.shared.kernel.KernelMfaRequiredException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArtistApprovalService {

    private static final String STATUS_ACTIVE = ArtistRegistrationStatus.ACTIVE;
    private static final String STATUS_REJECTED = ArtistRegistrationStatus.REJECTED;

    private final ArtistRepositoryPort artistRepository;
    private final KernelArtistProvisioningService kernelArtistProvisioningService;
    private final com.yowpainter.shared.tenant.TenantMigrationService tenantMigrationService;
    private final PendingProvisionSessionRepositoryPort pendingProvisionSessionRepository;
    private final KernelBootstrapAdminSession bootstrapAdminSession;

    public List<PendingArtistResponse> listPendingArtists() {
        return artistRepository.findByStatusIn(ArtistRegistrationStatus.pendingAdminApprovalStatuses()).stream()
                .sorted(Comparator.comparing(Artist::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toPendingResponse)
                .toList();
    }

    @Transactional
    public ApproveArtistMfaResponse approveArtist(UUID artistId, ApproveArtistRequest request) {
        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new IllegalArgumentException("Artiste non trouve"));

        if (!ArtistRegistrationStatus.isPendingAdminApproval(artist.getStatus())) {
            throw new IllegalArgumentException(
                    "Seuls les artistes en attente peuvent etre approuves (statut actuel: " + artist.getStatus() + ")"
            );
        }

        String mfaCode = sanitizeBootstrapMfaCode(request != null ? request.getBootstrapMfaCode() : null);
        UUID actorOverride = request != null ? request.getKernelActorId() : null;

        try {
            KernelArtistProvisioningService.ProvisioningResult provisioned =
                    kernelArtistProvisioningService.provisionOnAdminApproval(artist, mfaCode, actorOverride);

            artist.setOrganizationId(provisioned.organizationId());
            artist.setKernelActorId(provisioned.businessActorId());
            if (provisioned.tenantId() != null) {
                artist.setTenantId(provisioned.tenantId());
            }
            artist.setStatus(STATUS_ACTIVE);
            artistRepository.save(artist);

            // Execute tenant schema migration programmatically
            tenantMigrationService.migrateTenant(provisioned.organizationId());

            log.info("[provision] PROVISION_SUCCESS pour {} (org={}, actor={})", artist.getEmail(), provisioned.organizationId(),
                    provisioned.businessActorId());

            return ApproveArtistMfaResponse.builder()
                    .status(STATUS_ACTIVE)
                    .message("Artiste approuve. Son espace est actif.")
                    .organizationId(provisioned.organizationId())
                    .kernelActorId(provisioned.businessActorId())
                    .build();
        } catch (KernelMfaRequiredException ex) {
            log.info("[provision] MFA_REQUIRED pour {}", artist.getEmail());
            PendingProvisionSession session = PendingProvisionSession.builder()
                    .id(artistId)
                    .mfaToken(ex.getMfaToken())
                    .kernelActorIdOverride(actorOverride)
                    .createdAt(LocalDateTime.now())
                    .build();
            pendingProvisionSessionRepository.save(session);

            return ApproveArtistMfaResponse.builder()
                    .status("MFA_REQUIRED")
                    .message("Veuillez saisir le code reçu par email.")
                    .mfaSessionId(artistId.toString())
                    .build();
        } catch (RuntimeException ex) {
            log.warn("Echec approbation artiste {}: {}", artist.getEmail(), ex.getMessage());
            handleKernelException(ex);
            return null; // unreachable due to exception thrown by handleKernelException
        }
    }

    @Transactional
    public ApproveArtistMfaResponse confirmApproveArtist(UUID artistId, ApproveArtistMfaRequest request) {
        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new IllegalArgumentException("Artiste non trouve"));

        PendingProvisionSession session = pendingProvisionSessionRepository.findById(artistId)
                .orElseThrow(() -> new IllegalArgumentException("Session d'approbation expirée ou introuvable pour cet artiste."));

        log.info("[provision] PROVISION_RESUME pour {} avec le code MFA", artist.getEmail());

        try {
            bootstrapAdminSession.confirmMfaLogin(session.getMfaToken(), request.getMfaCode());

            KernelArtistProvisioningService.ProvisioningResult provisioned =
                    kernelArtistProvisioningService.provisionOnAdminApproval(artist, null, session.getKernelActorIdOverride());

            artist.setOrganizationId(provisioned.organizationId());
            artist.setKernelActorId(provisioned.businessActorId());
            if (provisioned.tenantId() != null) {
                artist.setTenantId(provisioned.tenantId());
            }
            artist.setStatus(STATUS_ACTIVE);
            artistRepository.save(artist);

            // Execute tenant schema migration programmatically
            tenantMigrationService.migrateTenant(provisioned.organizationId());

            // Clean up temporary MFA session
            pendingProvisionSessionRepository.deleteById(artistId);

            log.info("[provision] PROVISION_SUCCESS pour {} (org={}, actor={})", artist.getEmail(), provisioned.organizationId(),
                    provisioned.businessActorId());

            return ApproveArtistMfaResponse.builder()
                    .status(STATUS_ACTIVE)
                    .message("Artiste approuve. Son espace est actif.")
                    .organizationId(provisioned.organizationId())
                    .kernelActorId(provisioned.businessActorId())
                    .build();
        } catch (KernelMfaRequiredException ex) {
            log.info("[provision] MFA_REQUIRED suite à tentative de confirmation");
            session.setMfaToken(ex.getMfaToken());
            pendingProvisionSessionRepository.save(session);

            return ApproveArtistMfaResponse.builder()
                    .status("MFA_REQUIRED")
                    .message("Code MFA incorrect ou expiré. Un nouveau code a été envoyé.")
                    .mfaSessionId(artistId.toString())
                    .build();
        } catch (RuntimeException ex) {
            log.warn("Echec confirmation approbation artiste {}: {}", artist.getEmail(), ex.getMessage());
            handleKernelException(ex);
            return null; // unreachable due to exception thrown by handleKernelException
        }
    }

    private void handleKernelException(RuntimeException ex) {
        if (ex instanceof com.yowpainter.shared.kernel.KernelClientException kex) {
            String errorCode = kex.errorCode();
            if ("AUTH_INVALID_CREDENTIALS".equals(errorCode)) {
                throw new IllegalArgumentException("AUTH_INVALID_CREDENTIALS : Identifiants de connexion invalides.");
            } else if ("AUTH_MFA_REQUIRED".equals(errorCode)) {
                throw new IllegalArgumentException("AUTH_MFA_REQUIRED : MFA requis.");
            } else if ("AUTH_MFA_EXPIRED".equals(errorCode)) {
                throw new IllegalArgumentException("AUTH_MFA_EXPIRED : Code MFA expiré.");
            } else if ("AUTH_MFA_INVALID_CODE".equals(errorCode)) {
                throw new IllegalArgumentException("AUTH_MFA_INVALID_CODE : Code MFA invalide.");
            } else if (messageContains(ex, "invalid mfa")) {
                throw new IllegalArgumentException(
                        "AUTH_MFA_INVALID_CODE : Code MFA bootstrap invalide. "
                                + "Laissez bootstrapMfaCode vide au premier appel, puis utilisez POST .../approve/confirm."
                );
            } else if ("AUTH_TOKEN_EXPIRED".equals(errorCode)) {
                throw new IllegalArgumentException("AUTH_TOKEN_EXPIRED : Jeton d'accès expiré.");
            } else if ("AUTH_TOKEN_INVALID".equals(errorCode)) {
                throw new IllegalArgumentException("AUTH_TOKEN_INVALID : Jeton d'accès invalide.");
            }
            if (kex.statusCode() != null && kex.statusCode().is5xxServerError()) {
                throw new IllegalArgumentException("KERNEL_UNAVAILABLE : Le Kernel est actuellement indisponible (erreur 5xx).");
            }
        }
        if (ex instanceof org.springframework.web.client.ResourceAccessException) {
            throw new IllegalArgumentException("NETWORK_ERROR : Erreur réseau ou Kernel indisponible.");
        }
        throw new IllegalArgumentException(ex.getMessage() != null ? ex.getMessage() : "Echec du provisioning kernel");
    }

    private static String sanitizeBootstrapMfaCode(String mfaCode) {
        if (mfaCode == null || mfaCode.isBlank()) {
            return null;
        }
        String trimmed = mfaCode.trim();
        if ("string".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private static boolean messageContains(RuntimeException ex, String fragment) {
        if (ex.getMessage() == null) {
            return false;
        }
        return ex.getMessage().toLowerCase().contains(fragment);
    }

    @Transactional
    public ArtistApprovalResponse rejectArtist(UUID artistId, RejectArtistRequest request) {
        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new IllegalArgumentException("Artiste non trouve"));

        if (!ArtistRegistrationStatus.isPendingAdminApproval(artist.getStatus())) {
            throw new IllegalArgumentException(
                    "Seuls les artistes en attente peuvent etre refuses (statut actuel: " + artist.getStatus() + ")"
            );
        }

        artist.setStatus(STATUS_REJECTED);
        artistRepository.save(artist);

        if (request != null && request.getReason() != null && !request.getReason().isBlank()) {
            log.info("Artiste {} refuse: {}", artist.getEmail(), request.getReason());
        } else {
            log.info("Artiste {} refuse", artist.getEmail());
        }

        return ArtistApprovalResponse.builder()
                .artistId(artist.getId())
                .email(artist.getEmail())
                .status(STATUS_REJECTED)
                .message("Demande artiste refusee.")
                .build();
    }

    private PendingArtistResponse toPendingResponse(Artist artist) {
        return PendingArtistResponse.builder()
                .id(artist.getId())
                .email(artist.getEmail())
                .firstName(artist.getFirstName())
                .lastName(artist.getLastName())
                .artistName(artist.getArtistName())
                .slug(artist.getSlug())
                .status(ArtistRegistrationStatus.PENDING_APPROVAL)
                .kernelUserId(artist.getKernelUserId())
                .kernelActorId(artist.getKernelActorId())
                .organizationId(artist.getOrganizationId())
                .tenantId(artist.getTenantId())
                .createdAt(artist.getCreatedAt())
                .build();
    }
}
