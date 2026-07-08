package com.yowpainter.modules.auth.infrastructure.adapter.out.kernel.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.UUID;

public record KernelSignUpContextDto(
        UUID tenantId,
        UUID organizationId,
        String organizationCode,
        @JsonAlias("organizationName")
        String shortName,
        @JsonAlias("organizationName")
        String longName,
        String contextId
) {
}
