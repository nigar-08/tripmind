package com.tripmind.tripmind.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TripPlanRequest(
        @NotBlank @Size(max = 100) String origin,
        @NotBlank @Size(max = 100) String destination,
        @NotNull @Min(1) Integer travelers,
        @NotNull @Min(1000) Integer budget,
        @NotBlank String departureDate,
        String returnDate,
        String tripType,
        String budgetCurrency,
        String budgetScope,
        @Size(max = 100) String vibe,
        @Size(max = 500) String specialRequest,
        @Size(max = 500) String travelerNeeds
) {
}
