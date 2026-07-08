package com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto;

public record KernelSignUpRequestDto(
        String firstName,
        String lastName,
        String email,
        String username,
        String tenantId,
        String password,
        String accountType
) {
}
