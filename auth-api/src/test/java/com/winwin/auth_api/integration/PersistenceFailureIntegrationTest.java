package com.winwin.auth_api.integration;

import com.winwin.auth_api.entity.User;
import com.winwin.auth_api.repository.ProcessingLogRepository;
import com.winwin.auth_api.repository.UserRepository;
import com.winwin.auth_api.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.TransactionSystemException;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ProcessingIntegrationTest.HttpMockConfig.class)
class PersistenceFailureIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired MockRestServiceServer server;
    @MockitoSpyBean UserRepository users;
    @MockitoBean ProcessingLogRepository logs;

    private static final String SAFE_ERROR = "{\"message\":\"Unable to complete database operation\"}";

    @AfterEach
    void verifyHttpRequests() {
        server.verify();
        server.reset();
    }

    @Test
    void registrationAndConcurrentDuplicatePreserve201And409AfterRealConstraintFailure() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String body = "{\"email\":\"" + email + "\",\"password\":\"password123\"}";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        // Simulate another registration committing between the existence check and insert.
        doReturn(false, true).when(users).existsByEmail(email);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(content().string("{\"message\":\"Email already registered\"}"));
        assertThat(users.findByEmail(email)).isPresent();
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void unrelatedRegistrationConstraintFailureReturnsSafe500() throws Exception {
        doThrow(new DataIntegrityViolationException("INSERT INTO users; private database details"))
                .when(users).saveAndFlush(any(User.class));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unexpected@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(SAFE_ERROR));
    }

    @Test
    void databaseUnavailableDuringLoginReturnsSafe500() throws Exception {
        doThrow(new DataAccessResourceFailureException("private database hostname and credentials"))
                .when(users).findByEmail("unavailable@example.com");
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unavailable@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(SAFE_ERROR));
    }

    static Stream<RuntimeException> persistenceFailures() {
        return Stream.of(new DataIntegrityViolationException("INSERT INTO processing_log; private text"),
                new DataAccessResourceFailureException("private database credentials"),
                new TransactionSystemException("private commit failure"));
    }

    @ParameterizedTest
    @MethodSource("persistenceFailures")
    void processingLogFailuresReturnSafe500InsteadOfEmailConflict(RuntimeException failure) throws Exception {
        server.expect(requestTo("http://data-api.test:8081/api/transform"))
                .andRespond(withSuccess("{\"result\":\"HELLO WORLD\"}", MediaType.APPLICATION_JSON));
        when(logs.save(any())).thenThrow(failure);
        mockMvc.perform(post("/api/process")
                        .header("Authorization", "Bearer " + jwtService.generateToken(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Hello World\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(SAFE_ERROR));
    }
}
