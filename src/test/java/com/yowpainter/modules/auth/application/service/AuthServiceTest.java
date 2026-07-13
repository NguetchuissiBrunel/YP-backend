package com.yowpainter.modules.auth.application.service;

import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort;
import com.yowpainter.modules.auth.application.port.out.KernelAuthPort;
import com.yowpainter.modules.auth.domain.model.AppUser;
import com.yowpainter.modules.auth.domain.model.UserRole;
import com.yowpainter.modules.auth.domain.port.out.AppUserRepositoryPort;
import com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto.*;
import org.junit.jupiter.api.BeforeEach;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private AppUserRepositoryPort userRepository;

    @Mock
    private ArtistRepositoryPort artistRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private EmailService emailService;

    @Mock
    private KernelAuthPort kernelAuthPort;

    @Mock
    private KernelArtistRegistrationService kernelArtistRegistrationService;

    @Mock
    private KernelBuyerRegistrationService kernelBuyerRegistrationService;

    @Mock
    private KernelAdminRegistrationService kernelAdminRegistrationService;

    @Mock
    private com.yowpainter.shared.tenant.TenantMigrationService tenantMigrationService;

    @Mock
    private com.yowpainter.shared.kernel.KernelBootstrapAdminSession kernelBootstrapAdminSession;

    @Mock
    private com.yowpainter.shared.kernel.port.KernelFilePort kernelFilePort;

    @InjectMocks
    private AuthService authService;

    private AppUser buyer;
    private Artist artist;

    @BeforeEach
    void setUp() {
        buyer = AppUser.builder()
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .passwordHash("hashed_password")
                .role(UserRole.ROLE_BUYER)
                .build();
        buyer.setId(UUID.randomUUID());

        artist = Artist.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .passwordHash("hashed_password")
                .role(UserRole.ROLE_ARTIST)
                .artistName("John's Studio")
                .slug("johns-studio")
                .build();
        artist.setId(UUID.randomUUID());
    }

    @Test
    void getAvailableRoles_shouldReturnArtistAndBuyer() {
        List<String> roles = authService.getAvailableRoles();
        assertThat(roles).containsExactlyInAnyOrder("ROLE_ARTIST", "ROLE_BUYER");
    }

    @Test
    void processForgotPassword_shouldDelegateToKernelAndSendPreviewEmail() {
        when(kernelAuthPort.forgotPassword("alice@example.com"))
                .thenReturn(new KernelAuthPort.ForgotPasswordResult(
                        "alice@example.com",
                        1,
                        "selection-token",
                        3600,
                        List.of(new KernelAuthPort.PasswordResetContext(
                                "ctx-1", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                                "alice", "alice@example.com"
                        ))
                ));
        when(kernelAuthPort.issuePasswordReset("selection-token", "ctx-1"))
                .thenReturn(new KernelAuthPort.IssuedPasswordResetResult("PREVIEW_ONLY", "reset-token", 3600));

        authService.processForgotPassword("alice@example.com");

        verify(kernelAuthPort).forgotPassword("alice@example.com");
        verify(kernelAuthPort).issuePasswordReset("selection-token", "ctx-1");
        verify(emailService).sendPasswordResetEmail("alice@example.com", "reset-token");
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginAdmin_shouldDelegateToKernelAdminRegistration() {
        LoginRequest request = LoginRequest.builder()
                .email("admin@example.com")
                .password("secret")
                .build();
        AuthResponse expected = AuthResponse.builder().accessToken("admin-token").role("ROLE_ADMIN").build();
        when(kernelAdminRegistrationService.loginAdmin(request)).thenReturn(expected);

        AuthResponse response = authService.loginAdmin(request);

        assertThat(response).isEqualTo(expected);
        verify(kernelAdminRegistrationService).loginAdmin(request);
    }

    @Test
    void registerAdmin_shouldDelegateToKernelAdminRegistration() {
        AdminRegisterRequest request = AdminRegisterRequest.builder()
                .email("admin@example.com")
                .build();
        AuthResponse expected = AuthResponse.builder().accessToken("admin-token").role("ROLE_ADMIN").build();
        when(kernelAdminRegistrationService.registerAdmin(request)).thenReturn(expected);

        AuthResponse response = authService.registerAdmin(request);

        assertThat(response).isEqualTo(expected);
        verify(kernelAdminRegistrationService).registerAdmin(request);
    }

    @Test
    void register_withArtistRole_shouldDelegateToKernelArtistRegistration() {
        RegisterRequest request = RegisterRequest.builder()
                .role(UserRole.ROLE_ARTIST)
                .email("john.doe@example.com")
                .build();
        AuthResponse expected = AuthResponse.builder().accessToken("token").build();
        when(kernelArtistRegistrationService.registerArtist(request)).thenReturn(expected);

        AuthResponse response = authService.register(request);

        assertThat(response).isEqualTo(expected);
        verify(kernelArtistRegistrationService).registerArtist(request);
    }

    @Test
    void register_withBuyerRole_shouldDelegateToKernelBuyerRegistration() {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .password("password123")
                .role(UserRole.ROLE_BUYER)
                .build();

        AuthResponse expected = AuthResponse.builder()
                .email("alice@example.com")
                .registrationStatus("PENDING_EMAIL")
                .message("Inscription enregistree. Un e-mail de verification vous a ete envoye pour activer votre compte.")
                .build();
        when(kernelBuyerRegistrationService.registerBuyer(request)).thenReturn(expected);

        AuthResponse response = authService.register(request);

        assertThat(response).isEqualTo(expected);
        verify(kernelBuyerRegistrationService).registerBuyer(request);
    }

    @Test
    void login_shouldCreateBuyerProfileWhenLocalProfileMissing() {
        LoginRequest request = LoginRequest.builder()
                .email("alice@example.com")
                .password("password123")
                .build();

        when(kernelAuthPort.login("alice@example.com", "password123"))
                .thenReturn(mockLoginResult(
                        UUID.randomUUID(),
                        "alice",
                        "alice@example.com",
                        "access",
                        "refresh",
                        Set.of("ROLE_BUYER"),
                        "PROSPECT"
                ));
        when(artistRepository.findByKernelUserId(any())).thenReturn(Optional.empty());
        when(userRepository.findByKernelUserId(any())).thenReturn(Optional.empty());
        when(artistRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("{KERNEL_MANAGED}")).thenReturn("hashed");
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        AuthResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("access");
        assertThat(response.getRole()).isEqualTo("ROLE_BUYER");
        verify(userRepository).save(any(AppUser.class));
    }

    @Test
    void login_shouldCreateArtistProfileWhenLocalProfileMissing() {
        LoginRequest request = LoginRequest.builder()
                .email("john.doe@example.com")
                .password("password123")
                .build();

        when(kernelAuthPort.login("john.doe@example.com", "password123"))
                .thenReturn(mockLoginResult(
                        UUID.randomUUID(),
                        "john",
                        "john.doe@example.com",
                        "access",
                        "refresh",
                        Set.of("ROLE_ARTIST"),
                        "BUSINESS"
                ));
        when(artistRepository.findByKernelUserId(any())).thenReturn(Optional.empty());
        when(userRepository.findByKernelUserId(any())).thenReturn(Optional.empty());
        when(artistRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.empty());
        when(artistRepository.findBySlug(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode("{KERNEL_MANAGED}")).thenReturn("hashed");
        when(artistRepository.save(any(Artist.class))).thenAnswer(invocation -> {
            Artist saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        AuthResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("access");
        assertThat(response.getRole()).isEqualTo("ROLE_ARTIST");
        verify(artistRepository).save(any(Artist.class));
    }

    @Test
    void login_shouldDelegateToKernel() {
        LoginRequest request = LoginRequest.builder()
                .email("john.doe@example.com")
                .password("password123")
                .build();

        when(kernelAuthPort.login("john.doe@example.com", "password123"))
                .thenReturn(mockLoginResult(UUID.randomUUID(), "john", "john.doe@example.com", "access", "refresh", Set.of("ROLE_ARTIST"), "BUSINESS"));
        when(artistRepository.findByKernelUserId(any())).thenReturn(Optional.of(artist));
        when(artistRepository.save(any(Artist.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("access");
        assertThat(response.getArtistName()).isEqualTo("John's Studio");
    }

    @Test
    void refreshToken_shouldDelegateToKernel() {
        when(kernelAuthPort.refresh("refresh-token"))
                .thenReturn(mockLoginResult(UUID.randomUUID(), "refreshed", "refreshed@example.com", "new-access", "refresh-token", Set.of("ROLE_BUYER"), "PROSPECT"));

        AuthResponse response = authService.refreshToken("refresh-token");

        assertThat(response.getAccessToken()).isEqualTo("new-access");
    }

    @Test
    void logout_shouldDeleteUserRefreshToken() {
        authService.logout(buyer);
        verify(refreshTokenService).deleteByUserId(buyer.getId());
    }

    private KernelAuthPort.KernelLoginResult mockLoginResult(
            UUID userId,
            String username,
            String email,
            String accessToken,
            String refreshToken,
            Set<String> roles,
            String actorType
    ) {
        return new KernelAuthPort.KernelLoginResult(
                userId,
                UUID.randomUUID(),
                "BUSINESS".equalsIgnoreCase(actorType) ? UUID.randomUUID() : null,
                username,
                email,
                null,
                null,
                null,
                "ACTIVE",
                "COMMERCE",
                "COMPLETED",
                0,
                actorType,
                null,
                null,
                true,
                java.time.Instant.now(),
                false,
                null,
                false,
                null,
                accessToken,
                refreshToken,
                "Bearer",
                3600,
                List.of(),
                roles
        );
    }
}
