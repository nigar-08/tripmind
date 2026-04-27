package com.tripmind.tripmind.dto;

import jakarta.validation.constraints.NotBlank;

public record FeedbackRequest(
        @NotBlank String requestId,
        Boolean useful,
        Boolean budgetRealistic,
        Boolean wouldBook,
        String comments
) {
}
