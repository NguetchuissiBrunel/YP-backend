package com.yowpainter.modules.auth.domain.port.out;

import com.yowpainter.modules.auth.domain.model.AppUser;
import com.yowpainter.modules.auth.domain.model.RefreshToken;

import java.util.Optional;

public interface RefreshTokenRepositoryPort {

    Optional<RefreshToken> findByToken(String token);

    RefreshToken save(RefreshToken token);

    void delete(RefreshToken token);

    int deleteByUser(AppUser user);

    void flush();
}
