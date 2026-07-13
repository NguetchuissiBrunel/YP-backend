package com.yowpainter.modules.event.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.event.domain.model.Reservation;
import com.yowpainter.modules.event.domain.port.out.ReservationRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReservationRepositoryAdapter implements ReservationRepositoryPort {

    private final ReservationJpaRepository jpaRepository;

    @Override
    public Reservation save(Reservation reservation) {
        return jpaRepository.save(reservation);
    }

    @Override
    public java.util.Optional<Reservation> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public java.util.List<Reservation> findByEventId(UUID eventId) {
        return jpaRepository.findByEventId(eventId);
    }

    @Override
    public java.util.List<Reservation> findByStatusAndReservedAtBefore(com.yowpainter.modules.event.domain.model.ReservationStatus status, java.time.LocalDateTime reservedAt) {
        return jpaRepository.findByStatusAndReservedAtBefore(status, reservedAt);
     }

    @Override
    public java.util.List<Reservation> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId);
    }

    @Override
    public java.util.Optional<Reservation> findActiveByEventIdAndUserId(UUID eventId, UUID userId) {
        return jpaRepository.findActiveByEventIdAndUserId(eventId, userId);
    }
}
