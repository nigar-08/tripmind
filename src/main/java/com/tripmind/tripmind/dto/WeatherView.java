package com.tripmind.tripmind.dto;

public record WeatherView(
        String summary,
        String seasonSignal,
        String source,
        String travelTiming,
        java.util.List<String> packingList
) implements java.io.Serializable {
}
