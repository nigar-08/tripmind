package com.tripmind.tripmind.dto;

import java.util.List;

public record ItineraryView(
        String preview,
        String budgetNarrative,
        String aiNarrative,
        List<String> dayHighlights,
        List<ItineraryDayView> days,
        List<String> realPlaces,
        String placeSource
) implements java.io.Serializable {
}
