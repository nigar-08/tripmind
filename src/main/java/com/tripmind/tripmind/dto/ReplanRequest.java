package com.tripmind.tripmind.dto;

public record ReplanRequest(
        String vibe,
        String specialRequest,
        Integer budget,
        String departureDate,
        String returnDate
) {
}
