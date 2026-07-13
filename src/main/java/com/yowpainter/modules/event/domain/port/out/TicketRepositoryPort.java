package com.yowpainter.modules.event.domain.port.out;

import com.yowpainter.modules.event.domain.model.Ticket;

public interface TicketRepositoryPort {

    Ticket save(Ticket ticket);
    void delete(Ticket ticket);
    java.util.Optional<Ticket> findByQrCodeData(String qrCodeData);
    java.util.Optional<Ticket> findByReservationId(java.util.UUID reservationId);
}
