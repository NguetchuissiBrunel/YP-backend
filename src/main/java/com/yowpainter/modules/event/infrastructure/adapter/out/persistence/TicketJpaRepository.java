package com.yowpainter.modules.event.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.event.domain.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketJpaRepository extends JpaRepository<Ticket, java.util.UUID> {

    java.util.Optional<Ticket> findByQrCodeData(String qrCodeData);
    java.util.Optional<Ticket> findByReservationId(java.util.UUID reservationId);
}
