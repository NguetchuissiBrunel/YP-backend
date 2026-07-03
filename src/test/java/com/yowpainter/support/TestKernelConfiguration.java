package com.yowpainter.support;

import com.yowpainter.modules.auth.application.port.out.KernelAuthPort;
import com.yowpainter.shared.kernel.KernelBootstrapAdminSession;
import com.yowpainter.shared.kernel.port.KernelActorPort;
import com.yowpainter.shared.kernel.port.KernelAdministrationPort;
import com.yowpainter.shared.kernel.port.KernelFilePort;
import com.yowpainter.shared.kernel.port.KernelNotificationPort;
import com.yowpainter.shared.kernel.port.KernelOrganizationPort;
import com.yowpainter.shared.kernel.port.KernelProductPort;
import com.yowpainter.shared.kernel.port.KernelSalesPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@Profile("test")
public class TestKernelConfiguration {

    private final Map<String, String> credentials = new ConcurrentHashMap<>();

    @Bean
    @Primary
    KernelAuthPort testKernelAuthPort() {
        return new KernelAuthPort() {
            @Override
            public KernelLoginResult login(String principal, String password) {
                String stored = credentials.get(principal);
                if (stored == null || !stored.equals(password)) {
                    throw new com.yowpainter.shared.kernel.KernelClientException("Invalid credentials", null, null);
                }
                return buildLoginResult(principal);
            }

            @Override
            public KernelLoginResult signUp(SignUpCommand command) {
                credentials.put(command.email(), command.password());
                return buildLoginResult(command.email());
            }

            @Override
            public DiscoverSignUpContextsResult discoverSignUpContexts(String organizationCode) {
                return new DiscoverSignUpContextsResult(
                        "test-selection-token",
                        3600,
                        List.of(new SignUpContext(
                                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                                UUID.randomUUID(),
                                organizationCode,
                                organizationCode,
                                organizationCode,
                                "ctx-" + organizationCode
                        ))
                );
            }

            @Override
            public KernelLoginResult signUpWithContext(ContextualSignUpCommand command) {
                credentials.put(command.email(), command.password());
                return mockLoginResult(command.email(), Set.of("ROLE_BUYER"));
            }

            @Override
            public void requestEmailVerification(String accessToken) {
            }

            @Override
            public KernelLoginResult confirmEmailVerification(String verificationToken) {
                return buildLoginResult("artist@example.com");
            }

            @Override
            public KernelLoginResult refresh(String refreshToken) {
                return mockLoginResult("refreshed@example.com", Set.of("ROLE_BUYER"));
            }

            @Override
            public void logout(String refreshToken) {
            }

            @Override
            public KernelUserProfile me(String accessToken) {
                String email = accessToken.replace("test-token-", "");
                return mockUserProfile(email);
            }

            @Override
            public ForgotPasswordResult forgotPassword(String principal) {
                return new ForgotPasswordResult(
                        principal,
                        1,
                        "selection-token",
                        3600,
                        List.of(new PasswordResetContext(
                                "ctx-1",
                                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                principal,
                                principal
                        ))
                );
            }

            @Override
            public IssuedPasswordResetResult issuePasswordReset(String selectionToken, String contextId) {
                return new IssuedPasswordResetResult("PREVIEW_ONLY", "reset-token-preview", 3600);
            }

            @Override
            public void resetPassword(String resetToken, String newPassword) {
                if (resetToken == null || resetToken.isBlank()) {
                    throw new com.yowpainter.shared.kernel.KernelClientException("Invalid token", null, null);
                }
            }

            private KernelLoginResult buildLoginResult(String email) {
                return mockLoginResult(email, Set.of("ROLE_ARTIST"));
            }

            private KernelLoginResult mockLoginResult(String email, Set<String> roles) {
                UUID userId = UUID.nameUUIDFromBytes(("kernel-user-" + email).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return new KernelLoginResult(
                        userId,
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        UUID.randomUUID(),
                        email,
                        email,
                        null,
                        null,
                        null,
                        "ACTIVE",
                        "COMMERCE",
                        "COMPLETED",
                        0,
                        "INDIVIDUAL",
                        null,
                        null,
                        true,
                        java.time.Instant.now(),
                        false,
                        null,
                        false,
                        null,
                        "test-token-" + email,
                        "refresh-" + email,
                        "Bearer",
                        3600,
                        List.of(),
                        roles
                );
            }

            private KernelUserProfile mockUserProfile(String email) {
                UUID userId = UUID.nameUUIDFromBytes(("kernel-user-" + email).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return new KernelUserProfile(
                        userId,
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        UUID.randomUUID(),
                        email,
                        email,
                        null,
                        null,
                        null,
                        "ACTIVE",
                        "COMMERCE",
                        "COMPLETED",
                        0,
                        "INDIVIDUAL",
                        null,
                        null,
                        true,
                        java.time.Instant.now(),
                        false,
                        null,
                        false,
                        null,
                        List.of()
                );
            }
        };
    }

    @Bean
    @Primary
    KernelBootstrapAdminSession testKernelBootstrapAdminSession() {
        return new KernelBootstrapAdminSession(null, null, null) {
            @Override
            public String requireAccessToken() {
                return "test-bootstrap-admin-token";
            }

            @Override
            public String requireAccessToken(String mfaCode) {
                return requireAccessToken();
            }

            @Override
            public void invalidate() {
            }
        };
    }

    @Bean
    @Primary
    KernelActorPort testKernelActorPort() {
        return (command, accessToken) -> new KernelActorPort.BusinessActorView(
                UUID.randomUUID(),
                UUID.randomUUID(),
                command.code(),
                command.name()
        );
    }

    @Bean
    @Primary
    KernelOrganizationPort testKernelOrganizationPort() {
        return new KernelOrganizationPort() {
            @Override
            public OrganizationView createOrganization(CreateOrganizationCommand command, String accessToken) {
                return new OrganizationView(
                        UUID.randomUUID(),
                        command.businessActorId(),
                        command.code(),
                        command.shortName(),
                        command.longName()
                );
            }

            @Override
            public java.util.Optional<UUID> findOrganizationIdByCode(String code, String accessToken) {
                return java.util.Optional.empty();
            }

            @Override
            public void approveOrganization(UUID organizationId, String reason, String adminAccessToken) {
            }

            @Override
            public void applyCommercialPlan(UUID organizationId, String planCode, String accessToken) {
            }
        };
    }

    @Bean
    @Primary
    KernelProductPort testKernelProductPort() {
        return new KernelProductPort() {
            @Override
            public ProductView createProduct(CreateProductCommand command, String accessToken) {
                return new ProductView(
                        UUID.randomUUID(),
                        command.organizationId(),
                        command.sku(),
                        command.name(),
                        command.description(),
                        command.unitPrice(),
                        command.currency(),
                        "ACTIVE"
                );
            }

            @Override
            public ProductView getProduct(UUID productId, String accessToken) {
                return new ProductView(productId, UUID.randomUUID(), "SKU", "Test", null, BigDecimal.TEN, "XAF", "ACTIVE");
            }

            @Override
            public List<ProductView> listProducts(UUID organizationId, String accessToken) {
                return List.of();
            }
        };
    }

    @Bean
    @Primary
    KernelSalesPort testKernelSalesPort() {
        return new KernelSalesPort() {
            @Override
            public SalesOrderView createOrder(CreateSalesOrderCommand command, String accessToken) {
                BigDecimal total = command.unitPrice().multiply(command.quantity());
                return new SalesOrderView(
                        UUID.randomUUID(),
                        command.organizationId(),
                        command.productId(),
                        command.quantity(),
                        command.unitPrice(),
                        total,
                        command.currency(),
                        "PENDING"
                );
            }

            @Override
            public SalesOrderView getOrder(UUID orderId, String accessToken) {
                return new SalesOrderView(orderId, UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, "XAF", "PENDING");
            }

            @Override
            public SalesOrderView cancelOrder(UUID orderId, String accessToken) {
                return getOrder(orderId, accessToken);
            }

            @Override
            public SalesOrderView confirmOrder(UUID orderId, String accessToken) {
                return getOrder(orderId, accessToken);
            }

            @Override
            public List<SalesOrderView> listOrders(UUID organizationId, String accessToken) {
                return List.of();
            }
        };
    }

    @Bean
    @Primary
    KernelAdministrationPort testKernelAdministrationPort() {
        UUID tenantAdminRoleId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        return new KernelAdministrationPort() {
            @Override
            public List<AdministrativeRoleView> provisionDefaultRoles() {
                return List.of(new AdministrativeRoleView(tenantAdminRoleId, "GENERAL_ADMIN", "General Administrator"));
            }

            @Override
            public List<AdministrativeRoleView> listRoles() {
                return List.of(new AdministrativeRoleView(tenantAdminRoleId, "GENERAL_ADMIN", "General Administrator"));
            }

            @Override
            public void assignTenantAdminRole(UUID userId, UUID roleId) {
            }

            @Override
            public void grantOrganizationWriteAccess(UUID userId) {
            }

            @Override
            public void provisionDefaultRolesForOrganization(UUID organizationId) {
            }

            @Override
            public void grantOrganizationAdminRole(UUID userId, UUID organizationId) {
            }
        };
    }

    @Bean
    @Primary
    KernelNotificationPort testKernelNotificationPort() {
        return new KernelNotificationPort() {
            @Override
            public void send(SendNotificationCommand command, String accessToken) {
            }

            @Override
            public List<DeliveryView> listDeliveries(UUID organizationId, String accessToken) {
                return List.of();
            }
        };
    }

    @Bean
    @Primary
    KernelFilePort testKernelFilePort() {
        return new KernelFilePort() {
            @Override
            public FileView upload(UploadFileCommand command, String accessToken) {
                return new FileView(
                        UUID.randomUUID(),
                        command.fileName(),
                        command.contentType(),
                        "http://localhost/files/" + UUID.randomUUID()
                );
            }

            @Override
            public DownloadFileView download(UUID fileId, String accessToken) {
                return new DownloadFileView(
                        "mock-file-content".getBytes(StandardCharsets.UTF_8),
                        "text/plain",
                        "attachment; filename=\"mock.txt\""
                );
            }

            @Override
            public DownloadStreamView downloadStream(UUID fileId, org.springframework.http.HttpHeaders clientHeaders, String accessToken) {
                org.springframework.http.HttpHeaders responseHeaders = new org.springframework.http.HttpHeaders();
                responseHeaders.setContentType(org.springframework.http.MediaType.TEXT_PLAIN);
                responseHeaders.setContentDisposition(org.springframework.http.ContentDisposition.attachment().filename("mock.txt").build());
                return new DownloadStreamView(
                        new org.springframework.core.io.ByteArrayResource("mock-file-content".getBytes(StandardCharsets.UTF_8)),
                        org.springframework.http.HttpStatus.OK,
                        responseHeaders
                );
            }
        };
    }
}
