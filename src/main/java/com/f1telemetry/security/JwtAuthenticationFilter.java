package com.f1telemetry.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        try {
            username = jwtUtil.extractUsername(jwt);
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // If token is valid, set context
                if (jwtUtil.isTokenValid(jwt, username)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            username, null, new ArrayList<>());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    // Populate MDC with authenticated user at the earliest possible point
                    MDC.put("user", username);
                    log.debug("JWT authenticated user '{}' for {} {}",
                            username, request.getMethod(), request.getRequestURI());
                } else {
                    log.debug("JWT token validation failed for user '{}' — token expired or invalid", username);
                }
            }
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
        } catch (io.jsonwebtoken.security.SecurityException | io.jsonwebtoken.MalformedJwtException e) {
            log.warn("JWT token malformed or signature invalid: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("JWT authentication failed: {}", e.getMessage());
        }
        
        filterChain.doFilter(request, response);
    }
}

