package com.winwin.auth_api.controller;

import com.winwin.auth_api.dto.ProcessRequest;
import com.winwin.auth_api.dto.ProcessResponse;
import com.winwin.auth_api.service.ProcessingService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ProcessingController {
    private final ProcessingService processingService;

    public ProcessingController(ProcessingService processingService) {
        this.processingService = processingService;
    }

    @PostMapping("/process")
    public ProcessResponse process(@AuthenticationPrincipal UUID userId,
                                   @Valid @RequestBody ProcessRequest request) {
        return processingService.process(userId, request);
    }
}
