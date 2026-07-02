package com.yowpainter.modules.auth.application.service;

import com.yowpainter.config.KernelProperties;
import com.yowpainter.modules.auth.application.port.out.KernelAuthPort;
import com.yowpainter.modules.auth.domain.model.AppUser;
import com.yowpainter.modules.auth.domain.model.UserRole;
import com.yowpainter.modules.auth.domain.port.out.AppUserRepositoryPort;
import com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto.AuthResponse;
import com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto.RegisterRequest;
import com.yowpainter.shared.kernel.KernelClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KernelBuyerRegistrationService {

    private static final String KERNEL_MANAGED_PASSWORD = "{KERNEL_MANAGED}";

    private final KernelAuthPort kernelAuthPort;
    private final AppUserRepositoryPort userRepository;
    private final PasswordEncoder passwordEncoder;
    private final KernelProperties kernelProperties;

    @Transactional
    public AuthResponse registerBuyer(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Un utilisateur avec cet email existe deja");
        }

        String organizationCode = resolvePlatformOrganizationCode();

        try {
            KernelAuthPort.DiscoverSignUpContextsResult discovery = kernelAuthPort.discoverSignUpContexts(organizationCode);

            Map<String, Object> onboardingData = new LinkedHashMap<>();
            onboardingData.put("platform", "yowpainter");
            onboardingData.put("role", UserRole.ROLE_BUYER.name());

            KernelAuthPort.KernelLoginResult signup = signUpWithDiscovery(
                    discovery,
                    organizationCode,
                    request,
                    onboardingData
            );

            if (Boolean.FALSE.equals(signup.emailVerified()) && signup.accessToken() != null) {
                kernelAuthPort.requestEmailVerification(signup.accessToken());
            }

            AppUser buyer = AppUser.builder()
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .email(request.getEmail())
                    .passwordHash(passwordEncoder.encode(KERNEL_MANAGED_PASSWORD))
                    .role(UserRole.ROLE_BUYER)
                    .kernelUserId(signup.userId())
                    .profilePictureUrl(request.getImageUrl())
                    .build();
            userRepository.save(buyer);

            AuthResponse response = KernelAuthMapper.toAuthResponse(signup, buyer);
            response.setMessage(buildRegistrationMessage(signup));
            if (Boolean.FALSE.equals(signup.emailVerified())) {
                response.setAccessToken(null);
                response.setRefreshToken(null);
            }
            return response;
        } catch (KernelClientException ex) {
            throw new IllegalArgumentException(resolveKernelErrorMessage(ex));
        }
    }

    void applyEmailConfirmedBuyer(AppUser buyer, KernelAuthPort.KernelLoginResult confirmed) {
        if (confirmed.userId() != null) {
            buyer.setKernelUserId(confirmed.userId());
        }
        userRepository.save(buyer);
    }

    private String resolvePlatformOrganizationCode() {
        String code = kernelProperties.signupPlatformOrganizationCode();
        if (code == null || code.isBlank()) {
            return kernelProperties.clientId();
        }
        return code;
    }

    private KernelAuthPort.KernelLoginResult signUpWithDiscovery(
            KernelAuthPort.DiscoverSignUpContextsResult discovery,
            String organizationCode,
            RegisterRequest request,
            Map<String, Object> onboardingData
    ) {
        if (discovery.contexts() != null && !discovery.contexts().isEmpty()) {
            KernelAuthPort.SignUpContext context = resolveSignUpContext(discovery, organizationCode);
            return kernelAuthPort.signUpWithContext(
                    new KernelAuthPort.ContextualSignUpCommand(
                            discovery.selectionToken(),
                            context.contextId(),
                            request.getFirstName(),
                            request.getLastName(),
                            request.getEmail(),
                            request.getPassword(),
                            "PROSPECT",
                            null,
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
                        "PROSPECT",
                        null,
                        onboardingData
                )
        );
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

    private String buildRegistrationMessage(KernelAuthPort.KernelLoginResult signup) {
        if (Boolean.TRUE.equals(signup.emailVerified())) {
            return "Inscription enregistree. Vous pouvez vous connecter.";
        }
        return "Inscription enregistree. Un e-mail de verification vous a ete envoye pour activer votre compte.";
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
}
