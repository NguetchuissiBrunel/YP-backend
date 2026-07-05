package com.yowpainter.modules.shop.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.shop.domain.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PaymentJpaRepository extends JpaRepository<Payment, java.util.UUID> {

    java.util.Optional<Payment> findByReferenceId(UUID referenceId);
    java.util.List<Payment> findByUserIdOrderByCreatedAtDesc(UUID userId);
    java.util.List<Payment> findByStatusAndCreatedAtBefore(com.yowpainter.modules.shop.domain.model.PaymentStatus status, java.time.LocalDateTime createdAt);
}
