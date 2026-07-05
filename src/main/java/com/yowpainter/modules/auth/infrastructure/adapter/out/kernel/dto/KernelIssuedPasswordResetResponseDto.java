package com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto;

public record KernelIssuedPasswordResetResponseDto(
        String deliveryMode,
        String challengeTokenPreview,
        Long expiresInSeconds
) {
}
