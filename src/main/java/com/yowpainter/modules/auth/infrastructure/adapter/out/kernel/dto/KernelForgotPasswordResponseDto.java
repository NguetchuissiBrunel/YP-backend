package com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto;

import java.util.List;
import java.util.UUID;

public record KernelForgotPasswordResponseDto(
        String principal,
        Integer matchingAccountCount,
        String selectionToken,
        Long expiresInSeconds,
        List<KernelPasswordResetContextDto> contexts
) {
}
