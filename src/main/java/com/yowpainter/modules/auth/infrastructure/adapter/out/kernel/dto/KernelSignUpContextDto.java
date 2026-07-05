package com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto;

import java.util.UUID;

public record KernelSignUpContextDto(
        UUID tenantId,
        UUID organizationId,
        String organizationCode,
        String shortName,
        String longName,
        String contextId
) {
}
