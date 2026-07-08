package com.yowpainter.modules.auth.infrastructure.adapter.out.kernel;

import com.yowpainter.config.KernelProperties;
import com.yowpainter.modules.auth.application.port.out.KernelAuthPort;
import com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto.KernelAuthSessionResponseDto;
import com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto.KernelContextualSignUpRequestDto;
import com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto.KernelDiscoverSignUpContextsRequestDto;
import com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto.KernelDiscoverSignUpContextsResponseDto;
import com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto.KernelForgotPasswordResponseDto;
import com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto.KernelIssuePasswordResetRequestDto;
import com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto.KernelIssuedPasswordResetResponseDto;
import com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto.KernelLoginRequestDto;
import com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto.KernelLogoutRequestDto;
import com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto.KernelOrganizationAccessDto;
import com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto.KernelPasswordResetContextDto;
import com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto.KernelRefreshTokenRequestDto;
import com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto.KernelResetPasswordRequestDto;
import com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto.KernelSignUpContextDto;
import com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto.KernelSignUpRequestDto;
import com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto.KernelUserProfileResponseDto;
import com.yowpainter.shared.kernel.KernelHttpClient;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Primary
@Profile("!test")
public class KernelAuthHttpAdapter implements KernelAuthPort {

    private final KernelHttpClient kernelHttpClient;
    private final KernelProperties kernelProperties;

    public KernelAuthHttpAdapter(KernelHttpClient kernelHttpClient, KernelProperties kernelProperties) {
        this.kernelHttpClient = kernelHttpClient;
        this.kernelProperties = kernelProperties;
    }

    @Override
    public KernelLoginResult login(String principal, String password) {
        return mapSession(kernelHttpClient.post(
                "/api/auth/login",
                new KernelLoginRequestDto(principal, password),
                KernelAuthSessionResponseDto.class
        ));
    }

    @Override
    public KernelLoginResult signUp(SignUpCommand command) {
        return mapSession(kernelHttpClient.post(
                "/api/auth/sign-up",
                new KernelSignUpRequestDto(
                        command.firstName(),
                        command.lastName(),
                        command.email(),
                        command.email(), // username
                        kernelProperties.tenantId(), // tenantId
                        command.password(),
                        command.accountType()
                ),
                KernelAuthSessionResponseDto.class
        ));
    }

    @Override
    public DiscoverSignUpContextsResult discoverSignUpContexts(String organizationCode) {
        KernelDiscoverSignUpContextsResponseDto response = kernelHttpClient.post(
                "/api/auth/discover-sign-up-contexts",
                new KernelDiscoverSignUpContextsRequestDto(organizationCode),
                KernelDiscoverSignUpContextsResponseDto.class
        );
        return mapDiscovery(response);
    }

    @Override
    public KernelLoginResult signUpWithContext(ContextualSignUpCommand command) {
        return mapSession(kernelHttpClient.post(
                "/api/auth/sign-up",
                new KernelContextualSignUpRequestDto(
                        command.selectionToken(),
                        command.contextId(),
                        command.firstName(),
                        command.lastName(),
                        command.email(),
                        command.email(), // username
                        kernelProperties.tenantId(), // tenantId
                        command.password(),
                        command.accountType(),
                        command.businessType(),
                        command.onboardingData()
                ),
                KernelAuthSessionResponseDto.class
        ));
    }

    @Override
    public void requestEmailVerification(String accessToken) {
        kernelHttpClient.postVoid("/api/auth/email-verification/request", null, null, accessToken);
    }

    @Override
    public KernelLoginResult confirmEmailVerification(String verificationToken) {
        return mapSession(kernelHttpClient.post(
                "/api/auth/email-verification/confirm",
                Map.of("verificationToken", verificationToken),
                KernelAuthSessionResponseDto.class
        ));
    }

    @Override
    public KernelLoginResult refresh(String refreshToken) {
        return mapSession(kernelHttpClient.post(
                "/api/auth/refresh",
                new KernelRefreshTokenRequestDto(refreshToken),
                KernelAuthSessionResponseDto.class
        ));
    }

    @Override
    public void logout(String refreshToken) {
        kernelHttpClient.postVoid(
                "/api/auth/logout",
                new KernelLogoutRequestDto(refreshToken),
                null
        );
    }

    @Override
    public KernelUserProfile me(String accessToken) {
        KernelUserProfileResponseDto profile = kernelHttpClient.get(
                "/api/users/me",
                KernelUserProfileResponseDto.class,
                null,
                accessToken
        );
        return mapProfile(profile);
    }

    @Override
    public ForgotPasswordResult forgotPassword(String principal) {
        KernelForgotPasswordResponseDto response = kernelHttpClient.post(
                "/api/auth/forgot-password",
                Map.of("principal", principal),
                KernelForgotPasswordResponseDto.class
        );
        return mapForgotPassword(response);
    }

    @Override
    public IssuedPasswordResetResult issuePasswordReset(String selectionToken, String contextId) {
        KernelIssuedPasswordResetResponseDto response = kernelHttpClient.post(
                "/api/auth/password-reset/issue",
                new KernelIssuePasswordResetRequestDto(selectionToken, contextId),
                KernelIssuedPasswordResetResponseDto.class
        );
        return new IssuedPasswordResetResult(
                response.deliveryMode(),
                response.challengeTokenPreview(),
                response.expiresInSeconds() != null ? response.expiresInSeconds() : 0L
        );
    }

    @Override
    public void resetPassword(String resetToken, String newPassword) {
        kernelHttpClient.postVoid(
                "/api/auth/reset-password",
                new KernelResetPasswordRequestDto(resetToken, newPassword),
                null
        );
    }

    private KernelLoginResult mapSession(KernelAuthSessionResponseDto dto) {
        if (dto == null) {
            return null;
        }
        return new KernelLoginResult(
                dto.userId(),
                dto.tenantId(),
                dto.actorId(),
                dto.username(),
                dto.email(),
                dto.firstName(),
                dto.lastName(),
                dto.phoneNumber(),
                dto.accountStatus(),
                dto.commercialPlanCode(),
                dto.onboardingStatus(),
                dto.onboardingStep() != null ? dto.onboardingStep() : 0,
                dto.actorType(),
                dto.profilePictureUrl(),
                dto.locale(),
                dto.emailVerified(),
                dto.emailVerifiedAt(),
                dto.mfaEnabled(),
                dto.mfaChannel(),
                dto.passwordChangeRequired(),
                dto.registrationStatus(),
                dto.accessToken(),
                dto.refreshToken(),
                dto.tokenType(),
                dto.expiresInSeconds() != null ? dto.expiresInSeconds() : 0L,
                mapOrganizations(dto.organizations()),
                dto.authorities() != null ? dto.authorities() : Set.of()
        );
    }

    private KernelUserProfile mapProfile(KernelUserProfileResponseDto dto) {
        if (dto == null) {
            return null;
        }
        return new KernelUserProfile(
                dto.userId(),
                dto.tenantId(),
                dto.actorId(),
                dto.username(),
                dto.email(),
                dto.firstName(),
                dto.lastName(),
                dto.phoneNumber(),
                dto.accountStatus(),
                dto.commercialPlanCode(),
                dto.onboardingStatus(),
                dto.onboardingStep() != null ? dto.onboardingStep() : 0,
                dto.actorType(),
                dto.profilePictureUrl(),
                dto.locale(),
                dto.emailVerified(),
                dto.emailVerifiedAt(),
                dto.mfaEnabled(),
                dto.mfaChannel(),
                dto.passwordChangeRequired(),
                dto.registrationStatus(),
                mapOrganizations(dto.organizations())
        );
    }

    private DiscoverSignUpContextsResult mapDiscovery(KernelDiscoverSignUpContextsResponseDto dto) {
        if (dto == null) {
            return new DiscoverSignUpContextsResult(null, 0L, List.of());
        }
        List<SignUpContext> contexts = dto.contexts() == null ? List.of() : dto.contexts().stream()
                .map(this::mapSignUpContext)
                .toList();
        return new DiscoverSignUpContextsResult(
                dto.selectionToken(),
                dto.expiresInSeconds() != null ? dto.expiresInSeconds() : 0L,
                contexts
        );
    }

    private SignUpContext mapSignUpContext(KernelSignUpContextDto dto) {
        return new SignUpContext(
                dto.tenantId(),
                dto.organizationId(),
                dto.organizationCode(),
                dto.shortName(),
                dto.longName(),
                dto.contextId()
        );
    }

    private ForgotPasswordResult mapForgotPassword(KernelForgotPasswordResponseDto dto) {
        if (dto == null) {
            return new ForgotPasswordResult(null, 0, null, 0L, List.of());
        }
        List<PasswordResetContext> contexts = dto.contexts() == null ? List.of() : dto.contexts().stream()
                .map(this::mapPasswordResetContext)
                .toList();
        return new ForgotPasswordResult(
                dto.principal(),
                dto.matchingAccountCount() != null ? dto.matchingAccountCount() : 0,
                dto.selectionToken(),
                dto.expiresInSeconds() != null ? dto.expiresInSeconds() : 0L,
                contexts
        );
    }

    private PasswordResetContext mapPasswordResetContext(KernelPasswordResetContextDto dto) {
        return new PasswordResetContext(
                dto.contextId(),
                dto.tenantId(),
                dto.organizationId(),
                dto.userId(),
                dto.principal(),
                dto.displayName()
        );
    }

    private List<KernelOrganizationAccess> mapOrganizations(List<KernelOrganizationAccessDto> organizations) {
        if (organizations == null) {
            return List.of();
        }
        return organizations.stream()
                .map(org -> new KernelOrganizationAccess(
                        org.organizationId(),
                        org.organizationCode(),
                        org.shortName(),
                        org.displayName(),
                        org.services() != null ? org.services() : List.of()
                ))
                .toList();
    }
}
