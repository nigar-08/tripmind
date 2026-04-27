package com.tripmind.tripmind.dto;

import java.util.List;

public record ItineraryDayView(
        Integer day,
        String date,
        String theme,
        String morning,
        String afternoon,
        String evening,
        String budgetNote,
        List<String> bookingHints
) implements java.io.Serializable {
}
