package com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record KernelContextualSignUpRequestDto(
        @JsonProperty("signUpSelectionToken")
        String selectionToken,
        String contextId,
        String firstName,
        String lastName,
        String email,
        String username,
        String tenantId,
        String password,
        String accountType,
        String businessType,
        Map<String, Object> onboardingData
) {
}
