package com.yowpainter.modules.auth.application.service;

import com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort;
import com.yowpainter.modules.auth.application.port.out.KernelAuthPort;
import com.yowpainter.modules.auth.domain.model.AppUser;
import com.yowpainter.modules.auth.domain.model.UserRole;
import com.yowpainter.modules.auth.domain.port.out.AppUserRepositoryPort;
import com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto.AdminRegisterRequest;
import com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto.AuthResponse;
import com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto.LoginRequest;
import com.yowpainter.shared.kernel.KernelClientException;
import com.yowpainter.shared.kernel.port.KernelAdministrationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KernelAdminRegistrationService {

    private static final String KERNEL_MANAGED_PASSWORD = "{KERNEL_MANAGED}";
    private static final List<String> ADMIN_ROLE_CODES = List.of("GENERAL_ADMIN", "TENANT_ADMIN");

    private final KernelAuthPort kernelAuthPort;
    private final KernelAdministrationPort kernelAdministrationPort;
    private final AppUserRepositoryPort userRepository;
    private final ArtistRepositoryPort artistRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse loginAdmin(LoginRequest request) {
        try {
            KernelAuthPort.KernelLoginResult loginResult = kernelAuthPort.login(
                    request.getEmail(),
                    request.getPassword()
            );
            AppUser admin = ensureLocalAdminProfile(loginResult);
            return KernelAuthMapper.toAuthResponse(loginResult, admin);
        } catch (KernelClientException ex) {
            throw new IllegalArgumentException(
                    ex.getMessage() != null ? ex.getMessage() : "Email ou mot de passe invalide"
            );
        }
    }

    @Transactional
    public AuthResponse registerAdmin(AdminRegisterRequest request) {
        rejectIfAlreadyRegisteredAsAdmin(request.getEmail());
        rejectIfArtistAccount(request.getEmail());

        try {
            KernelAuthPort.KernelLoginResult signup = kernelAuthPort.signUp(new KernelAuthPort.SignUpCommand(
                    request.getFirstName(),
                    request.getLastName(),
                    request.getEmail(),
                    request.getPassword(),
                    "PROSPECT"
            ));

            AppUser admin = AppUser.builder()
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .email(request.getEmail())
                    .passwordHash(passwordEncoder.encode(KERNEL_MANAGED_PASSWORD))
                    .role(UserRole.ROLE_ADMIN)
                    .kernelUserId(signup.userId())
                    .build();
            userRepository.save(admin);

            try {
                UUID adminRoleId = resolveAdminRoleId();
                kernelAdministrationPort.assignTenantAdminRole(signup.userId(), adminRoleId);
            } catch (RuntimeException ex) {
                log.warn(
                        "Assignation role administratif kernel ignoree pour {}: {}",
                        request.getEmail(),
                        ex.getMessage()
                );
            }

            KernelAuthPort.KernelLoginResult session = resolveSessionAfterSignup(
                    request,
                    signup
            );
            if (session.userId() != null) {
                admin.setKernelUserId(session.userId());
                admin = userRepository.save(admin);
            }

            return KernelAuthMapper.toAuthResponse(session, admin);
        } catch (KernelClientException ex) {
            throw new IllegalArgumentException(
                    ex.getMessage() != null ? ex.getMessage() : "Echec inscription administrateur via le kernel"
            );
        }
    }

    AppUser ensureLocalAdminProfile(KernelAuthPort.KernelLoginResult loginResult) {
        if (loginResult.email() == null || loginResult.email().isBlank()) {
            throw new IllegalArgumentException("E-mail utilisateur kernel introuvable apres connexion");
        }
        rejectIfArtistAccount(loginResult.email());

        if (loginResult.userId() != null) {
            Optional<AppUser> byKernelUserId = userRepository.findByKernelUserId(loginResult.userId());
            if (byKernelUserId.isPresent()) {
                return promoteToAdminIfNeeded(byKernelUserId.get(), loginResult);
            }
        }

        Optional<AppUser> byEmail = userRepository.findByEmail(loginResult.email());
        if (byEmail.isPresent()) {
            AppUser user = byEmail.get();
            if (user.getRole() == UserRole.ROLE_ADMIN) {
                return linkKernelUserId(user, loginResult.userId());
            }
            return promoteToAdminIfNeeded(user, loginResult);
        }

        AppUser admin = AppUser.builder()
                .firstName(loginResult.firstName())
                .lastName(loginResult.lastName())
                .email(loginResult.email())
                .passwordHash(passwordEncoder.encode(KERNEL_MANAGED_PASSWORD))
                .role(UserRole.ROLE_ADMIN)
                .kernelUserId(loginResult.userId())
                .build();
        admin = userRepository.save(admin);
        log.info("Profil administrateur local cree pour {} (kernelUserId={})", admin.getEmail(), admin.getKernelUserId());
        return admin;
    }

    private KernelAuthPort.KernelLoginResult resolveSessionAfterSignup(
            AdminRegisterRequest request,
            KernelAuthPort.KernelLoginResult signup
    ) {
        if (signup.accessToken() != null && !signup.accessToken().isBlank()) {
            return signup;
        }
        try {
            return kernelAuthPort.login(request.getEmail(), request.getPassword());
        } catch (KernelClientException ex) {
            log.warn(
                    "Login admin post-inscription ignore pour {} ({}). Utilisez POST /api/admin/auth/login.",
                    request.getEmail(),
                    ex.getMessage()
            );
            return signup;
        }
    }

    private void rejectIfAlreadyRegisteredAsAdmin(String email) {
        userRepository.findByEmail(email)
                .filter(user -> user.getRole() == UserRole.ROLE_ADMIN)
                .ifPresent(user -> {
                    throw new IllegalArgumentException("Un administrateur avec cet email existe deja");
                });
    }

    private void rejectIfArtistAccount(String email) {
        if (artistRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException(
                    "Ce compte est enregistre comme artiste. Utilisez la connexion artiste."
            );
        }
    }

    private AppUser promoteToAdminIfNeeded(AppUser user, KernelAuthPort.KernelLoginResult loginResult) {
        if (user.getRole() != UserRole.ROLE_ADMIN) {
            log.info("Promotion du profil local {} vers ROLE_ADMIN", user.getEmail());
            user.setRole(UserRole.ROLE_ADMIN);
        }
        if (loginResult.firstName() != null && !loginResult.firstName().isBlank()) {
            user.setFirstName(loginResult.firstName());
        }
        if (loginResult.lastName() != null && !loginResult.lastName().isBlank()) {
            user.setLastName(loginResult.lastName());
        }
        return linkKernelUserId(user, loginResult.userId());
    }

    private AppUser linkKernelUserId(AppUser user, UUID kernelUserId) {
        if (kernelUserId == null) {
            return user;
        }
        if (kernelUserId.equals(user.getKernelUserId())) {
            return user;
        }
        user.setKernelUserId(kernelUserId);
        return userRepository.save(user);
    }

    private UUID resolveAdminRoleId() {
        List<KernelAdministrationPort.AdministrativeRoleView> roles = new ArrayList<>();
        try {
            roles.addAll(kernelAdministrationPort.provisionDefaultRoles());
        } catch (Exception ex) {
            log.warn("Provision des roles administratifs kernel echouee: {}", ex.getMessage());
        }
        if (roles.isEmpty()) {
            roles.addAll(kernelAdministrationPort.listRoles());
        }
        return findAdminRoleId(roles).orElseThrow(() -> new IllegalStateException(
                "Role administratif introuvable (GENERAL_ADMIN ou TENANT_ADMIN). "
                        + "Verifiez KSM_KERNEL_BOOTSTRAP_ADMIN_USERNAME/PASSWORD et le compte platform-admin (MFA)."
        ));
    }

    private Optional<UUID> findAdminRoleId(List<KernelAdministrationPort.AdministrativeRoleView> roles) {
        for (String code : ADMIN_ROLE_CODES) {
            Optional<UUID> roleId = roles.stream()
                    .filter(role -> code.equalsIgnoreCase(role.code()))
                    .map(KernelAdministrationPort.AdministrativeRoleView::id)
                    .findFirst();
            if (roleId.isPresent()) {
                return roleId;
            }
        }
        return Optional.empty();
    }
}
