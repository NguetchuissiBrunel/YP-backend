package com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto;

public record KernelMfaChallengeResponse(
        String nextStep,
        String mfaToken,
        String channel
) {}
