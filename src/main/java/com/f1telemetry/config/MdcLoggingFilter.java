package com.f1telemetry.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that populates SLF4J MDC with user-scoped context on every HTTP request.
 *
 * MDC keys set:
 *   - "user"      → authenticated username, or "anonymous" if unauthenticated
 *   - "requestId" → short correlation ID (first 8 chars of UUID) for tracing a single request
 *
 * Runs at highest priority (+1) so the MDC is populated before any other filter logs anything.
 * Always clears MDC in the finally block to prevent thread-pool context leakage.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final String MDC_USER_KEY = "user";
    private static final String MDC_REQUEST_ID_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // Generate a short correlation ID for this request
            String requestId = UUID.randomUUID().toString().substring(0, 8);
            MDC.put(MDC_REQUEST_ID_KEY, requestId);

            // Attempt to extract authenticated username
            String username = resolveUsername();
            MDC.put(MDC_USER_KEY, username);

            filterChain.doFilter(request, response);
        } finally {
            // Always clear MDC to prevent context bleeding across pooled threads
            MDC.clear();
        }
    }

    /**
     * Resolves the current authenticated username from the SecurityContext.
     * Returns "anonymous" if no authentication is present or the user is the anonymous principal.
     */
    private String resolveUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(auth.getPrincipal())) {
                return auth.getName();
            }
        } catch (Exception ignored) {
            // SecurityContext not yet available — safe to ignore
        }
        return "anonymous";
    }
}
