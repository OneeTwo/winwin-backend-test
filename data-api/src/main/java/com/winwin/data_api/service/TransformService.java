package com.winwin.data_api.service;

import com.winwin.data_api.dto.TransformRequest;
import com.winwin.data_api.dto.TransformResponse;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class TransformService {

    public TransformResponse transform(TransformRequest request) {
        return new TransformResponse(request.text().toUpperCase(Locale.ROOT));
    }
}
