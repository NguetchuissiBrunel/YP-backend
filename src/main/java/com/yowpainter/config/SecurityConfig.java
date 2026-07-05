package com.yowpainter.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import com.yowpainter.shared.security.KernelJwtAuthenticationConverter;
import com.yowpainter.shared.security.PublicAwareBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Profile("!test")
@Slf4j
public class SecurityConfig {

    private final KernelProperties kernelProperties;
    private final KernelJwtAuthenticationConverter kernelJwtAuthenticationConverter;
    private final PublicAwareBearerTokenResolver publicAwareBearerTokenResolver;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/admin/auth/**",
                                "/api/public/**",
                                "/api/v1/public/**",
                                "/api/shop/v1/public/**",
                                "/api/public/health",
                                "/api-docs/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-resources",
                                "/swagger-resources/**",
                                "/configuration/ui",
                                "/configuration/security",
                                "/webjars/**",
                                "/error",
                                "/ws/**",
                                "/api/chat/**",
                                "/api/messages/**",
                                "/api/payment/callback",
                                "/api/files/*"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(publicAwareBearerTokenResolver)
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(kernelJwtAuthenticationConverter)
                        )
                        .authenticationEntryPoint(customAuthenticationEntryPoint())
                )
                .addFilterAfter(new com.yowpainter.shared.tenant.TenantSecurityFilter(), org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        log.info("[security] Initialisation du JwtDecoder avec cache et timeout 5s. JWKS URI: {}", kernelProperties.resolvedJwkSetUri());

        org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000); // 5s connect timeout
        requestFactory.setReadTimeout(5000);    // 5s read timeout

        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate(requestFactory);

        return NimbusJwtDecoder.withJwkSetUri(kernelProperties.resolvedJwkSetUri())
                .restOperations(restTemplate)
                .build();
    }

    @Bean
    public org.springframework.security.web.AuthenticationEntryPoint customAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            log.warn("[security] Echec d'authentification sur {} : {}", request.getRequestURI(), authException.getMessage());
            
            String origin = request.getHeader("Origin");
            if (origin != null) {
                response.setHeader("Access-Control-Allow-Origin", origin);
                response.setHeader("Access-Control-Allow-Credentials", "true");
                response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH");
                response.setHeader("Access-Control-Allow-Headers", "*");
            }
            
            response.setStatus(org.springframework.http.HttpStatus.UNAUTHORIZED.value());
            response.setContentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
            
            String message = "Token invalide ou serveur d'authentification indisponible.";
            Throwable cause = authException.getCause();
            if (cause != null) {
                message += " Cause: " + cause.getMessage();
            } else if (authException.getMessage() != null) {
                message += " Cause: " + authException.getMessage();
            }
            
            response.getWriter().write("{\"message\": \"" + message.replace("\"", "\\\"") + "\"}");
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private List<String> corsOriginPatterns() {
        List<String> origins = new ArrayList<>(List.of(
                "http://localhost:3000",
                "http://*.localhost:3000",
                "http://localhost:3001",
                "http://*.localhost:3001",
                "https://yp-frontend.vercel.app",
                "https://*.yp-frontend.vercel.app"
        ));
        if (frontendUrl != null && !frontendUrl.isBlank()) {
            origins.add(frontendUrl.trim());
        }
        return origins;
    }

    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<org.springframework.web.filter.CorsFilter> corsFilterRegistrationBean() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(corsOriginPatterns());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization", "X-Organization-Id"));
        configuration.setAllowCredentials(true);
        source.registerCorsConfiguration("/**", configuration);
        
        org.springframework.web.filter.CorsFilter corsFilter = new org.springframework.web.filter.CorsFilter(source);
        org.springframework.boot.web.servlet.FilterRegistrationBean<org.springframework.web.filter.CorsFilter> bean =
                new org.springframework.boot.web.servlet.FilterRegistrationBean<>(corsFilter);
        bean.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(corsOriginPatterns());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization", "X-Organization-Id"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
