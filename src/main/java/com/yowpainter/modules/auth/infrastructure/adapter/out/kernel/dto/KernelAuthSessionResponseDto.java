package com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record KernelAuthSessionResponseDto(
        UUID userId,
        UUID tenantId,
        UUID actorId,
        String username,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        String accountStatus,
        String commercialPlanCode,
        String onboardingStatus,
        Integer onboardingStep,
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
        String refreshToken,
        String tokenType,
        Long expiresInSeconds,
        List<KernelOrganizationAccessDto> organizations,
        Set<String> authorities
) {
}
