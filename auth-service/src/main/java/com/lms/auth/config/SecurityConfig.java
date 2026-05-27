package com.lms.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.auth.dto.ApiError;
import com.lms.auth.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth -> auth.requestMatchers(HttpMethod.GET, "/actuator/**")
                                .permitAll()
                                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                                .permitAll()
                                .requestMatchers(HttpMethod.GET, "/static/avatars/**")
                                .permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login")
                                .permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh")
                                .permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/v1/auth/me/avatar")
                                .permitAll()
                                .requestMatchers(HttpMethod.PATCH, "/api/v1/auth/me")
                                .permitAll()
                                .requestMatchers(HttpMethod.DELETE, "/api/v1/auth/me")
                                .permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/v1/admin/impersonate/stop")
                                .authenticated()
                                .requestMatchers("/api/v1/admin/**")
                                .hasRole("ADMIN")
                                .anyRequest()
                                .authenticated())
                .exceptionHandling(
                        ex -> ex.authenticationEntryPoint(
                                        (request, response, authException) -> writeJson(
                                                response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED",
                                                "Authentication required"))
                                .accessDeniedHandler(
                                        (request, response, accessDeniedException) -> writeJson(
                                                response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                                                "Access denied")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void writeJson(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String rid = MDC.get(RequestIdFilter.MDC_KEY);
        objectMapper.writeValue(response.getOutputStream(), new ApiError(code, message, rid));
    }
}
