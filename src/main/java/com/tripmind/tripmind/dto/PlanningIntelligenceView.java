package com.tripmind.tripmind.dto;

import java.util.List;

public record PlanningIntelligenceView(
        String tripArchetype,
        String planningMode,
        String bookingReadiness,
        String sourceReliability,
        String liveDataCoverage,
        String travelerProfile,
        List<String> groundingNotes
) implements java.io.Serializable {
}
