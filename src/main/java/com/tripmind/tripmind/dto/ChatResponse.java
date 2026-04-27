package com.tripmind.tripmind.dto;

import java.util.List;
import java.util.Map;

public record ChatResponse(
        String sessionId,
        String conversationState,
        String response,
        Map<String, Object> tripSnapshot,
        List<String> missingFields,
        Boolean readyToPlan,
        TripPlanResponse plan,
        ComparisonView comparison,
        List<TripPlanSummaryView> planHistory
) {
}
