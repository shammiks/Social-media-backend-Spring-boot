package com.example.DPMHC_backend.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);
    private final JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        logger.info("Configuring Security Filter Chain");

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> {
                    logger.info("Configuring authorization rules");
                    auth
                            // Public endpoints - no authentication required
                            .requestMatchers(
                                    "/api/auth/register",
                                    "/api/auth/login",
                                    "/api/auth/verify",
                                    "/api/auth/request-password-reset",
                                    "/api/auth/reset-password",
                                    "/actuator/health",
                                    "/uploads/**",
                                    "/error"
                            ).permitAll()

                            // Authenticated endpoints - require valid JWT token
                            .requestMatchers("/api/chats/**").authenticated()
                            .requestMatchers("/api/chat/**").authenticated()
                            .requestMatchers("/api/messages/**").authenticated()
                            .requestMatchers("/api/auth/me").authenticated()
                            .requestMatchers("/api/users/**").authenticated()
                            .requestMatchers("/api/posts/**").authenticated()
                            .requestMatchers("/api/follow/**").authenticated()
                            .requestMatchers("/api/bookmarks/**").authenticated()
                            .requestMatchers("/api/comments/**").authenticated()

                            // Admin only endpoints
                            .requestMatchers("/api/admin/**").hasRole("ADMIN")

                            // All other requests require authentication
                            .anyRequest().authenticated();
                })
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            logger.warn("Authentication failed for {}: {}",
                                    request.getRequestURI(), authException.getMessage());
                            writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            logger.warn("Access denied for {}: {}",
                                    request.getRequestURI(), accessDeniedException.getMessage());
                            writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Access denied");
                        })
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        logger.info("Security Filter Chain configured successfully");
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        logger.info("Configuring CORS");

        CorsConfiguration configuration = new CorsConfiguration();

        // Allow all origins for development (use specific origins in production)
        configuration.setAllowedOriginPatterns(List.of("*"));

        // Allow all common HTTP methods
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"
        ));

        // Allow all headers
        configuration.setAllowedHeaders(List.of("*"));

        // Don't allow credentials with wildcard origins
        configuration.setAllowCredentials(false);

        // Cache preflight response for 1 hour
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        logger.info("CORS configuration completed - allowing all origins for development");
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        logger.info("Configuring BCrypt password encoder");
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        logger.info("Configuring authentication manager");
        return config.getAuthenticationManager();
    }

    private void writeJsonError(HttpServletResponse response, int status, String message) throws IOException {
        if (response.isCommitted()) {
            logger.warn("Response already committed, cannot write error");
            return;
        }

        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String jsonError = String.format("{\"error\":\"%s\"}", message);
        response.getWriter().write(jsonError);
        response.getWriter().flush();

        logger.debug("Sent JSON error response: {} - {}", status, message);
    }
}
