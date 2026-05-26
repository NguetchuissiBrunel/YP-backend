package com.yowpainter.modules.auth.application.service;

import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.artist.domain.port.out.ArtistRepositoryPort;
import com.yowpainter.modules.auth.domain.model.AppUser;
import com.yowpainter.modules.auth.domain.model.RefreshToken;
import com.yowpainter.modules.auth.domain.model.UserRole;
import com.yowpainter.modules.auth.domain.port.out.AppUserRepositoryPort;
import com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto.*;
import com.yowpainter.shared.security.JwtService;
import com.yowpainter.shared.tenant.TenantProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private TenantProvisioningService tenantProvisioningService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    private AppUser buyer;
    private Artist artist;
    private AppUser admin;
    private RefreshToken refreshToken;

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

        admin = AppUser.builder()
                .firstName("Admin")
                .lastName("User")
                .email("admin@example.com")
                .passwordHash("hashed_password")
                .role(UserRole.ROLE_ADMIN)
                .build();
        admin.setId(UUID.randomUUID());

        refreshToken = RefreshToken.builder()
                .token("refresh-token-123")
                .user(buyer)
                .expiryDate(Instant.now().plusSeconds(7 * 24 * 3600))
                .build();
    }

    @Test
    void getAvailableRoles_shouldReturnArtistAndBuyer() {
        List<String> roles = authService.getAvailableRoles();
        assertThat(roles).containsExactlyInAnyOrder("ROLE_ARTIST", "ROLE_BUYER");
    }

    @Test
    void processForgotPassword_whenUserExists_shouldGenerateTokenAndSendEmail() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(buyer));
        when(userRepository.save(any(AppUser.class))).thenReturn(buyer);

        authService.processForgotPassword("alice@example.com");

        assertThat(buyer.getResetToken()).isNotNull();
        assertThat(buyer.getResetTokenExpiry()).isAfter(LocalDateTime.now());
        verify(userRepository).save(buyer);
        verify(emailService).sendPasswordResetEmail(eq("alice@example.com"), anyString());
    }

    @Test
    void processForgotPassword_whenUserDoesNotExist_shouldThrowException() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.processForgotPassword("missing@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Utilisateur non trouvé avec cet e-mail");

        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void resetPassword_withValidToken_shouldUpdatePassword() {
        buyer.setResetToken("reset-token-123");
        buyer.setResetTokenExpiry(LocalDateTime.now().plusMinutes(30));

        when(userRepository.findByResetToken("reset-token-123")).thenReturn(Optional.of(buyer));
        when(passwordEncoder.encode("new_password")).thenReturn("new_hashed_password");
        when(userRepository.save(any(AppUser.class))).thenReturn(buyer);

        authService.resetPassword("reset-token-123", "new_password");

        assertThat(buyer.getPasswordHash()).isEqualTo("new_hashed_password");
        assertThat(buyer.getResetToken()).isNull();
        assertThat(buyer.getResetTokenExpiry()).isNull();
        verify(userRepository).save(buyer);
    }

    @Test
    void resetPassword_withExpiredToken_shouldThrowException() {
        buyer.setResetToken("reset-token-123");
        buyer.setResetTokenExpiry(LocalDateTime.now().minusMinutes(5));

        when(userRepository.findByResetToken("reset-token-123")).thenReturn(Optional.of(buyer));

        assertThatThrownBy(() -> authService.resetPassword("reset-token-123", "new_password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Le jeton de réinitialisation a expiré");

        verify(userRepository, never()).save(any(AppUser.class));
    }

    @Test
    void registerAdmin_shouldCreateAdminAndGenerateTokens() {
        AdminRegisterRequest request = AdminRegisterRequest.builder()
                .firstName("Admin")
                .lastName("User")
                .email("admin@example.com")
                .password("adminpassword")
                .build();

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("adminpassword")).thenReturn("hashed_password");
        when(userRepository.save(any(AppUser.class))).thenReturn(admin);
        when(jwtService.generateToken(admin, "public")).thenReturn("jwt-access-token");
        when(refreshTokenService.createRefreshToken(admin.getId())).thenReturn(refreshToken);

        AuthResponse response = authService.registerAdmin(request);

        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("admin@example.com");
        assertThat(response.getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(response.getAccessToken()).isEqualTo("jwt-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token-123");
        verify(userRepository).save(any(AppUser.class));
    }

    @Test
    void registerAdmin_whenEmailExists_shouldThrowException() {
        AdminRegisterRequest request = AdminRegisterRequest.builder()
                .email("admin@example.com")
                .build();

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> authService.registerAdmin(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Un administrateur avec cet email existe deja");
    }

    @Test
    void register_withBuyerRole_shouldCreateBuyerAndGenerateTokens() {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .password("password123")
                .role(UserRole.ROLE_BUYER)
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hashed_password");
        when(userRepository.save(any(AppUser.class))).thenReturn(buyer);
        when(jwtService.generateToken(buyer, "public")).thenReturn("jwt-access-token");
        when(refreshTokenService.createRefreshToken(buyer.getId())).thenReturn(refreshToken);

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.getRole()).isEqualTo("ROLE_BUYER");
        assertThat(response.getTenantId()).isEqualTo("public");
        assertThat(response.getAccessToken()).isEqualTo("jwt-access-token");
        verify(userRepository).save(any(AppUser.class));
        verify(artistRepository, never()).save(any(Artist.class));
    }

    @Test
    void register_withArtistRole_shouldCreateArtistProvisionSchemaAndGenerateTokens() {
        RegisterRequest request = RegisterRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .password("password123")
                .role(UserRole.ROLE_ARTIST)
                .artistName("John's Studio")
                .slug("johns-studio")
                .build();

        when(userRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.empty());
        when(artistRepository.findBySlug("johns-studio")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hashed_password");
        when(artistRepository.save(any(Artist.class))).thenReturn(artist);
        when(jwtService.generateToken(artist, "johns-studio")).thenReturn("jwt-access-token");
        when(refreshTokenService.createRefreshToken(artist.getId())).thenReturn(refreshToken);

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.getRole()).isEqualTo("ROLE_ARTIST");
        assertThat(response.getTenantId()).isEqualTo("johns-studio");
        assertThat(response.getArtistName()).isEqualTo("John's Studio");

        verify(artistRepository).save(any(Artist.class));
        verify(tenantProvisioningService).provisionTenant("johns-studio");
    }

    @Test
    void register_whenRoleIsAdmin_shouldThrowException() {
        RegisterRequest request = RegisterRequest.builder()
                .role(UserRole.ROLE_ADMIN)
                .build();

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Le role ADMIN ne peut pas etre choisi publiquement");
    }

    @Test
    void login_forArtist_shouldAuthenticateAndReturnTokens() {
        LoginRequest request = LoginRequest.builder()
                .email("john.doe@example.com")
                .password("password123")
                .build();

        when(userRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(artist));
        when(jwtService.generateToken(artist, "johns-studio")).thenReturn("jwt-access-token");
        when(refreshTokenService.createRefreshToken(artist.getId())).thenReturn(refreshToken);

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(response.getTenantId()).isEqualTo("johns-studio");
        assertThat(response.getAccessToken()).isEqualTo("jwt-access-token");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void refreshToken_withValidToken_shouldGenerateNewAccessToken() {
        when(refreshTokenService.findByToken("refresh-token-123")).thenReturn(Optional.of(refreshToken));
        when(refreshTokenService.verifyExpiration(refreshToken)).thenReturn(refreshToken);
        when(jwtService.generateToken(buyer, "public")).thenReturn("new-jwt-access-token");

        AuthResponse response = authService.refreshToken("refresh-token-123");

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("new-jwt-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token-123");
    }

    @Test
    void logout_shouldDeleteUserRefreshToken() {
        authService.logout(buyer);
        verify(refreshTokenService).deleteByUserId(buyer.getId());
    }
}
