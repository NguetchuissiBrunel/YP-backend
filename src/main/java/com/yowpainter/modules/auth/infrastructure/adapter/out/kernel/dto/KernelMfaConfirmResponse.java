package com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto;

public record KernelMfaConfirmResponse(
        String accessToken,
        long expiresInSeconds
) {}
