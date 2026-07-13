package com.yowpainter.modules.auth.application.service;

import com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort;
import com.yowpainter.modules.auth.application.port.out.KernelAuthPort;
import com.yowpainter.modules.auth.domain.model.AppUser;
import com.yowpainter.modules.auth.domain.model.UserRole;
import com.yowpainter.modules.auth.domain.port.out.AppUserRepositoryPort;
import com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto.LoginRequest;
import com.yowpainter.shared.kernel.port.KernelAdministrationPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KernelAdminRegistrationServiceTest {

    @Mock
    private KernelAuthPort kernelAuthPort;

    @Mock
    private KernelAdministrationPort kernelAdministrationPort;

    @Mock
    private AppUserRepositoryPort userRepository;

    @Mock
    private ArtistRepositoryPort artistRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private KernelAdminRegistrationService kernelAdminRegistrationService;

    @Test
    void loginAdmin_shouldCreateLocalAdminWhenMissing() {
        UUID kernelUserId = UUID.randomUUID();
        LoginRequest request = LoginRequest.builder()
                .email("landrybrunel1@gmail.com")
                .password("Admin12345!")
                .build();

        when(kernelAuthPort.login("landrybrunel1@gmail.com", "Admin12345!"))
                .thenReturn(loginResult(kernelUserId, "landrybrunel1@gmail.com", "Landry", "Brunel"));
        when(artistRepository.findByEmail("landrybrunel1@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.findByKernelUserId(kernelUserId)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("landrybrunel1@gmail.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        var response = kernelAdminRegistrationService.loginAdmin(request);

        assertThat(response.getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(response.getEmail()).isEqualTo("landrybrunel1@gmail.com");
        assertThat(response.getFirstName()).isEqualTo("Landry");
        verify(userRepository).save(any(AppUser.class));
    }

    @Test
    void loginAdmin_shouldPromoteExistingBuyerToAdmin() {
        UUID kernelUserId = UUID.randomUUID();
        AppUser buyer = AppUser.builder()
                .email("admin237@gmail.com")
                .role(UserRole.ROLE_BUYER)
                .build();
        buyer.setId(UUID.randomUUID());

        when(kernelAuthPort.login("admin237@gmail.com", "Admin12345!"))
                .thenReturn(loginResult(kernelUserId, "admin237@gmail.com", "Admin", "User"));
        when(artistRepository.findByEmail("admin237@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.findByKernelUserId(kernelUserId)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin237@gmail.com")).thenReturn(Optional.of(buyer));
        when(userRepository.save(buyer)).thenReturn(buyer);

        var response = kernelAdminRegistrationService.loginAdmin(LoginRequest.builder()
                .email("admin237@gmail.com")
                .password("Admin12345!")
                .build());

        assertThat(response.getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(buyer.getRole()).isEqualTo(UserRole.ROLE_ADMIN);
        assertThat(buyer.getKernelUserId()).isEqualTo(kernelUserId);
    }

    private KernelAuthPort.KernelLoginResult loginResult(
            UUID userId,
            String email,
            String firstName,
            String lastName
    ) {
        return new KernelAuthPort.KernelLoginResult(
                userId,
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                null,
                email,
                email,
                firstName,
                lastName,
                null,
                "ACTIVE",
                null,
                null,
                0,
                null,
                null,
                null,
                true,
                null,
                false,
                null,
                false,
                null,
                "access-token",
                "refresh-token",
                "Bearer",
                3600,
                List.of(),
                Set.of()
        );
    }
}
