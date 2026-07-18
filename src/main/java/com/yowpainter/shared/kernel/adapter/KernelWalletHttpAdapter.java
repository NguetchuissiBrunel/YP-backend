package com.yowpainter.shared.kernel.adapter;

import com.yowpainter.shared.kernel.KernelHttpClient;
import com.yowpainter.shared.kernel.adapter.dto.*;
import com.yowpainter.shared.kernel.port.KernelWalletPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class KernelWalletHttpAdapter implements KernelWalletPort {

    private final KernelHttpClient kernelHttpClient;

    public KernelWalletHttpAdapter(KernelHttpClient kernelHttpClient) {
        this.kernelHttpClient = kernelHttpClient;
    }

    @Override
    public KernelGeneratedWalletResponseDto createWallet(UUID organizationId, String label, String accessToken) {
        return kernelHttpClient.post(
                "/api/v1/blockchain/wallets",
                new KernelCreateWalletRequestDto(organizationId, label),
                KernelGeneratedWalletResponseDto.class,
                organizationId,
                accessToken
        );
    }

    @Override
    public List<KernelWalletResponseDto> listWallets(UUID organizationId, String accessToken) {
        return kernelHttpClient.getListWithQuery(
                "/api/v1/blockchain/wallets",
                Map.of("organizationId", organizationId.toString()),
                KernelWalletResponseDto.class,
                organizationId,
                accessToken
        );
    }

    @Override
    public KernelTransactionResponseDto submitTransaction(KernelSubmitTransactionRequestDto command, String accessToken) {
        return kernelHttpClient.post(
                "/api/v1/blockchain/transactions",
                command,
                KernelTransactionResponseDto.class,
                command.organizationId(),
                accessToken
        );
    }

    @Override
    public String signPayload(String privateKey, String payload, String accessToken) {
        SignatureResponse response = kernelHttpClient.post(
                "/api/v1/blockchain/crypto/sign",
                new SignPayloadRequest(privateKey, payload),
                SignatureResponse.class,
                null,
                accessToken
        );
        return response.signature();
    }

    private record SignPayloadRequest(String privateKey, String payload) {}
    private record SignatureResponse(String signature) {}


    @Override
    public List<KernelTransactionResponseDto> listTransactions(UUID organizationId, String chainCode, String accessToken) {
        Map<String, String> params = (chainCode != null)
                ? Map.of("organizationId", organizationId.toString(), "chainCode", chainCode)
                : Map.of("organizationId", organizationId.toString());
        return kernelHttpClient.getListWithQuery(
                "/api/v1/blockchain/transactions",
                params,
                KernelTransactionResponseDto.class,
                organizationId,
                accessToken
        );
    }

    @Override
    public KernelChainValidationReportDto validateChain(UUID organizationId, String chainCode, String accessToken) {
        Map<String, String> params = (chainCode != null)
                ? Map.of("organizationId", organizationId.toString(), "chainCode", chainCode)
                : Map.of("organizationId", organizationId.toString());
        return kernelHttpClient.getWithQuery(
                "/api/v1/blockchain/validate",
                params,
                KernelChainValidationReportDto.class,
                organizationId,
                accessToken
        );
    }
}
