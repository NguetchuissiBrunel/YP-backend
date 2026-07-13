package com.yowpainter.modules.event.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.event.domain.model.Ticket;
import com.yowpainter.modules.event.domain.port.out.TicketRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class TicketRepositoryAdapter implements TicketRepositoryPort {

    private final TicketJpaRepository jpaRepository;

    @Override
    public Ticket save(Ticket ticket) {
        return jpaRepository.save(ticket);
    }

    @Override
    public void delete(Ticket ticket) {
        jpaRepository.delete(ticket);
    }

    @Override
    public java.util.Optional<Ticket> findByQrCodeData(String qrCodeData) {
        return jpaRepository.findByQrCodeData(qrCodeData);
    }

    @Override
    public java.util.Optional<Ticket> findByReservationId(java.util.UUID reservationId) {
        return jpaRepository.findByReservationId(reservationId);
    }
}
