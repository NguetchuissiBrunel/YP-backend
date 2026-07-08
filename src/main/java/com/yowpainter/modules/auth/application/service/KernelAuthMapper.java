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
        Boolean emailVerified = resolveEmailVerified(loginResult, user);
        
        String regStatus = null;
        Boolean canAccessDashboard = true;
        String pendingReason = null;
        String nextAction = null;
        Boolean refreshAllowed = true;
        String kernelVerificationUrl = null;

        if (user instanceof Artist artist) {
            regStatus = artist.getStatus();
            
            if ("PENDING_EMAIL".equalsIgnoreCase(regStatus)) {
                canAccessDashboard = false;
                pendingReason = "EMAIL_NOT_VERIFIED";
                nextAction = "VERIFY_EMAIL";
                refreshAllowed = true;
                kernelVerificationUrl = "https://kernel-core.yowyob.com";
            } else if ("EMAIL_VERIFIED".equalsIgnoreCase(regStatus)) {
                canAccessDashboard = false;
                pendingReason = "ORGANIZATION_NOT_CREATED";
                nextAction = "WAIT_ORGANIZATION_VALIDATION";
                refreshAllowed = true;
            } else if ("ORGANIZATION_VALIDATION_REQUIRED".equalsIgnoreCase(regStatus) 
                    || "PENDING_APPROVAL".equalsIgnoreCase(regStatus)) {
                regStatus = "ORGANIZATION_VALIDATION_REQUIRED";
                canAccessDashboard = false;
                pendingReason = "ORGANIZATION_PENDING_VALIDATION";
                nextAction = "WAIT_ORGANIZATION_VALIDATION";
                refreshAllowed = true;
            } else if ("ORGANIZATION_REJECTED".equalsIgnoreCase(regStatus)
                    || "REJECTED".equalsIgnoreCase(regStatus)) {
                regStatus = "ORGANIZATION_REJECTED";
                canAccessDashboard = false;
                pendingReason = "ORGANIZATION_REJECTED";
                nextAction = "CONTACT_SUPPORT";
                refreshAllowed = false;
            } else if ("ORGANIZATION_SUSPENDED".equalsIgnoreCase(regStatus)
                    || "SUSPENDED".equalsIgnoreCase(regStatus)) {
                regStatus = "ORGANIZATION_SUSPENDED";
                canAccessDashboard = false;
                pendingReason = "ORGANIZATION_SUSPENDED";
                nextAction = "CONTACT_SUPPORT";
                refreshAllowed = false;
            } else {
                regStatus = "ACTIVE";
                canAccessDashboard = true;
            }
        } else if (user != null) {
            regStatus = Boolean.TRUE.equals(emailVerified) ? "ACTIVE" : "PENDING_EMAIL";
            if (!Boolean.TRUE.equals(emailVerified)) {
                canAccessDashboard = false;
                pendingReason = "EMAIL_NOT_VERIFIED";
                nextAction = "VERIFY_EMAIL";
                refreshAllowed = true;
                kernelVerificationUrl = "https://kernel-core.yowyob.com";
            }
        }

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
                .emailVerified(emailVerified)
                .registrationStatus(regStatus)
                .canAccessDashboard(canAccessDashboard)
                .pendingReason(pendingReason)
                .nextAction(nextAction)
                .refreshAllowed(refreshAllowed)
                .kernelVerificationUrl(kernelVerificationUrl)
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
