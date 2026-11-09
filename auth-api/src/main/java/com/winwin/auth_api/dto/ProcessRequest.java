package com.winwin.auth_api.dto;

import jakarta.validation.constraints.NotBlank;

public record ProcessRequest(@NotBlank String text) {}
