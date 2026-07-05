package com.yowpainter.modules.artist.domain.port.out;

import com.yowpainter.modules.artist.domain.model.PendingProvisionSession;
import java.util.UUID;

public interface PendingProvisionSessionRepositoryPort {

    PendingProvisionSession save(PendingProvisionSession session);
    java.util.Optional<PendingProvisionSession> findById(UUID id);
    void deleteById(UUID id);
}
