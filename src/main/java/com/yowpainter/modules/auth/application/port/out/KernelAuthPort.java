package com.yowpainter.modules.auth.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface KernelAuthPort {

    KernelLoginResult login(String principal, String password);

    KernelLoginResult signUp(SignUpCommand command);

    DiscoverSignUpContextsResult discoverSignUpContexts(String organizationCode);

    KernelLoginResult signUpWithContext(ContextualSignUpCommand command);

    void requestEmailVerification(String accessToken);

    KernelLoginResult confirmEmailVerification(String verificationToken);

    KernelLoginResult refresh(String refreshToken);

    void logout(String refreshToken);

    KernelUserProfile me(String accessToken);

    ForgotPasswordResult forgotPassword(String principal);

    IssuedPasswordResetResult issuePasswordReset(String selectionToken, String contextId);

    void resetPassword(String resetToken, String newPassword);

    record SignUpCommand(
            String firstName,
            String lastName,
            String email,
            String password,
            String accountType
    ) {
    }

    record ContextualSignUpCommand(
            String selectionToken,
            String contextId,
            String firstName,
            String lastName,
            String email,
            String password,
            String accountType,
            String businessType,
            Map<String, Object> onboardingData
    ) {
    }

    record DiscoverSignUpContextsResult(
            String selectionToken,
            long expiresInSeconds,
            List<SignUpContext> contexts
    ) {
    }

    record SignUpContext(
            UUID tenantId,
            UUID organizationId,
            String organizationCode,
            String shortName,
            String longName,
            String contextId
    ) {
    }

    record ForgotPasswordResult(
            String principal,
            int matchingAccountCount,
            String selectionToken,
            long expiresInSeconds,
            List<PasswordResetContext> contexts
    ) {
    }

    record PasswordResetContext(
            String contextId,
            UUID tenantId,
            UUID organizationId,
            UUID userId,
            String principal,
            String displayName
    ) {
    }

    record IssuedPasswordResetResult(
            String deliveryMode,
            String challengeTokenPreview,
            long expiresInSeconds
    ) {
    }

    record KernelOrganizationAccess(
            UUID organizationId,
            String organizationCode,
            String shortName,
            String displayName,
            List<String> services
    ) {
    }

    record KernelLoginResult(
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
            int onboardingStep,
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
            long expiresInSeconds,
            List<KernelOrganizationAccess> organizations,
            Set<String> authorities
    ) {
    }

    record KernelUserProfile(
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
            int onboardingStep,
            String actorType,
            String profilePictureUrl,
            String locale,
            Boolean emailVerified,
            Instant emailVerifiedAt,
            Boolean mfaEnabled,
            String mfaChannel,
            Boolean passwordChangeRequired,
            String registrationStatus,
            List<KernelOrganizationAccess> organizations
    ) {
    }
}
