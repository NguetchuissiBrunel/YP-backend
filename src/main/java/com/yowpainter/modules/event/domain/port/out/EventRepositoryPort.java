package com.yowpainter.modules.event.domain.port.out;

import com.yowpainter.modules.event.domain.model.Event;
import com.yowpainter.modules.event.domain.model.EventStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepositoryPort {

    Event save(Event event);

    Optional<Event> findById(UUID id);

    List<Event> findByArtistId(UUID artistId);

    List<Event> findUpcomingEvents(LocalDateTime now);

    List<Event> searchPublicEvents(String query);

    List<String> findDistinctLocations();

    long countByStatusAndStartDateTimeAfter(EventStatus status, LocalDateTime startDateTime);
}
