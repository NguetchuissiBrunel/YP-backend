package com.yowpainter.modules.event.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.event.domain.model.Event;
import com.yowpainter.modules.event.domain.model.EventStatus;
import com.yowpainter.modules.event.domain.port.out.EventRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EventRepositoryAdapter implements EventRepositoryPort {

    private final EventJpaRepository jpaRepository;

    @Override
    public Event save(Event event) {
        return jpaRepository.save(event);
    }

    @Override
    public Optional<Event> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Event> findByArtistId(UUID artistId) {
        return jpaRepository.findByArtistId(artistId);
    }

    @Override
    public List<Event> findUpcomingEvents(LocalDateTime now) {
        return jpaRepository.findUpcomingEvents(now);
    }

    @Override
    public List<Event> searchPublicEvents(String query) {
        return jpaRepository.searchPublicEvents(query);
    }

    @Override
    public List<String> findDistinctLocations() {
        return jpaRepository.findDistinctLocations();
    }

    @Override
    public long countByStatusAndStartDateTimeAfter(EventStatus status, LocalDateTime startDateTime) {
        return jpaRepository.countByStatusAndStartDateTimeAfter(status, startDateTime);
    }
}
