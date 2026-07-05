package com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto;

import java.util.Map;

public record KernelContextualSignUpRequestDto(
        String selectionToken,
        String contextId,
        String firstName,
        String lastName,
        String email,
        String password,
        String accountType,
        String businessType,
        Map<String, Object> onboardingData
) {
}
