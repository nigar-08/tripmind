package com.tripmind.tripmind.model;

import com.tripmind.tripmind.dto.AdvisorView;
import com.tripmind.tripmind.dto.BudgetView;
import com.tripmind.tripmind.dto.FlightView;
import com.tripmind.tripmind.dto.HotelView;
import com.tripmind.tripmind.dto.ItineraryView;
import com.tripmind.tripmind.dto.PlanningIntelligenceView;
import com.tripmind.tripmind.dto.WeatherView;

import java.util.List;
import java.util.Map;

public record PlanningSnapshot(
        String feasibility,
        String summary,
        PlanningIntelligenceView planningIntelligence,
        BudgetView budget,
        FlightView flights,
        HotelView hotels,
        WeatherView weather,
        AdvisorView advisor,
        ItineraryView itinerary,
        Map<String, String> dataSources,
        List<AgentResult> stages
) {
}
