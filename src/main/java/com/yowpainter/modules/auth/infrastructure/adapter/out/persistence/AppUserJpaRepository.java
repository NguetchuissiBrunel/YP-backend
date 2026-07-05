package com.yowpainter.modules.auth.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.auth.domain.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserJpaRepository extends JpaRepository<AppUser, java.util.UUID> {

    java.util.Optional<AppUser> findByEmail(String email);
    java.util.Optional<AppUser> findByKernelUserId(java.util.UUID kernelUserId);
    java.util.List<AppUser> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String firstName, String lastName, String email);
}
