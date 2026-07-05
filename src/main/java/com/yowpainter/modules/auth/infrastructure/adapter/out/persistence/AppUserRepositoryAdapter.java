package com.yowpainter.modules.auth.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.auth.domain.model.AppUser;
import com.yowpainter.modules.auth.domain.port.out.AppUserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AppUserRepositoryAdapter implements AppUserRepositoryPort {

    private final AppUserJpaRepository jpaRepository;

    @Override
    public java.util.Optional<AppUser> findByEmail(String email) {
        return jpaRepository.findByEmail(email);
    }

    @Override
    public java.util.Optional<AppUser> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public java.util.Optional<AppUser> findByKernelUserId(UUID kernelUserId) {
        return jpaRepository.findByKernelUserId(kernelUserId);
    }

    @Override
    public java.util.List<AppUser> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String firstName, String lastName, String email) {
        return jpaRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                firstName, lastName, email);
    }

    @Override
    public java.util.List<AppUser> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public AppUser save(AppUser user) {
        return jpaRepository.save(user);
    }

    @Override
    public boolean existsById(UUID id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }
}
