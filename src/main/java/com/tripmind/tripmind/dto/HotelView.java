package com.tripmind.tripmind.dto;

import java.util.List;

public record HotelView(
        Integer nights,
        Integer rooms,
        Integer estimatedNightly,
        Integer estimatedTotal,
        Integer allocatedBudget,
        Integer remainingAfterHotels,
        String stayStyle,
        String aiRecommendation,
        String source,
        List<BookingLinkView> bookingLinks
) implements java.io.Serializable {
}
