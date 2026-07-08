package com.yowpainter.modules.event.domain.port.out;

import com.yowpainter.modules.event.domain.model.Reservation;
import java.util.UUID;

public interface ReservationRepositoryPort {

    Reservation save(Reservation reservation);
    java.util.Optional<Reservation> findById(UUID id);
    java.util.List<Reservation> findByEventId(UUID eventId);
    java.util.List<Reservation> findByStatusAndReservedAtBefore(com.yowpainter.modules.event.domain.model.ReservationStatus status, java.time.LocalDateTime reservedAt);
    java.util.List<Reservation> findByUserId(UUID userId);
}
