package com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto;

public record KernelMfaConfirmRequest(
        String mfaToken,
        String code
) {}
