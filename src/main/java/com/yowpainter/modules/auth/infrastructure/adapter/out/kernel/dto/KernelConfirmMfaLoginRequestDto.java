package com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto;

public record KernelConfirmMfaLoginRequestDto(String mfaToken, String code) {}
