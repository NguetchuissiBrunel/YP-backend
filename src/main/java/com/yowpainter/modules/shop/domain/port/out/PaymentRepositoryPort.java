package com.yowpainter.modules.shop.domain.port.out;

import com.yowpainter.modules.shop.domain.model.Payment;
import java.util.UUID;

public interface PaymentRepositoryPort {

    Payment save(Payment payment);
    java.util.Optional<Payment> findByReferenceId(UUID referenceId);
    java.util.List<Payment> findByUserIdOrderByCreatedAtDesc(UUID userId);
    java.util.List<Payment> findByStatusAndCreatedAtBefore(com.yowpainter.modules.shop.domain.model.PaymentStatus status, java.time.LocalDateTime createdAt);
}
