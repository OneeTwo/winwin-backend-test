package com.winwin.data_api.controller;

import com.winwin.data_api.dto.TransformRequest;
import com.winwin.data_api.dto.TransformResponse;
import com.winwin.data_api.service.TransformService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TransformController {

    private final TransformService transformService;

    public TransformController(TransformService transformService) {
        this.transformService = transformService;
    }

    @PostMapping("/transform")
    public ResponseEntity<TransformResponse> transform(@Valid @RequestBody TransformRequest request) {
        return ResponseEntity.ok(transformService.transform(request));
    }
}
