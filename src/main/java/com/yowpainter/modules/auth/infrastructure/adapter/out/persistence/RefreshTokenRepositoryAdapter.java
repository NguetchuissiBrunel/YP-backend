package com.yowpainter.modules.auth.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.auth.domain.model.AppUser;
import com.yowpainter.modules.auth.domain.model.RefreshToken;
import com.yowpainter.modules.auth.domain.port.out.RefreshTokenRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepositoryPort {

    private final RefreshTokenJpaRepository jpaRepository;

    @Override
    public Optional<RefreshToken> findByToken(String token) {
        return jpaRepository.findByToken(token);
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        return jpaRepository.save(token);
    }

    @Override
    public void delete(RefreshToken token) {
        jpaRepository.delete(token);
    }

    @Override
    public int deleteByUser(AppUser user) {
        return jpaRepository.deleteByUser(user);
    }

    @Override
    public void flush() {
        jpaRepository.flush();
    }
}
