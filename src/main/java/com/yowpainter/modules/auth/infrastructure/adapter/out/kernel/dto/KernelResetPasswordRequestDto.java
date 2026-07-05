package com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto;

public record KernelResetPasswordRequestDto(String resetToken, String newPassword) {
}
