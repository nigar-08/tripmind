package com.tripmind.tripmind.model;

public record TripRequest(
        String requestId,
        String origin,
        String destination,
        int travelers,
        int budget,
        String departureDate,
        String returnDate,
        String tripType,
        String budgetCurrency,
        String budgetScope,
        String vibe,
        String specialRequest,
        String travelerNeeds
) {
}
