package com.yowpainter.modules.auth.domain.port.out;

import com.yowpainter.modules.auth.domain.model.AppUser;
import java.util.UUID;

public interface AppUserRepositoryPort {

    java.util.Optional<AppUser> findByEmail(String email);
    java.util.Optional<AppUser> findById(UUID id);
    java.util.Optional<AppUser> findByKernelUserId(UUID kernelUserId);
    java.util.List<AppUser> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String firstName, String lastName, String email);
    java.util.List<AppUser> findAll();
    AppUser save(AppUser user);
    boolean existsById(UUID id);
    void deleteById(UUID id);
    long count();
}
