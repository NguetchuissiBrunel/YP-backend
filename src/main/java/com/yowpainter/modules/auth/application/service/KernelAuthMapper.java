package com.yowpainter.modules.auth.application.service;

import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.auth.application.port.out.KernelAuthPort;
import com.yowpainter.modules.auth.domain.model.AppUser;
import com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto.AuthResponse;
import com.yowpainter.shared.security.KernelAuthorityMapper;

import java.util.List;
import java.util.UUID;

final class KernelAuthMapper {

    private KernelAuthMapper() {
    }

    static AuthResponse toAuthResponse(KernelAuthPort.KernelLoginResult loginResult, AppUser user) {
        UUID organizationId = resolveOrganizationId(loginResult, user instanceof Artist artist ? artist : null);
        return AuthResponse.builder()
                .accessToken(loginResult.accessToken())
                .refreshToken(loginResult.refreshToken())
                .email(loginResult.email())
                .firstName(user != null ? user.getFirstName() : null)
                .lastName(user != null ? user.getLastName() : null)
                .imageUrl(user != null ? com.yowpainter.shared.utils.UrlSanitizer.sanitizeFileUrl(user.getProfilePictureUrl()) : null)
                .role(resolveRole(loginResult, user))
                .tenantId(loginResult.tenantId() != null ? loginResult.tenantId().toString() : null)
                .artistName(user instanceof Artist artist ? artist.getArtistName() : null)
                .kernelUserId(loginResult.userId())
                .organizationId(organizationId)
                .organizations(mapOrganizations(loginResult.organizations()))
                .emailVerified(resolveEmailVerified(loginResult, user))
                .registrationStatus(user instanceof Artist artist ? artist.getStatus() : (user != null ? "ACTIVE" : null))
                .build();
    }

    private static Boolean resolveEmailVerified(
            KernelAuthPort.KernelLoginResult loginResult,
            AppUser user
    ) {
        if (Boolean.TRUE.equals(loginResult.emailVerified())) {
            return true;
        }
        if (user instanceof Artist artist
                && artist.getStatus() != null
                && !"PENDING_EMAIL".equalsIgnoreCase(artist.getStatus())) {
            return true;
        }
        return loginResult.emailVerified();
    }

    private static UUID resolveOrganizationId(KernelAuthPort.KernelLoginResult loginResult, Artist artist) {
        if (artist != null && artist.getOrganizationId() != null) {
            return artist.getOrganizationId();
        }
        if (loginResult.organizations() != null && loginResult.organizations().size() == 1) {
            return loginResult.organizations().get(0).organizationId();
        }
        return null;
    }

    private static String resolveRole(KernelAuthPort.KernelLoginResult loginResult, AppUser user) {
        if (user != null) {
            return user.getRole().name();
        }
        if (loginResult.authorities() != null
                && loginResult.authorities().stream().anyMatch(KernelAuthorityMapper::isKernelAdminAuthority)) {
            return "ROLE_ADMIN";
        }
        if (loginResult.authorities() != null) {
            return loginResult.authorities().stream().findFirst().orElse("ROLE_BUYER");
        }
        return "ROLE_BUYER";
    }

    private static List<AuthResponse.OrganizationAccessResponse> mapOrganizations(
            List<KernelAuthPort.KernelOrganizationAccess> organizations
    ) {
        if (organizations == null) {
            return List.of();
        }
        return organizations.stream()
                .map(org -> AuthResponse.OrganizationAccessResponse.builder()
                        .organizationId(org.organizationId())
                        .organizationCode(org.organizationCode())
                        .displayName(org.displayName() != null ? org.displayName() : org.shortName())
                        .services(org.services())
                        .build())
                .toList();
    }
}
