package com.winwin.auth_api.security;

import com.winwin.auth_api.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAuthenticationFilterTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void applicationExceptionIsNotMisclassifiedAsInvalidJwt() {
        JwtService jwtService = new JwtService("test-jwt-secret-key-at-least-32-bytes-long!!", 3600000);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService.generateToken(userId));
        MockHttpServletResponse response = new MockHttpServletResponse();
        IllegalArgumentException failure = new IllegalArgumentException("application failure");

        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(userId);
            throw failure;
        })).isSameAs(failure);
        assertThat(response.getStatus()).isNotEqualTo(401);
    }
}
