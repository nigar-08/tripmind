package com.tripmind.tripmind.dto;

import java.util.List;

public record FlightView(
        String route,
        String cabin,
        Integer estimatedTotal,
        Integer allocatedBudget,
        Integer remainingAfterFlights,
        String estimateType,
        String bookingPath,
        String source,
        List<BookingLinkView> bookingLinks
) implements java.io.Serializable {
}
