package com.yowpainter.shared.kernel.port;

import com.yowpainter.shared.kernel.adapter.dto.*;
import java.util.List;
import java.util.UUID;

public interface KernelWalletPort {
    KernelGeneratedWalletResponseDto createWallet(UUID organizationId, String label, String accessToken);
    List<KernelWalletResponseDto> listWallets(UUID organizationId, String accessToken);
    KernelTransactionResponseDto submitTransaction(KernelSubmitTransactionRequestDto command, String accessToken);
    String signPayload(String privateKey, String payload, String accessToken);
    List<KernelTransactionResponseDto> listTransactions(UUID organizationId, String chainCode, String accessToken);
    KernelChainValidationReportDto validateChain(UUID organizationId, String chainCode, String accessToken);
}
