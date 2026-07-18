package com.yowpainter.shared.kernel.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KernelChainValidationReportDto(
    String chainCode,
    boolean valid,
    int blockCount,
    long transactionCount,
    String latestBlockHash,
    List<String> validationErrors
) {}
