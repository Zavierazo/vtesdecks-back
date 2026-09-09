package com.vtesdecks.configuration;

import com.vtesdecks.api.service.UserSecurityService;
import org.springframework.dao.DataAccessException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JWTAuthorizationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private final String jwtSecret;
    private final UserSecurityService userSecurityService;
    private final boolean rejectLegacyTokens;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        // Recovery must also work with a stale login header. Neither endpoint uses JWT authentication.
        return ("/api/1.0/auth/reset-password".equals(path) && "PUT".equals(request.getMethod()))
                || ("/api/1.0/auth/verify".equals(path) && "POST".equals(request.getMethod()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException,
            IOException {
        try {
            if (hasJWTToken(request)) {
                Claims claims = validateToken(request);
                SecurityContextHolder.getContext().setAuthentication(userSecurityService.authenticate(claims, rejectLegacyTokens));
            } else {
                SecurityContextHolder.clearContext();
            }
        } catch (DataAccessException e) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Authentication temporarily unavailable");
            log.warn("event=auth.jwt.authorize outcome=failure reason=database_unavailable");
            return;
        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Unauthorized access");
            // Parser messages and exception causes can contain credential material.
            log.warn("event=auth.jwt.authorize outcome=failure reason={}", e.getClass().getSimpleName());
            return;
        }
        chain.doFilter(request, response);
    }

    private Claims validateToken(HttpServletRequest request) {
        String jwtToken = request.getHeader(HEADER);
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(jwtToken).getPayload();
    }

    private boolean hasJWTToken(HttpServletRequest request) {
        String authenticationHeader = request.getHeader(HEADER);
        return authenticationHeader != null;
    }


}
