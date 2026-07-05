package com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto;

import java.util.List;

public record KernelDiscoverSignUpContextsResponseDto(
        String selectionToken,
        Long expiresInSeconds,
        List<KernelSignUpContextDto> contexts
) {
}
