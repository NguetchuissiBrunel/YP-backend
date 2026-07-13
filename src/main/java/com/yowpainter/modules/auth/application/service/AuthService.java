package com.yowpainter.modules.auth.application.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.auth.application.port.out.KernelAuthPort;
import com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto.AdminRegisterRequest;
import com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort;
import com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto.AuthResponse;
import com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto.LoginRequest;
import com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto.RegisterRequest;
import com.yowpainter.modules.auth.domain.model.AppUser;
import com.yowpainter.modules.auth.domain.model.UserRole;
import com.yowpainter.modules.auth.domain.port.out.AppUserRepositoryPort;
import com.yowpainter.shared.kernel.KernelClientException;
import com.yowpainter.shared.kernel.KernelBootstrapAdminSession;
import com.yowpainter.shared.kernel.port.KernelFilePort;
import com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto.ProfileImageUploadResponse;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.yowpainter.shared.tenant.TenantMigrationService;
import com.yowpainter.shared.kernel.KernelStatusResolver;
import com.yowpainter.shared.kernel.KernelOrganizationResolver;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String KERNEL_MANAGED_PASSWORD = "{KERNEL_MANAGED}";

    private final AppUserRepositoryPort userRepository;
    private final ArtistRepositoryPort artistRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;
    private final KernelAuthPort kernelAuthPort;
    private final KernelArtistRegistrationService kernelArtistRegistrationService;
    private final KernelBuyerRegistrationService kernelBuyerRegistrationService;
    private final KernelAdminRegistrationService kernelAdminRegistrationService;
    private final KernelBootstrapAdminSession kernelBootstrapAdminSession;
    private final KernelFilePort kernelFilePort;
    private final TenantMigrationService tenantMigrationService;

    public List<String> getAvailableRoles() {
        return List.of(UserRole.ROLE_ARTIST.name(), UserRole.ROLE_BUYER.name());
    }

    public void processForgotPassword(String email) {
        KernelAuthPort.ForgotPasswordResult forgot = kernelAuthPort.forgotPassword(email);
        if (forgot.matchingAccountCount() <= 0 || forgot.contexts().isEmpty()) {
            return;
        }

        KernelAuthPort.PasswordResetContext context = forgot.contexts().get(0);
        KernelAuthPort.IssuedPasswordResetResult issued = kernelAuthPort.issuePasswordReset(
                forgot.selectionToken(),
                context.contextId()
        );

        if ("PREVIEW_ONLY".equalsIgnoreCase(issued.deliveryMode())
                && issued.challengeTokenPreview() != null
                && !issued.challengeTokenPreview().isBlank()) {
            emailService.sendPasswordResetEmail(email, issued.challengeTokenPreview());
        }
    }

    public void resetPassword(String token, String newPassword) {
        kernelAuthPort.resetPassword(token, newPassword);
    }

    @Transactional
    public AuthResponse registerAdmin(AdminRegisterRequest request) {
        return kernelAdminRegistrationService.registerAdmin(request);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.getRole() == UserRole.ROLE_ADMIN) {
            throw new IllegalArgumentException("Le role ADMIN ne peut pas etre choisi publiquement");
        }

        if (request.getRole() == UserRole.ROLE_ARTIST) {
            return kernelArtistRegistrationService.registerArtist(request);
        }

        return kernelBuyerRegistrationService.registerBuyer(request);
    }

    @Transactional
    public AuthResponse confirmEmail(String verificationToken) {
        if (verificationToken == null || verificationToken.isBlank()) {
            throw new IllegalArgumentException("Le token de verification est requis");
        }

        try {
            KernelAuthPort.KernelLoginResult confirmed = kernelAuthPort.confirmEmailVerification(verificationToken);

            Optional<Artist> artist = artistRepository.findByEmail(confirmed.email());
            if (artist.isPresent()) {
                kernelArtistRegistrationService.applyEmailConfirmedArtist(artist.get(), confirmed);
                Artist updatedArtist = artistRepository.findByEmail(confirmed.email()).orElse(artist.get());
                AuthResponse response = KernelAuthMapper.toAuthResponse(confirmed, updatedArtist);
                if (kernelArtistRegistrationService.isArtistActive(updatedArtist)) {
                    response.setMessage("E-mail verifie. Votre espace artiste est actif, vous pouvez vous connecter.");
                } else {
                    response.setMessage(
                            "E-mail verifie. Votre demande est en attente de validation par notre equipe."
                    );
                }
                return response;
            }

            Optional<AppUser> buyer = userRepository.findByEmail(confirmed.email())
                    .filter(user -> user.getRole() == UserRole.ROLE_BUYER);
            if (buyer.isPresent()) {
                kernelBuyerRegistrationService.applyEmailConfirmedBuyer(buyer.get(), confirmed);
                AuthResponse response = KernelAuthMapper.toAuthResponse(confirmed, buyer.get());
                response.setMessage("E-mail verifie. Vous pouvez vous connecter.");
                return response;
            }

            throw new IllegalArgumentException("Profil local introuvable pour " + confirmed.email());
        } catch (KernelClientException ex) {
            throw new IllegalArgumentException(
                    ex.getMessage() != null ? ex.getMessage() : "Echec de la verification e-mail"
            );
        }
    }

    @Transactional
    public AuthResponse loginAdmin(LoginRequest request) {
        return kernelAdminRegistrationService.loginAdmin(request);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            KernelAuthPort.KernelLoginResult loginResult = kernelAuthPort.login(
                    request.getEmail(),
                    request.getPassword()
            );
            AppUser user = resolveLocalUserAfterLogin(loginResult);
            return KernelAuthMapper.toAuthResponse(loginResult, user);
        } catch (KernelClientException ex) {
            throw new IllegalArgumentException(
                    ex.getMessage() != null ? ex.getMessage() : "Email ou mot de passe invalide"
            );
        }
    }

    @Transactional
    public AuthResponse refreshToken(String requestToken) {
        KernelAuthPort.KernelLoginResult refreshed = kernelAuthPort.refresh(requestToken);
        AppUser user = syncLocalUserLink(refreshed);
        return KernelAuthMapper.toAuthResponse(refreshed, user);
    }

    @Transactional
    public void logout(AppUser user) {
        refreshTokenService.deleteByUserId(user.getId());
    }

    public void logoutWithRefreshToken(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            kernelAuthPort.logout(refreshToken);
        }
    }

    private AppUser resolveLocalUserAfterLogin(KernelAuthPort.KernelLoginResult loginResult) {
        try {
            AppUser user = syncLocalUserLink(loginResult);
            if (user != null) {
                return user;
            }
            return createLocalProfileFromKernel(loginResult);
        } catch (RuntimeException ex) {
            log.warn(
                    "Synchronisation profil local ignoree pour {} ({}): {}",
                    loginResult.email(),
                    ex.getClass().getSimpleName(),
                    ex.getMessage()
            );
            return null;
        }
    }

    private AppUser createLocalProfileFromKernel(KernelAuthPort.KernelLoginResult loginResult) {
        if (loginResult.email() == null || loginResult.email().isBlank()) {
            return null;
        }
        if (isArtistKernelAccount(loginResult)) {
            return createArtistProfileFromKernel(loginResult);
        }
        return createBuyerProfileFromKernel(loginResult);
    }

    private Artist createArtistProfileFromKernel(KernelAuthPort.KernelLoginResult loginResult) {
        String artistName = resolveDisplayName(loginResult);
        String slug = generateUniqueArtistSlug(artistName, loginResult.email());
        UUID organizationId = loginResult.organizations() != null && !loginResult.organizations().isEmpty()
                ? loginResult.organizations().get(0).organizationId()
                : null;

        Artist artist = Artist.builder()
                .firstName(loginResult.firstName())
                .lastName(loginResult.lastName())
                .email(loginResult.email())
                .passwordHash(passwordEncoder.encode(KERNEL_MANAGED_PASSWORD))
                .role(UserRole.ROLE_ARTIST)
                .artistName(artistName)
                .slug(slug)
                .status(resolveArtistStatusFromKernel(loginResult, organizationId))
                .kernelUserId(loginResult.userId())
                .kernelActorId(loginResult.actorId())
                .organizationId(organizationId)
                .tenantId(loginResult.tenantId())
                .profilePictureUrl(loginResult.profilePictureUrl())
                .build();
        artist = artistRepository.save(artist);
        log.info(
                "Profil artiste local recree pour {} (kernelUserId={}, status={})",
                artist.getEmail(),
                artist.getKernelUserId(),
                artist.getStatus()
        );
        return artist;
    }

    private AppUser createBuyerProfileFromKernel(KernelAuthPort.KernelLoginResult loginResult) {
        AppUser buyer = AppUser.builder()
                .firstName(loginResult.firstName())
                .lastName(loginResult.lastName())
                .email(loginResult.email())
                .passwordHash(passwordEncoder.encode(KERNEL_MANAGED_PASSWORD))
                .role(UserRole.ROLE_BUYER)
                .kernelUserId(loginResult.userId())
                .profilePictureUrl(loginResult.profilePictureUrl())
                .build();
        buyer = userRepository.save(buyer);
        log.info("Profil acheteur local recree pour {} (kernelUserId={})", buyer.getEmail(), buyer.getKernelUserId());
        return buyer;
    }

    private boolean isArtistKernelAccount(KernelAuthPort.KernelLoginResult loginResult) {
        if ("BUSINESS".equalsIgnoreCase(loginResult.actorType())) {
            return true;
        }
        return loginResult.organizations() != null && !loginResult.organizations().isEmpty();
    }

    private String resolveArtistStatusFromKernel(
            KernelAuthPort.KernelLoginResult loginResult,
            UUID organizationId
    ) {
        if (organizationId != null && loginResult.actorId() != null) {
            return "ACTIVE";
        }
        return KernelStatusResolver.determineStatusFromKernel(
                loginResult.emailVerified(),
                loginResult.registrationStatus(),
                loginResult.accountStatus(),
                loginResult.organizations(),
                loginResult.actorId()
        );
    }

    private String resolveDisplayName(KernelAuthPort.KernelLoginResult loginResult) {
        if (loginResult.firstName() != null && loginResult.lastName() != null) {
            return loginResult.firstName() + " " + loginResult.lastName();
        }
        if (loginResult.firstName() != null && !loginResult.firstName().isBlank()) {
            return loginResult.firstName();
        }
        if (loginResult.username() != null && !loginResult.username().isBlank()) {
            return loginResult.username();
        }
        return loginResult.email().split("@")[0];
    }

    private String generateUniqueArtistSlug(String artistName, String email) {
        String base = artistName != null ? artistName : email.split("@")[0];
        String slug = base.toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        if (slug.isBlank()) {
            slug = "artist";
        }
        if (artistRepository.findBySlug(slug).isEmpty()) {
            return slug;
        }
        return slug + "-" + UUID.randomUUID().toString().substring(0, 5);
    }

    private AppUser syncLocalUserLink(KernelAuthPort.KernelLoginResult loginResult) {
        if (loginResult.userId() != null) {
            Optional<Artist> byKernelUser = artistRepository.findByKernelUserId(loginResult.userId());
            if (byKernelUser.isPresent()) {
                return refreshArtistStatus(byKernelUser.get(), loginResult);
            }
            Optional<AppUser> byKernelUserGeneric = userRepository.findByKernelUserId(loginResult.userId());
            if (byKernelUserGeneric.isPresent()) {
                return byKernelUserGeneric.get();
            }
        }
        if (loginResult.email() == null) {
            return null;
        }

        Optional<Artist> artist = artistRepository.findByEmail(loginResult.email())
                .map(found -> {
                    linkKernelUserId(found, loginResult.userId());
                    return refreshArtistStatus(found, loginResult);
                });
        if (artist.isPresent()) {
            return artist.get();
        }

        Optional<AppUser> user = userRepository.findByEmail(loginResult.email())
                .map(found -> {
                    linkKernelUserId(found, loginResult.userId());
                    return found;
                });
        return user.orElse(null);
    }

    private Artist refreshArtistStatus(Artist artist, KernelAuthPort.KernelLoginResult loginResult) {
        String oldStatus = artist.getStatus();
        KernelAuthPort.KernelUserProfile kernelProfile = fetchKernelProfile(loginResult);

        UUID organizationId = KernelOrganizationResolver.resolveOrganizationId(loginResult, kernelProfile)
                .orElse(null);
        if (organizationId != null) {
            artist.setOrganizationId(organizationId);
        }
        if (loginResult.actorId() != null) {
            artist.setKernelActorId(loginResult.actorId());
        } else if (kernelProfile != null && kernelProfile.actorId() != null) {
            artist.setKernelActorId(kernelProfile.actorId());
        }
        if (loginResult.tenantId() != null) {
            artist.setTenantId(loginResult.tenantId());
        } else if (kernelProfile != null && kernelProfile.tenantId() != null) {
            artist.setTenantId(kernelProfile.tenantId());
        }

        String newStatus;
        if (artist.getOrganizationId() != null && artist.getKernelActorId() != null) {
            newStatus = "ACTIVE";
        } else {
            newStatus = KernelStatusResolver.determineStatusFromKernel(
                    loginResult.emailVerified(),
                    loginResult.registrationStatus(),
                    loginResult.accountStatus(),
                    loginResult.organizations(),
                    loginResult.actorId()
            );
        }
        
        artist.setStatus(newStatus);

        if ("ACTIVE".equalsIgnoreCase(newStatus) && !"ACTIVE".equalsIgnoreCase(oldStatus)) {
            UUID orgId = artist.getOrganizationId();
            if (orgId != null) {
                try {
                    tenantMigrationService.migrateTenant(orgId);
                } catch (Exception e) {
                    log.error("Failed to run tenant migration for org {}", orgId, e);
                }
            }
        }
        
        kernelArtistRegistrationService.provisionArtistIfPending(artist, loginResult);
        return artistRepository.save(artist);
    }

    private KernelAuthPort.KernelUserProfile fetchKernelProfile(KernelAuthPort.KernelLoginResult loginResult) {
        if (loginResult.accessToken() == null || loginResult.accessToken().isBlank()) {
            return null;
        }
        if (loginResult.organizations() != null && !loginResult.organizations().isEmpty()) {
            return null;
        }
        try {
            return kernelAuthPort.me(loginResult.accessToken());
        } catch (RuntimeException ex) {
            log.debug("Profil kernel /me ignore pour {}: {}", loginResult.email(), ex.getMessage());
            return null;
        }
    }

    private AppUser linkKernelUserId(AppUser user, UUID kernelUserId) {
        if (kernelUserId == null) {
            return user;
        }
        if (user.getKernelUserId() != null && kernelUserId.equals(user.getKernelUserId())) {
            return user;
        }
        user.setKernelUserId(kernelUserId);
        if (user instanceof Artist artist) {
            return artistRepository.save(artist);
        }
        return userRepository.save(user);
    }

    public ProfileImageUploadResponse uploadRegistrationAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Fichier image requis");
        }
        try {
            String fileName = file.getOriginalFilename();
            if (fileName == null || fileName.isBlank()) {
                fileName = "profile-picture.jpg";
            }
            String bootstrapAdminToken = kernelBootstrapAdminSession.getBootstrapAdminAccessToken();
            KernelFilePort.FileView uploaded = kernelFilePort.upload(
                    new KernelFilePort.UploadFileCommand(
                            null,
                            file.getBytes(),
                            fileName,
                            file.getContentType(),
                            "PROFILE_PICTURE"
                    ),
                    bootstrapAdminToken
            );
            return ProfileImageUploadResponse.builder()
                    .fileId(uploaded.id())
                    .imageUrl(uploaded.downloadUrl())
                    .build();
        } catch (KernelClientException ex) {
            throw new IllegalStateException(
                    ex.getMessage() != null ? ex.getMessage() : "Echec upload photo de profil via le kernel (bootstrap)",
                    ex
            );
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Impossible de lire le fichier image", ex);
        }
    }
}
