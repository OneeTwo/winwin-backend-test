package com.winwin.data_api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "internal.token=${INTERNAL_TOKEN}",
        "INTERNAL_TOKEN=regression-internal-token"
})
@AutoConfigureMockMvc
class InternalTokenAuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validTokenReturnsTransformedText() throws Exception {
        mockMvc.perform(post("/api/transform").servletPath("/api/transform")
                        .header("X-Internal-Token", "regression-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Hello World\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"result\":\"HELLO WORLD\"}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-token", "", "test-internal-token", " regression-internal-token", "REGRESSION-INTERNAL-TOKEN"})
    void invalidTokenReturnsForbidden(String token) throws Exception {
        mockMvc.perform(post("/api/transform").servletPath("/api/transform")
                        .header("X-Internal-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Hello World\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingTokenReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/transform").servletPath("/api/transform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Hello World\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticationRunsBeforeBodyValidation() throws Exception {
        mockMvc.perform(post("/api/transform").servletPath("/api/transform")
                        .header("X-Internal-Token", "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("invalid-json"))
                .andExpect(status().isForbidden());
    }
}
