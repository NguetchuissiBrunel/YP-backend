package com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto;

public record KernelConfirmMfaEnableRequestDto(String challengeToken, String code) {}
