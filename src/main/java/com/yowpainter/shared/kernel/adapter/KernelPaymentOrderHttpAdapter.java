package com.yowpainter.shared.kernel.adapter;

import com.yowpainter.shared.kernel.KernelHttpClient;
import com.yowpainter.shared.kernel.adapter.dto.KernelInitiatePaymentRequestDto;
import com.yowpainter.shared.kernel.adapter.dto.KernelPaymentOrderResponseDto;
import com.yowpainter.shared.kernel.port.KernelPaymentOrderPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adaptateur HTTP vers /api/payments/orders (kernel core, module billing).
 * <p>
 * Ces endpoints wrappent leur reponse dans ApiResponse : on passe donc par
 * post/get standards (et non par les variantes raw reservees a /api/paiement).
 * </p>
 */
@Component
public class KernelPaymentOrderHttpAdapter implements KernelPaymentOrderPort {

    private final KernelHttpClient kernelHttpClient;

    public KernelPaymentOrderHttpAdapter(KernelHttpClient kernelHttpClient) {
        this.kernelHttpClient = kernelHttpClient;
    }

    // On utilise les variantes SANS jeton explicite : KernelHttpClient envoie
    // toujours X-Client-Id / X-Api-Key (auth server-to-server suffisante) et
    // transmet le bearer present dans le RequestContext s'il y en a un.

    @Override
    public KernelPaymentOrderResponseDto initiatePayment(KernelInitiatePaymentRequestDto command, UUID organizationId) {
        return kernelHttpClient.post(
                "/api/payments/orders",
                command,
                KernelPaymentOrderResponseDto.class,
                organizationId
        );
    }

    @Override
    public KernelPaymentOrderResponseDto refreshPayment(String orderId, UUID organizationId) {
        return kernelHttpClient.post(
                "/api/payments/orders/" + orderId + "/refresh",
                null,
                KernelPaymentOrderResponseDto.class,
                organizationId
        );
    }

    @Override
    public KernelPaymentOrderResponseDto getPayment(String orderId, UUID organizationId) {
        return kernelHttpClient.get(
                "/api/payments/orders/" + orderId,
                KernelPaymentOrderResponseDto.class,
                organizationId
        );
    }
}
