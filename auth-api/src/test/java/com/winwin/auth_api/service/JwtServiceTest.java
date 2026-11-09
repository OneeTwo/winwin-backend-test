package com.winwin.auth_api.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-jwt-secret-key-at-least-32-bytes-long!!";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 3_600_000L);
    }

    @Test
    void generateToken_includesSubjectIssuedAtAndExpiration() {
        UUID userId = UUID.randomUUID();
        Instant before = Instant.now().minusSeconds(1);

        String token = jwtService.generateToken(userId);

        Claims claims = jwtService.parseClaims(token);
        Instant after = Instant.now().plusSeconds(1);

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getIssuedAt().toInstant()).isBetween(before, after);
        assertThat(claims.getExpiration().toInstant())
                .isBetween(before.plus(Duration.ofHours(1)), after.plus(Duration.ofHours(1)));
        assertThat(jwtService.parseSubject(token)).isEqualTo(userId);
    }

    @Test
    void parseClaims_rejectsTokenSignedWithDifferentSecret() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId);

        SecretKey otherKey = Keys.hmacShaKeyFor(
                "another-secret-key-at-least-32-bytes-xx".getBytes(StandardCharsets.UTF_8));
        String tamperedToken = Jwts.builder()
                .subject(userId.toString())
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .signWith(otherKey, Jwts.SIG.HS256)
                .compact();

        assertThat(jwtService.isValid(token)).isTrue();
        assertThatThrownBy(() -> jwtService.parseClaims(tamperedToken))
                .isInstanceOf(JwtException.class);
        assertThat(jwtService.isValid(tamperedToken)).isFalse();
    }

    @Test
    void parseClaims_rejectsExpiredToken() {
        UUID userId = UUID.randomUUID();
        String expiredToken = jwtService.generateToken(userId, Duration.ofSeconds(-1));

        assertThatThrownBy(() -> jwtService.parseClaims(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
        assertThat(jwtService.isValid(expiredToken)).isFalse();
    }

    @Test
    void constructor_rejectsWeakSecret() {
        assertThatThrownBy(() -> new JwtService("too-short", 3_600_000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
