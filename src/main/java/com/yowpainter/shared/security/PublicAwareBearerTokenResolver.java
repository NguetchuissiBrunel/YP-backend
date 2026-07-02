package com.yowpainter.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;

/**
 * Sur les routes publiques, ignore le header Authorization pour eviter un 401
 * lorsque Swagger envoie un JWT expire via "Authorize".
 */
@Component
public class PublicAwareBearerTokenResolver implements BearerTokenResolver {

    private final DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        if (isPublicPath(request)) {
            return null;
        }
        return delegate.resolve(request);
    }

    private static boolean isPublicPath(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        return path.startsWith("/api/auth/")
                || path.startsWith("/api/admin/auth/")
                || path.startsWith("/api/public/")
                || path.startsWith("/api/v1/public/")
                || path.startsWith("/api/shop/v1/public/")
                || path.equals("/api/public/health")
                || path.startsWith("/api-docs")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.equals("/swagger-ui.html")
                || path.startsWith("/swagger-resources")
                || path.startsWith("/webjars/")
                || path.startsWith("/error")
                || path.startsWith("/ws/")
                || path.startsWith("/api/chat/")
                || path.startsWith("/api/messages/")
                || path.equals("/api/payment/callback")
                || (path.startsWith("/api/files/") && !"POST".equalsIgnoreCase(request.getMethod()));
    }
}
