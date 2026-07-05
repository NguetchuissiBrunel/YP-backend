package com.yowpainter.modules.artist.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.artist.domain.model.PendingProvisionSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingProvisionSessionJpaRepository extends JpaRepository<PendingProvisionSession, java.util.UUID> {


}
