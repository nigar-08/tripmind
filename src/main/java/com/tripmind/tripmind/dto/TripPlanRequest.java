package com.tripmind.tripmind.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TripPlanRequest(
        @NotBlank String origin,
        @NotBlank String destination,
        @NotNull @Min(1) Integer travelers,
        @NotNull @Min(1000) Integer budget,
        @NotBlank String departureDate,
        String returnDate,
        String tripType,
        String budgetCurrency,
        String budgetScope,
        String vibe,
        String specialRequest,
        String travelerNeeds
) {
}
