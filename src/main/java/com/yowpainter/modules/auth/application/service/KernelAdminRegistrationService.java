package com.yowpainter.modules.auth.application.service;

import com.yowpainter.modules.auth.application.port.out.KernelAuthPort;
import com.yowpainter.modules.auth.domain.model.AppUser;
import com.yowpainter.modules.auth.domain.model.UserRole;
import com.yowpainter.modules.auth.domain.port.out.AppUserRepositoryPort;
import com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto.AdminRegisterRequest;
import com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto.AuthResponse;
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
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse registerAdmin(AdminRegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Un administrateur avec cet email existe deja");
        }

        try {
            KernelAuthPort.KernelLoginResult signup = kernelAuthPort.signUp(new KernelAuthPort.SignUpCommand(
                    request.getFirstName(),
                    request.getLastName(),
                    request.getEmail(),
                    request.getPassword(),
                    "PROSPECT"
            ));

            UUID adminRoleId = resolveAdminRoleId();
            kernelAdministrationPort.assignTenantAdminRole(signup.userId(), adminRoleId);

            KernelAuthPort.KernelLoginResult loginResult = kernelAuthPort.login(
                    request.getEmail(),
                    request.getPassword()
            );

            AppUser admin = AppUser.builder()
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .email(request.getEmail())
                    .passwordHash(passwordEncoder.encode(KERNEL_MANAGED_PASSWORD))
                    .role(UserRole.ROLE_ADMIN)
                    .kernelUserId(loginResult.userId())
                    .build();
            userRepository.save(admin);

            return KernelAuthMapper.toAuthResponse(loginResult, admin);
        } catch (KernelClientException ex) {
            throw new IllegalArgumentException(
                    ex.getMessage() != null ? ex.getMessage() : "Echec inscription administrateur via le kernel"
            );
        }
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
