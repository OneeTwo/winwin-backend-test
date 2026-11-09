package com.winwin.auth_api.integration;

import com.winwin.auth_api.repository.ProcessingLogRepository;
import com.winwin.auth_api.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ProcessingIntegrationTest.HttpMockConfig.class)
class ProcessingIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired ProcessingLogRepository logs;
    @Autowired MockRestServiceServer server;
    private UUID userId;

    @BeforeEach
    void setUp() {
        server.reset();
        logs.deleteAll();
        userId = UUID.randomUUID();
    }

    @AfterEach
    void verifyHttpRequests() {
        server.verify();
    }

    private MockHttpServletRequestBuilder authenticated(String body) {
        return post("/api/process")
                .header("Authorization", "Bearer " + jwtService.generateToken(userId))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void successfulProcessingSendsTokenAndPersistsAuthenticatedUser() throws Exception {
        server.expect(requestTo("http://data-api.test:8081/api/transform"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", "test-internal-token"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"text\":\"Hello World\"}"))
                .andRespond(withSuccess("{\"result\":\"HELLO WORLD\"}", MediaType.APPLICATION_JSON));

        // A body-supplied userId must never override the JWT subject.
        mockMvc.perform(authenticated("{\"text\":\"Hello World\",\"userId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .json("{\"result\":\"HELLO WORLD\"}"));

        assertThat(logs.findAll()).singleElement().satisfies(log -> {
            assertThat(log.getId()).isNotNull();
            assertThat(log.getUserId()).isEqualTo(userId);
            assertThat(log.getInputText()).isEqualTo("Hello World");
            assertThat(log.getOutputText()).isEqualTo("HELLO WORLD");
            assertThat(log.getCreatedAt()).isNotNull();
        });
    }

    @Test
    void missingJwtReturns401() throws Exception {
        mockMvc.perform(post("/api/process").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Hello World\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(logs.count()).isZero();
    }

    @Test
    void invalidJwtReturns401() throws Exception {
        mockMvc.perform(post("/api/process").header("Authorization", "Bearer invalid-jwt")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Hello World\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(logs.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"text\":null}", "{\"text\":\"\"}", "{\"text\":\"   \"}"})
    void invalidTextReturns400(String body) throws Exception {
        mockMvc.perform(authenticated(body)).andExpect(status().isBadRequest());
        assertThat(logs.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 403, 500, 503})
    void downstreamErrorReturns502WithoutSavingOrExposingDetails(int statusCode) throws Exception {
        server.expect(requestTo("http://data-api.test:8081/api/transform"))
                .andRespond(withStatus(HttpStatus.valueOf(statusCode))
                        .body("sensitive downstream details").contentType(MediaType.TEXT_PLAIN));
        mockMvc.perform(authenticated("{\"text\":\"Hello World\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Transformation service unavailable"));
        assertThat(logs.count()).isZero();
    }

    @Test
    void unavailableDataApiReturns502WithoutSaving() throws Exception {
        server.expect(requestTo("http://data-api.test:8081/api/transform"))
                .andRespond(withException(new IOException("Connection refused")));
        mockMvc.perform(authenticated("{\"text\":\"Hello World\"}"))
                .andExpect(status().isBadGateway());
        assertThat(logs.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"result\":null}", "not-json"})
    void invalidDownstreamResponseReturns502WithoutSaving(String response) throws Exception {
        server.expect(requestTo("http://data-api.test:8081/api/transform"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        mockMvc.perform(authenticated("{\"text\":\"Hello World\"}"))
                .andExpect(status().isBadGateway());
        assertThat(logs.count()).isZero();
    }

    @TestConfiguration
    static class HttpMockConfig {
        @Bean
        MockRestServiceServer mockRestServiceServer(RestClient.Builder dataApiRestClientBuilder) {
            return MockRestServiceServer.bindTo(dataApiRestClientBuilder).build();
        }

        @Bean
        @Primary
        RestClient mockDataApiRestClient(RestClient.Builder dataApiRestClientBuilder,
                                        MockRestServiceServer mockRestServiceServer) {
            return dataApiRestClientBuilder.build();
        }
    }
}
