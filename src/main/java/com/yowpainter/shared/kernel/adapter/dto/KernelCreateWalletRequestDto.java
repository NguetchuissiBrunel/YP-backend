package com.yowpainter.shared.kernel.adapter.dto;

import java.util.UUID;

public record KernelCreateWalletRequestDto(
    UUID organizationId,
    String label
) {}
