package com.lms.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.auth.config.RequestIdFilter;
import com.lms.auth.dto.ApiError;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String m = request.getMethod();
        if (HttpMethod.GET.matches(m) && path.startsWith("/actuator")) {
            return true;
        }
        if (path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")) {
            return true;
        }
        if (HttpMethod.POST.matches(m)) {
            return "/api/v1/auth/register".equals(path)
                    || "/api/v1/auth/login".equals(path)
                    || "/api/v1/auth/refresh".equals(path);
        }
        return false;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        String raw = header.substring(7);
        try {
            JwtUserPrincipal principal = jwtService.parseAccessPrincipal(raw);
            var auth = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (JwtException ex) {
            writeUnauthorized(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String rid = MDC.get(RequestIdFilter.MDC_KEY);
        var err = new ApiError("UNAUTHORIZED", "Invalid or expired token", rid);
        objectMapper.writeValue(response.getOutputStream(), err);
    }
}
