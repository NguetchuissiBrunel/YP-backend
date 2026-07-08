package com.yowpainter.modules.auth.infrastructure.adapter.out.external;

import com.yowpainter.config.KernelProperties;
import com.yowpainter.modules.auth.application.port.out.KernelAuthPort;
import com.yowpainter.shared.kernel.KernelHttpClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component("externalKernelAuthHttpAdapter")
@Profile("!test")
@RequiredArgsConstructor
public class KernelAuthHttpAdapter implements KernelAuthPort {

    private final KernelHttpClient kernelHttpClient;
    private final KernelProperties kernelProperties;

    @Override
    public ForgotPasswordResult forgotPassword(String email) {
        return kernelHttpClient.post(
                "/api/auth/forgot-password",
                Map.of("principal", email),
                ForgotPasswordResult.class
        );
    }

    @Override
    public IssuedPasswordResetResult issuePasswordReset(String selectionToken, String contextId) {
        Map<String, String> body = new HashMap<>();
        body.put("selectionToken", selectionToken);
        body.put("contextId", contextId);
        return kernelHttpClient.post(
                "/api/auth/password-reset/issue",
                body,
                IssuedPasswordResetResult.class
        );
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        kernelHttpClient.postVoid(
                "/api/auth/reset-password",
                Map.of("resetToken", token, "newPassword", newPassword)
        );
    }

    @Override
    public KernelLoginResult confirmEmailVerification(String verificationToken) {
        KernelUserProfile profile = kernelHttpClient.post(
                "/api/auth/email-verification/confirm",
                Map.of("verificationToken", verificationToken),
                KernelUserProfile.class
        );
        return toLoginResult(profile, null, null, null, 0);
    }

    @Override
    public KernelLoginResult login(String email, String password) {
        KernelLoginResponse response = kernelHttpClient.post(
                "/api/auth/login",
                Map.of("principal", email, "password", password),
                KernelLoginResponse.class
        );
        return response != null ? response.toResult() : null;
    }

    @Override
    public KernelLoginResult refresh(String refreshToken) {
        RefreshTokenResponse response = kernelHttpClient.post(
                "/api/auth/refresh",
                Map.of("refreshToken", refreshToken),
                RefreshTokenResponse.class
        );

        if (response == null || response.accessToken() == null) {
            return null;
        }

        KernelUserProfile profile = me(response.accessToken());
        return toLoginResult(
                profile,
                response.accessToken(),
                response.refreshToken(),
                response.tokenType(),
                response.accessExpiresInSeconds()
        );
    }

    @Override
    public void logout(String refreshToken) {
        kernelHttpClient.postVoid(
                "/api/auth/logout",
                Map.of("refreshToken", refreshToken)
        );
    }

    @Override
    public DiscoverSignUpContextsResult discoverSignUpContexts(String organizationCode) {
        DiscoverSignUpContextsResponse response = kernelHttpClient.post(
                "/api/auth/discover-sign-up-contexts",
                Map.of("organizationCode", organizationCode),
                DiscoverSignUpContextsResponse.class
        );
        return response != null ? response.toResult() : null;
    }

    @Override
    public KernelLoginResult signUp(SignUpCommand command) {
        Map<String, Object> body = new HashMap<>();
        if (kernelProperties.tenantId() != null && !kernelProperties.tenantId().isBlank()) {
            body.put("tenantId", kernelProperties.tenantId());
        }
        body.put("firstName", command.firstName());
        body.put("lastName", command.lastName());
        body.put("username", command.email());
        body.put("email", command.email());
        body.put("password", command.password());
        body.put("accountType", command.accountType());

        KernelLoginResponse response = kernelHttpClient.post(
                "/api/auth/sign-up",
                body,
                KernelLoginResponse.class
        );
        return response != null ? response.toResult() : null;
    }

    @Override
    public KernelLoginResult signUpWithContext(ContextualSignUpCommand command) {
        Map<String, Object> body = new HashMap<>();
        if (kernelProperties.tenantId() != null && !kernelProperties.tenantId().isBlank()) {
            body.put("tenantId", kernelProperties.tenantId());
        }
        body.put("signUpSelectionToken", command.selectionToken());
        body.put("contextId", command.contextId());
        body.put("firstName", command.firstName());
        body.put("lastName", command.lastName());
        body.put("username", command.email());
        body.put("email", command.email());
        body.put("password", command.password());
        body.put("accountType", command.accountType());
        body.put("businessType", command.businessType());
        body.put("onboardingData", command.onboardingData());

        KernelLoginResponse response = kernelHttpClient.post(
                "/api/auth/sign-up",
                body,
                KernelLoginResponse.class
        );
        return response != null ? response.toResult() : null;
    }

    @Override
    public void requestEmailVerification(String accessToken) {
        kernelHttpClient.postVoid(
                "/api/auth/email-verification/request",
                null,
                null,
                accessToken
        );
    }

    @Override
    public KernelUserProfile me(String accessToken) {
        return kernelHttpClient.get(
                "/api/users/me",
                KernelUserProfile.class,
                null,
                accessToken
        );
    }

    private KernelLoginResult toLoginResult(
            KernelUserProfile profile,
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresInSeconds
    ) {
        if (profile == null) {
            return null;
        }
        return new KernelLoginResult(
                profile.userId(),
                profile.tenantId(),
                profile.actorId(),
                profile.username(),
                profile.email(),
                profile.firstName(),
                profile.lastName(),
                profile.phoneNumber(),
                profile.accountStatus(),
                profile.commercialPlanCode(),
                profile.onboardingStatus(),
                profile.onboardingStep(),
                profile.actorType(),
                profile.profilePictureUrl(),
                profile.locale(),
                profile.emailVerified(),
                profile.emailVerifiedAt(),
                profile.mfaEnabled(),
                profile.mfaChannel(),
                profile.passwordChangeRequired(),
                profile.registrationStatus(),
                accessToken,
                refreshToken,
                tokenType,
                expiresInSeconds,
                profile.organizations(),
                Collections.emptySet()
        );
    }

    private record KernelLoginResponse(
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
            List<KernelOrganizationAccess> organizations,
            Set<String> authorities
    ) {
        public KernelLoginResult toResult() {
            return new KernelLoginResult(
                    userId,
                    tenantId,
                    actorId,
                    username,
                    email,
                    firstName,
                    lastName,
                    phoneNumber,
                    accountStatus,
                    commercialPlanCode,
                    onboardingStatus,
                    onboardingStep != null ? onboardingStep : 0,
                    actorType,
                    profilePictureUrl,
                    locale,
                    emailVerified,
                    emailVerifiedAt,
                    mfaEnabled,
                    mfaChannel,
                    passwordChangeRequired,
                    registrationStatus,
                    accessToken,
                    refreshToken,
                    tokenType,
                    expiresInSeconds != null ? expiresInSeconds : 0L,
                    organizations,
                    authorities != null ? authorities : Collections.emptySet()
            );
        }
    }

    private record RefreshTokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long accessExpiresInSeconds,
            long refreshExpiresInSeconds,
            Instant refreshExpiresAt
    ) {}

    private record DiscoverSignUpContextsResponse(
            String selectionToken,
            long expiresInSeconds,
            List<SignUpContextResponse> contexts
    ) {
        public DiscoverSignUpContextsResult toResult() {
            List<SignUpContext> mappedContexts = contexts != null
                    ? contexts.stream().map(c -> new SignUpContext(
                            c.tenantId(),
                            c.organizationId(),
                            c.organizationCode(),
                            c.organizationName(),
                            c.organizationName(),
                            c.contextId()
                    )).toList()
                    : Collections.emptyList();

            return new DiscoverSignUpContextsResult(
                    selectionToken,
                    expiresInSeconds,
                    mappedContexts
            );
        }
    }

    private record SignUpContextResponse(
            String contextId,
            UUID tenantId,
            UUID organizationId,
            String organizationCode,
            String organizationName,
            String organizationType
    ) {}
}
