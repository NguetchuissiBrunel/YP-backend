package com.yowpainter.shared.kernel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowpainter.modules.auth.application.port.out.KernelAuthPort;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KernelOrganizationResolver {

    private static final Pattern ORGANIZATION_PERMISSION =
            Pattern.compile("#ORGANIZATION:([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private KernelOrganizationResolver() {
    }

    public static Optional<UUID> firstOrganizationId(Collection<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return Optional.empty();
        }
        for (String scope : scopes) {
            Optional<UUID> parsed = parseOrganizationId(scope);
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        return Optional.empty();
    }

    public static Optional<UUID> firstOrganizationIdFromAccesses(
            List<KernelAuthPort.KernelOrganizationAccess> organizations
    ) {
        if (organizations == null || organizations.isEmpty()) {
            return Optional.empty();
        }
        UUID organizationId = organizations.get(0).organizationId();
        return organizationId != null ? Optional.of(organizationId) : Optional.empty();
    }

    public static Optional<UUID> fromAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return Optional.empty();
        }
        String[] parts = accessToken.split("\\.");
        if (parts.length < 2) {
            return Optional.empty();
        }
        try {
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode payload = OBJECT_MAPPER.readTree(payloadJson);
            List<String> scopes = new ArrayList<>();
            addJsonArray(payload.get("permissions"), scopes);
            addJsonArray(payload.get("authorities"), scopes);
            return firstOrganizationId(scopes);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public static Optional<UUID> resolveOrganizationId(
            KernelAuthPort.KernelLoginResult loginResult,
            KernelAuthPort.KernelUserProfile profile
    ) {
        Optional<UUID> fromLoginOrganizations = firstOrganizationIdFromAccesses(loginResult.organizations());
        if (fromLoginOrganizations.isPresent()) {
            return fromLoginOrganizations;
        }
        if (profile != null) {
            Optional<UUID> fromProfileOrganizations = firstOrganizationIdFromAccesses(profile.organizations());
            if (fromProfileOrganizations.isPresent()) {
                return fromProfileOrganizations;
            }
        }
        Optional<UUID> fromAuthorities = firstOrganizationId(loginResult.authorities());
        if (fromAuthorities.isPresent()) {
            return fromAuthorities;
        }
        return fromAccessToken(loginResult.accessToken());
    }

    private static void addJsonArray(JsonNode node, List<String> target) {
        if (node == null || !node.isArray()) {
            return;
        }
        node.forEach(item -> {
            if (item.isTextual()) {
                target.add(item.asText());
            }
        });
    }

    private static Optional<UUID> parseOrganizationId(String scope) {
        if (scope == null || scope.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = ORGANIZATION_PERMISSION.matcher(scope);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(matcher.group(1)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
