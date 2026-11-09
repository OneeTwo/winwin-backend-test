package com.winwin.auth_api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class DataApiConfig {
    @Bean
    public RestClient.Builder dataApiRestClientBuilder(
            @Value("${data-api.url}") String url,
            @Value("${internal.token}") String token) {
        if (url.isBlank() || token.isBlank()) {
            throw new IllegalStateException("DATA_API_URL and INTERNAL_TOKEN must not be blank");
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().baseUrl(url)
                .defaultHeader("X-Internal-Token", token)
                .requestFactory(factory);
    }

    @Bean
    public RestClient dataApiRestClient(RestClient.Builder dataApiRestClientBuilder) {
        return dataApiRestClientBuilder.build();
    }
}
