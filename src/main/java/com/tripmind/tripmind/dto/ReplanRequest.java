package com.tripmind.tripmind.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ReplanRequest(
        @Size(max = 100) String vibe,
        @Size(max = 500) String specialRequest,
        @Min(1000) Integer budget,
        String departureDate,
        String returnDate
) {
}
