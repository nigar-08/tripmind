package com.tripmind.tripmind.dto;

public record ComparisonView(
        String comparisonRequest,
        String secondaryDestination,
        TripPlanResponse secondaryPlan,
        String note
) implements java.io.Serializable {
}
