package com.tripmind.tripmind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FeedbackRequest(
        @NotBlank @Size(max = 100) String requestId,
        Boolean useful,
        Boolean budgetRealistic,
        Boolean wouldBook,
        @Size(max = 1000) String comments
) {
}
