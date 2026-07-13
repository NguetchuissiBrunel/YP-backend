package com.yowpainter.modules.event.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.event.domain.model.Reservation;
import com.yowpainter.modules.event.domain.model.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.UUID;

public interface ReservationJpaRepository extends JpaRepository<Reservation, java.util.UUID> {

    @org.springframework.data.jpa.repository.Query("SELECT r FROM Reservation r WHERE r.event.id = :eventId")
    java.util.List<Reservation> findByEventId(@Param("eventId") UUID eventId);
    java.util.List<Reservation> findByStatusAndReservedAtBefore(ReservationStatus status, java.time.LocalDateTime reservedAt);
    java.util.List<Reservation> findByUserId(UUID userId);

    @org.springframework.data.jpa.repository.Query("""
            SELECT r FROM Reservation r
            WHERE r.event.id = :eventId
              AND r.userId = :userId
              AND r.status IN :statuses
            """)
    java.util.Optional<Reservation> findActiveByEventIdAndUserId(
            @Param("eventId") UUID eventId,
            @Param("userId") UUID userId,
            @Param("statuses") Collection<ReservationStatus> statuses
    );
}
