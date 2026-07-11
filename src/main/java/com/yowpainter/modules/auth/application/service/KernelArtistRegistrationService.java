package com.yowpainter.modules.auth.application.service;

import com.yowpainter.config.KernelProperties;
import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort;
import com.yowpainter.modules.auth.application.port.out.KernelAuthPort;
import com.yowpainter.modules.auth.domain.model.UserRole;
import com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto.AuthResponse;
import com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto.RegisterRequest;
import com.yowpainter.shared.kernel.KernelClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KernelArtistRegistrationService {

    private static final String KERNEL_MANAGED_PASSWORD = "{KERNEL_MANAGED}";
    private static final String STATUS_PENDING_EMAIL = "PENDING_EMAIL";
    private static final String STATUS_PENDING_APPROVAL = "ORGANIZATION_VALIDATION_REQUIRED";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final KernelAuthPort kernelAuthPort;
    private final KernelArtistProvisioningService kernelArtistProvisioningService;
    private final ArtistRepositoryPort artistRepository;
    private final PasswordEncoder passwordEncoder;
    private final KernelProperties kernelProperties;

    @Transactional
    public AuthResponse registerArtist(RegisterRequest request) {
        if (artistRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Un utilisateur avec cet email existe deja");
        }

        String slug = resolveSlug(request);
        String artistName = resolveArtistName(request);
        String platformOrganizationCode = resolvePlatformOrganizationCode();

        try {
            KernelAuthPort.DiscoverSignUpContextsResult discovery =
                    kernelAuthPort.discoverSignUpContexts(platformOrganizationCode);

            Map<String, Object> onboardingData = new LinkedHashMap<>();
            onboardingData.put("platform", "yowpainter");
            onboardingData.put("slug", slug);
            onboardingData.put("organizationCode", slug);
            onboardingData.put("artistName", artistName);

            KernelAuthPort.KernelLoginResult signup = signUpWithDiscovery(
                    discovery,
                    platformOrganizationCode,
                    request,
                    "BUSINESS",
                    "ART",
                    onboardingData
            );

            if (Boolean.FALSE.equals(signup.emailVerified()) && signup.accessToken() != null) {
                // L'utilisateur kernel est deja cree : un echec de (re)demande de verification
                // email (ex. OTP_RESEND_COOLDOWN) ne doit PAS faire echouer l'inscription.
                try {
                    kernelAuthPort.requestEmailVerification(signup.accessToken());
                } catch (KernelClientException ex) {
                    log.warn("requestEmailVerification apres signup ignoree (user kernel deja cree): {}", ex.getMessage());
                }
            }

            UUID organizationId = resolveOrganizationId(signup, discovery);
            UUID tenantId = signup.tenantId() != null
                    ? signup.tenantId()
                    : firstContextTenantId(discovery);

            Artist artist = Artist.builder()
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .email(request.getEmail())
                    .passwordHash(passwordEncoder.encode(KERNEL_MANAGED_PASSWORD))
                    .role(UserRole.ROLE_ARTIST)
                    .artistName(artistName)
                    .slug(slug)
                    .status(resolveInitialStatus(signup))
                    .kernelUserId(signup.userId())
                    .kernelActorId(signup.actorId())
                    .organizationId(organizationId)
                    .tenantId(tenantId)
                    .profilePictureUrl(request.getImageUrl())
                    .build();

            artistRepository.save(artist);

            if (Boolean.TRUE.equals(signup.emailVerified())) {
                if (kernelProperties.autoProvisionArtists()) {
                    tryProvisionArtist(artist, signup);
                } else {
                    artist.setStatus(STATUS_PENDING_APPROVAL);
                }
                artistRepository.save(artist);
            }

            AuthResponse response = KernelAuthMapper.toAuthResponse(signup, artist);
            response.setMessage(buildRegistrationMessage(signup, artist));
            if (Boolean.FALSE.equals(signup.emailVerified())) {
                response.setAccessToken(null);
                response.setRefreshToken(null);
            }
            return response;
        } catch (KernelClientException ex) {
            throw new IllegalArgumentException(resolveKernelErrorMessage(ex));
        }
    }

    void applyEmailConfirmedArtist(Artist artist, KernelAuthPort.KernelLoginResult confirmed) {
        linkArtistFromKernel(artist, confirmed);
        if (kernelProperties.autoProvisionArtists()
                && confirmed.accessToken() != null
                && !confirmed.accessToken().isBlank()) {
            tryProvisionArtist(artist, confirmed);
        } else {
            artist.setStatus(STATUS_PENDING_APPROVAL);
        }
        artistRepository.save(artist);
    }

    void provisionArtistIfPending(Artist artist, KernelAuthPort.KernelLoginResult loginResult) {
        if (!kernelProperties.autoProvisionArtists()) {
            return;
        }
        if (STATUS_ACTIVE.equalsIgnoreCase(artist.getStatus())) {
            return;
        }
        if (loginResult.accessToken() == null || loginResult.accessToken().isBlank()) {
            return;
        }
        if (!Boolean.TRUE.equals(loginResult.emailVerified())
                && !STATUS_PENDING_APPROVAL.equalsIgnoreCase(artist.getStatus())) {
            return;
        }
        tryProvisionArtist(artist, loginResult);
        artistRepository.save(artist);
    }

    boolean isArtistActive(Artist artist) {
        return STATUS_ACTIVE.equalsIgnoreCase(artist.getStatus());
    }

    private boolean tryProvisionArtist(Artist artist, KernelAuthPort.KernelLoginResult confirmed) {
        try {
            KernelArtistProvisioningService.ProvisioningResult provisioned =
                    kernelArtistProvisioningService.provisionAfterOnboarding(artist, confirmed);
            artist.setOrganizationId(provisioned.organizationId());
            if (provisioned.tenantId() != null) {
                artist.setTenantId(provisioned.tenantId());
            }
            artist.setStatus(STATUS_ACTIVE);
            return true;
        } catch (RuntimeException ex) {
            log.warn(
                    "Provision artiste {} echouee ({}): {}",
                    artist.getEmail(),
                    ex.getClass().getSimpleName(),
                    ex.getMessage()
            );
            artist.setStatus(STATUS_PENDING_APPROVAL);
            return false;
        }
    }

    private KernelAuthPort.KernelLoginResult signUpWithDiscovery(
            KernelAuthPort.DiscoverSignUpContextsResult discovery,
            String platformOrganizationCode,
            RegisterRequest request,
            String accountType,
            String businessType,
            Map<String, Object> onboardingData
    ) {
        if (discovery.contexts() != null && !discovery.contexts().isEmpty()) {
            KernelAuthPort.SignUpContext context = resolveSignUpContext(discovery, platformOrganizationCode);
            return kernelAuthPort.signUpWithContext(
                    new KernelAuthPort.ContextualSignUpCommand(
                            discovery.selectionToken(),
                            context.contextId(),
                            request.getFirstName(),
                            request.getLastName(),
                            request.getEmail(),
                            request.getPassword(),
                            accountType,
                            businessType,
                            onboardingData
                    )
            );
        }

        return kernelAuthPort.signUpWithContext(
                new KernelAuthPort.ContextualSignUpCommand(
                        discovery.selectionToken(),
                        null,
                        request.getFirstName(),
                        request.getLastName(),
                        request.getEmail(),
                        request.getPassword(),
                        accountType,
                        businessType,
                        onboardingData
                )
        );
    }

    private UUID firstContextTenantId(KernelAuthPort.DiscoverSignUpContextsResult discovery) {
        if (discovery.contexts() == null || discovery.contexts().isEmpty()) {
            return null;
        }
        return discovery.contexts().get(0).tenantId();
    }

    private KernelAuthPort.SignUpContext resolveSignUpContext(
            KernelAuthPort.DiscoverSignUpContextsResult discovery,
            String organizationCode
    ) {
        return discovery.contexts().stream()
                .filter(ctx -> organizationCode.equalsIgnoreCase(ctx.organizationCode()))
                .findFirst()
                .orElse(discovery.contexts().get(0));
    }

    private UUID resolveOrganizationId(
            KernelAuthPort.KernelLoginResult signup,
            KernelAuthPort.DiscoverSignUpContextsResult discovery
    ) {
        if (signup.organizations() != null && signup.organizations().size() == 1) {
            return signup.organizations().get(0).organizationId();
        }
        if (discovery.contexts() != null && !discovery.contexts().isEmpty()) {
            return discovery.contexts().get(0).organizationId();
        }
        return null;
    }

    private String resolveInitialStatus(KernelAuthPort.KernelLoginResult signup) {
        if (Boolean.TRUE.equals(signup.emailVerified())) {
            return STATUS_PENDING_APPROVAL;
        }
        return STATUS_PENDING_EMAIL;
    }

    private String buildRegistrationMessage(KernelAuthPort.KernelLoginResult signup, Artist artist) {
        if (Boolean.TRUE.equals(signup.emailVerified())) {
            if (STATUS_ACTIVE.equalsIgnoreCase(artist.getStatus())) {
                return "Inscription enregistree. Votre espace artiste est pret.";
            }
            if (!kernelProperties.autoProvisionArtists()) {
                return "Inscription enregistree. Votre demande sera examinee par notre equipe.";
            }
            return "Inscription enregistree. La validation automatique est en cours.";
        }
        if (!kernelProperties.autoProvisionArtists()) {
            return "Inscription enregistree. Verifiez votre e-mail, puis notre equipe validera votre demande.";
        }
        return "Inscription enregistree. Un e-mail de verification vous a ete envoye pour activer votre compte.";
    }

    private void linkArtistFromKernel(Artist artist, KernelAuthPort.KernelLoginResult loginResult) {
        if (loginResult.userId() != null) {
            artist.setKernelUserId(loginResult.userId());
        }
        if (loginResult.tenantId() != null) {
            artist.setTenantId(loginResult.tenantId());
        }
        if (loginResult.organizations() != null && loginResult.organizations().size() == 1) {
            artist.setOrganizationId(loginResult.organizations().get(0).organizationId());
        }
        if (loginResult.actorId() != null) {
            artist.setKernelActorId(loginResult.actorId());
        }
    }

    private String resolvePlatformOrganizationCode() {
        String code = kernelProperties.signupPlatformOrganizationCode();
        if (code == null || code.isBlank()) {
            return kernelProperties.clientId();
        }
        return code;
    }

    private String resolveArtistName(RegisterRequest request) {
        if (request.getArtistName() != null && !request.getArtistName().isBlank()) {
            return request.getArtistName();
        }
        return request.getFirstName() + " " + request.getLastName();
    }

    private String resolveSlug(RegisterRequest request) {
        String slug = request.getSlug();
        if (slug == null || slug.isBlank()) {
            slug = generateSlug(resolveArtistName(request));
        } else {
            slug = generateSlug(slug);
        }
        if (artistRepository.findBySlug(slug).isPresent()) {
            slug = slug + "-" + UUID.randomUUID().toString().substring(0, 5);
        }
        return slug;
    }

    private String resolveKernelErrorMessage(KernelClientException ex) {
        if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
            return ex.getMessage();
        }
        if (ex.statusCode() != null && ex.statusCode().value() == 401) {
            return "Configuration kernel invalide (client-id / api-key). Verifiez .env.local";
        }
        return "Echec inscription via le kernel";
    }

    private String generateSlug(String input) {
        if (input == null || input.isBlank()) {
            return "artist";
        }
        return input.toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
