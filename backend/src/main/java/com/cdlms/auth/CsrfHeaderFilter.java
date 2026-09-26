package com.cdlms.auth;

import com.cdlms.common.ErrorResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * CSRF protection by required custom header (ADR-014).
 *
 * <p>Every state-changing request to {@code /api} must send {@code X-CSRF-Protection: 1}. Another
 * website can't add a custom header without a CORS preflight, and the backend grants no
 * cross-origin access, so a forged request never carries it. Runs before authentication, so it
 * also protects login.
 */
public class CsrfHeaderFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-CSRF-Protection";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SAFE_METHODS.contains(request.getMethod()) || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!"1".equals(request.getHeader(HEADER))) {
            ErrorResponses.write(response, HttpServletResponse.SC_FORBIDDEN,
                    "Missing " + HEADER + " header", "CSRF_HEADER_MISSING");
            return;
        }
        chain.doFilter(request, response);
    }
}
