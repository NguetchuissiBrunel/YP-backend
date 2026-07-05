package com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto;

public record KernelOtpChallengePayloadDto(String challengeToken, String codePreview) {}
