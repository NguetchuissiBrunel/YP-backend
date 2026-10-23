package com.yowpainter.shared.kernel;

import com.yowpainter.config.KernelProperties;
import com.yowpainter.modules.auth.application.port.out.KernelAuthPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Slf4j
public class KernelSystemUserTokenProvider {

    private static final long TOKEN_REFRESH_MARGIN_SECONDS = 60;

    private final KernelAuthPort kernelAuthPort;
    private final KernelProperties properties;

    private volatile CachedToken cachedToken;

    public KernelSystemUserTokenProvider(KernelAuthPort kernelAuthPort, KernelProperties properties) {
        this.kernelAuthPort = kernelAuthPort;
        this.properties = properties;
    }

    public synchronized String getSystemUserAccessToken() {
        CachedToken current = cachedToken;
        if (current != null && current.isValid()) {
            return current.accessToken();
        }

        // Compte machine du noyau (sans MFA, adresse @yowyob.com), fourni par l'environnement : jamais
        // de valeur par defaut dans le code, le depot est public.
        if (isBlank(properties.systemUserEmail()) || isBlank(properties.systemUserPassword())) {
            throw new IllegalStateException(
                    "Compte systeme du noyau non configure : definir KSM_KERNEL_SYSTEM_USER_EMAIL et KSM_KERNEL_SYSTEM_USER_PASSWORD");
        }
        log.info("[system-token] Requesting new system user token for {}...", properties.systemUserEmail());
        try {
            KernelAuthPort.KernelLoginResult loginResult = kernelAuthPort.login(
                    properties.systemUserEmail(),
                    properties.systemUserPassword()
            );

            if (loginResult == null || loginResult.accessToken() == null || loginResult.accessToken().isBlank()) {
                throw new IllegalStateException("Failed to obtain system user token: access token is empty");
            }

            cachedToken = CachedToken.of(loginResult.accessToken(), loginResult.expiresInSeconds());
            log.info("[system-token] Successfully obtained and cached system user token. Expires at: {}", cachedToken.expiry());
            return cachedToken.accessToken();
        } catch (Exception ex) {
            log.error("[system-token] Error obtaining system user token: {}", ex.getMessage(), ex);
            throw new IllegalStateException("Could not authenticate system user with Kernel: " + ex.getMessage(), ex);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public synchronized void invalidate() {
        cachedToken = null;
    }

    private record CachedToken(String accessToken, Instant expiry) {
        public static CachedToken of(String accessToken, long expiresInSeconds) {
            long duration = expiresInSeconds > 0 ? expiresInSeconds : 900; // fallback to 15 minutes
            return new CachedToken(accessToken, Instant.now().plusSeconds(duration));
        }

        public boolean isValid() {
            return Instant.now().isBefore(expiry.minusSeconds(TOKEN_REFRESH_MARGIN_SECONDS));
        }
    }
}
