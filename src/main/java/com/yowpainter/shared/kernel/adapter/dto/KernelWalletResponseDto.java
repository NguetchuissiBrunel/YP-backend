package com.yowpainter.shared.kernel.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KernelWalletResponseDto(
    UUID id,
    UUID organizationId,
    String label,
    String publicKey,
    String fingerprint,
    Instant createdAt,
    boolean active
) {}
