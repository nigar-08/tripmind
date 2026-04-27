package com.tripmind.tripmind.dto;

import java.time.Instant;

public record TripPlanSummaryView(
        String requestId,
        String origin,
        String destination,
        String feasibility,
        Integer budget,
        String departureDate,
        String returnDate,
        Instant createdAt,
        String summary
) implements java.io.Serializable {
}
