package com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record KernelAuthSessionResponseDto(
        @JsonAlias("id")
        UUID userId,
        UUID tenantId,
        UUID actorId,
        String username,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        @JsonAlias("status")
        String accountStatus,
        @JsonAlias("plan")
        String commercialPlanCode,
        String onboardingStatus,
        Integer onboardingStep,
        @JsonAlias("accountType")
        String actorType,
        String profilePictureUrl,
        String locale,
        Boolean emailVerified,
        Instant emailVerifiedAt,
        Boolean mfaEnabled,
        String mfaChannel,
        Boolean passwordChangeRequired,
        String registrationStatus,
        String accessToken,
        @JsonAlias("sessionToken")
        String refreshToken,
        String tokenType,
        @JsonAlias("accessExpiresInSeconds")
        Long expiresInSeconds,
        List<KernelOrganizationAccessDto> organizations,
        Set<String> authorities
) {
}
