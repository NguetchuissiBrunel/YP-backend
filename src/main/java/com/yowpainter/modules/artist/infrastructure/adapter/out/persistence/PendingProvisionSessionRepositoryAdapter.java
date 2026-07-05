package com.yowpainter.modules.artist.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.artist.domain.model.PendingProvisionSession;
import com.yowpainter.modules.artist.domain.port.out.PendingProvisionSessionRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PendingProvisionSessionRepositoryAdapter implements PendingProvisionSessionRepositoryPort {

    private final PendingProvisionSessionJpaRepository jpaRepository;

    @Override
    public PendingProvisionSession save(PendingProvisionSession session) {
        return jpaRepository.save(session);
    }

    @Override
    public java.util.Optional<PendingProvisionSession> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }
}
