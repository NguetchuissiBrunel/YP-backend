package com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto;

import java.util.UUID;

public record KernelPasswordResetContextDto(
        String contextId,
        UUID tenantId,
        UUID organizationId,
        UUID userId,
        String principal,
        String displayName
) {
}
