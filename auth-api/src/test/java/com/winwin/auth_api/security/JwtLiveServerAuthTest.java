package com.winwin.auth_api.security;

import com.winwin.auth_api.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uses a real Tomcat instance so ERROR dispatch to {@code /error} is exercised.
 * MockMvc does not reproduce the missing-endpoint → 401 misreport.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(JwtLiveServerAuthTest.ProtectedEndpointConfig.class)
class JwtLiveServerAuthTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtService jwtService;

    private RestClient client() {
        return RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void missingToken_returnsUnauthorized() {
        ResponseEntity<String> response = client().get()
                .uri("/api/protected")
                .retrieve()
                .onStatus(status -> true, (req, res) -> { })
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidToken_returnsUnauthorized() {
        ResponseEntity<String> response = client().get()
                .uri("/api/protected")
                .header("Authorization", "Bearer not-a-valid-token")
                .retrieve()
                .onStatus(status -> true, (req, res) -> { })
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validToken_returnsOk() {
        String token = jwtService.generateToken(UUID.randomUUID());

        ResponseEntity<String> response = client().get()
                .uri("/api/protected")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"ok\"");
    }

    @Test
    void missingEndpoint_withValidToken_returnsNotFound() {
        String token = jwtService.generateToken(UUID.randomUUID());

        ResponseEntity<String> response = client().get()
                .uri("/api/nonexistent")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(status -> true, (req, res) -> { })
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @TestConfiguration
    static class ProtectedEndpointConfig {

        @RestController
        static class ProtectedController {

            @GetMapping("/api/protected")
            Map<String, String> protectedEndpoint() {
                return Map.of("status", "ok");
            }
        }
    }
}
