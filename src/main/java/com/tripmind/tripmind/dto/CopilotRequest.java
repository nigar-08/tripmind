package com.tripmind.tripmind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CopilotRequest(@NotBlank @Size(max = 2000) String message) {}
