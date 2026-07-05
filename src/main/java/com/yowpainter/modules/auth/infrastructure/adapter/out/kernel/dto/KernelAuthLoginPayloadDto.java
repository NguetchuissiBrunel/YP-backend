package com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto;

public record KernelAuthLoginPayloadDto(String accessToken, Long expiresInSeconds, String mfaToken, String codePreview) {}
