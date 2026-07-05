package com.yowpainter.modules.shop.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.shop.domain.model.Payment;
import com.yowpainter.modules.shop.domain.port.out.PaymentRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentRepositoryAdapter implements PaymentRepositoryPort {

    private final PaymentJpaRepository jpaRepository;

    @Override
    public Payment save(Payment payment) {
        return jpaRepository.save(payment);
    }

    @Override
    public java.util.Optional<Payment> findByReferenceId(UUID referenceId) {
        return jpaRepository.findByReferenceId(referenceId);
    }

    @Override
    public java.util.List<Payment> findByUserIdOrderByCreatedAtDesc(UUID userId) {
        return jpaRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public java.util.List<Payment> findByStatusAndCreatedAtBefore(com.yowpainter.modules.shop.domain.model.PaymentStatus status, java.time.LocalDateTime createdAt) {
        return jpaRepository.findByStatusAndCreatedAtBefore(status, createdAt);
    }
}
