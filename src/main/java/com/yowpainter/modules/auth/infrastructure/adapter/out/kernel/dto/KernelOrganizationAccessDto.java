package com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto;

import java.util.List;
import java.util.UUID;

public record KernelOrganizationAccessDto(
        UUID organizationId,
        String organizationCode,
        String shortName,
        String displayName,
        List<String> services
) {
}
