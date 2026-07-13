package com.yowpainter.shared.kernel;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KernelOrganizationResolverTest {

    private static final UUID ORG_ID = UUID.fromString("a002c0ed-cfa0-4321-8118-b26c5c35dbcc");

    @Test
    void firstOrganizationId_shouldParseOrganizationPermission() {
        Optional<UUID> organizationId = KernelOrganizationResolver.firstOrganizationId(List.of(
                "ROLE_ORGANIZATION_ADMIN#ORGANIZATION:" + ORG_ID,
                "settings:read#ORGANIZATION:" + ORG_ID
        ));

        assertThat(organizationId).contains(ORG_ID);
    }

    @Test
    void fromAccessToken_shouldParsePermissionsClaim() {
        String payload = """
                {"permissions":["ROLE_ORGANIZATION_ADMIN#ORGANIZATION:a002c0ed-cfa0-4321-8118-b26c5c35dbcc"]}
                """;
        String token = "header." + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ".signature";

        Optional<UUID> organizationId = KernelOrganizationResolver.fromAccessToken(token);

        assertThat(organizationId).contains(ORG_ID);
    }
}
