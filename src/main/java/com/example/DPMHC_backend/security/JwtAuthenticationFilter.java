package com.example.DPMHC_backend.security;

import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String requestPath = request.getRequestURI();
        final String method = request.getMethod();

        logger.debug("Processing request: {} {}", method, requestPath);

        try {
            final String authHeader = request.getHeader("Authorization");

            // Log auth header presence (without exposing the actual token)
            logger.debug("Authorization header present: {}", authHeader != null);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                logger.debug("No valid Authorization header found, continuing filter chain");
                filterChain.doFilter(request, response);
                return;
            }

            final String jwt = authHeader.substring(7);
            logger.debug("JWT token extracted, length: {}", jwt.length());

            // Extract email from JWT
            final String userEmail;
            try {
                userEmail = jwtService.extractEmail(jwt);
                logger.debug("Extracted email from JWT: {}", userEmail);
            } catch (Exception e) {
                logger.error("Failed to extract email from JWT: {}", e.getMessage());
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid token format");
                return;
            }

            if (userEmail == null || userEmail.trim().isEmpty()) {
                logger.error("Email extracted from JWT is null or empty");
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid token - no email");
                return;
            }

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                logger.debug("No existing authentication, proceeding with JWT validation");

                // Find user by email
                Optional<User> userOpt = userRepository.findByEmail(userEmail);
                if (userOpt.isEmpty()) {
                    logger.error("User not found in database: {}", userEmail);
                    sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "User not found");
                    return;
                }

                User user = userOpt.get();
                logger.debug("User found: {} with ID: {}", user.getEmail(), user.getId());

                // Validate JWT token
                boolean isTokenValid;
                try {
                    isTokenValid = jwtService.isTokenValid(jwt, user);
                    logger.debug("Token validation result: {}", isTokenValid);
                } catch (Exception e) {
                    logger.error("Token validation failed: {}", e.getMessage());
                    sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Token validation failed");
                    return;
                }

                if (!isTokenValid) {
                    logger.error("JWT token is invalid for user: {}", userEmail);
                    sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
                    return;
                }

                // Extract role from JWT
                String role;
                try {
                    role = jwtService.extractRole(jwt);
                    logger.debug("Extracted role from JWT: {}", role);
                } catch (Exception e) {
                    logger.error("Failed to extract role from JWT: {}", e.getMessage());
                    // Default to USER role if extraction fails
                    role = "USER";
                    logger.debug("Defaulting to USER role");
                }

                if (role == null || role.trim().isEmpty()) {
                    logger.warn("Role extracted from JWT is null or empty, defaulting to USER");
                    role = "USER";
                }

                // Create authorities
                List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                        new SimpleGrantedAuthority("ROLE_" + role)
                );
                logger.debug("Created authorities: {}", authorities);

                // Create authentication token
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(user, null, authorities);
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Set authentication in security context
                SecurityContextHolder.getContext().setAuthentication(authToken);
                logger.info("=== JWT FILTER FINAL DEBUG ===");
                logger.info("Set authentication for user: {}", user.getEmail());
                logger.info("Authentication principal class: {}", authToken.getPrincipal().getClass().getName());
                logger.info("Authentication principal: {}", authToken.getPrincipal());
                logger.info("Authentication authorities: {}", authToken.getAuthorities());
                logger.info("Is authenticated: {}", authToken.isAuthenticated());
            } else {
                logger.debug("Authentication already exists in SecurityContext");
            }

            filterChain.doFilter(request, response);

        } catch (Exception ex) {
            logger.error("JWT Authentication failed for request {} {}: {}",
                    method, requestPath, ex.getMessage(), ex);

            if (!response.isCommitted()) {
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed");
            }
        }
    }

    private void sendError(HttpServletResponse response, int status, String message)
            throws IOException {
        if (response.isCommitted()) {
            logger.warn("Response already committed, cannot send error");
            return;
        }

        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String jsonError = String.format("{\"error\":\"%s\"}", message);
        response.getWriter().write(jsonError);
        response.getWriter().flush();

        logger.debug("Sent error response: {} - {}", status, message);
    }
}