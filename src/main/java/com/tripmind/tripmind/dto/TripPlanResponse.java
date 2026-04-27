package com.tripmind.tripmind.dto;

import java.util.List;
import java.util.Map;

public record TripPlanResponse(
        String requestId,
        String destination,
        String feasibility,
        String summary,
        PlanningIntelligenceView planningIntelligence,
        BudgetView budget,
        FlightView flights,
        HotelView hotels,
        WeatherView weather,
        AdvisorView advisor,
        List<AgentStageView> stages,
        ItineraryView itinerary,
        Map<String, String> dataSources,
        List<TripRiskView> riskFlags
) implements java.io.Serializable {
}
