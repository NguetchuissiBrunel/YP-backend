package com.yowpainter.modules.event.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.event.domain.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ReservationJpaRepository extends JpaRepository<Reservation, java.util.UUID> {

    @org.springframework.data.jpa.repository.Query("SELECT r FROM Reservation r WHERE r.event.id = :eventId")
    java.util.List<Reservation> findByEventId(@org.springframework.data.repository.query.Param("eventId") UUID eventId);
    java.util.List<Reservation> findByStatusAndReservedAtBefore(com.yowpainter.modules.event.domain.model.ReservationStatus status, java.time.LocalDateTime reservedAt);
    java.util.List<Reservation> findByUserId(UUID userId);

    @org.springframework.data.jpa.repository.Query("""
            SELECT r FROM Reservation r
            WHERE r.event.id = :eventId
              AND r.userId = :userId
              AND r.status IN (com.yowpainter.modules.event.domain.model.ReservationStatus.PENDING,
                               com.yowpainter.modules.event.domain.model.ReservationStatus.CONFIRMED)
            """)
    java.util.Optional<Reservation> findActiveByEventIdAndUserId(
            @org.springframework.data.repository.query.Param("eventId") UUID eventId,
            @org.springframework.data.repository.query.Param("userId") UUID userId
    );
}
