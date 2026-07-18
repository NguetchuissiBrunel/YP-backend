package com.yowpainter.shared.kernel.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KernelTransactionResponseDto(
    UUID id,
    UUID organizationId,
    String chainCode,
    String transactionType,
    String sourceService,
    String sourceReference,
    String payloadHash,
    String senderFingerprint,
    String transactionHash,
    String status,
    UUID blockId,
    Long blockHeight,
    Instant createdAt,
    Instant minedAt
) {}
