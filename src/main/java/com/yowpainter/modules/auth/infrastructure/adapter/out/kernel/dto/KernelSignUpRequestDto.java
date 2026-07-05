package com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto;

import java.util.Map;

public record KernelSignUpRequestDto(
        String firstName,
        String lastName,
        String email,
        String password,
        String accountType
) {
}
