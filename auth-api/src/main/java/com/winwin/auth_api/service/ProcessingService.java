package com.winwin.auth_api.service;

import com.winwin.auth_api.dto.ProcessRequest;
import com.winwin.auth_api.dto.ProcessResponse;
import com.winwin.auth_api.entity.ProcessingLog;
import com.winwin.auth_api.exception.DataApiException;
import com.winwin.auth_api.repository.ProcessingLogRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Service
public class ProcessingService {
    private final RestClient dataApiRestClient;
    private final ProcessingLogRepository processingLogRepository;

    public ProcessingService(RestClient dataApiRestClient, ProcessingLogRepository processingLogRepository) {
        this.dataApiRestClient = dataApiRestClient;
        this.processingLogRepository = processingLogRepository;
    }

    public ProcessResponse process(UUID userId, ProcessRequest request) {
        ProcessResponse response;
        try {
            response = dataApiRestClient.post().uri("/api/transform")
                    .contentType(MediaType.APPLICATION_JSON).body(request)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (req, res) -> {
                        throw new DataApiException();
                    })
                    .body(ProcessResponse.class);
        } catch (RestClientException ex) {
            throw new DataApiException();
        }
        if (response == null || response.result() == null) {
            throw new DataApiException();
        }
        processingLogRepository.save(new ProcessingLog(userId, request.text(), response.result()));
        return response;
    }
}
