package com.tripmind.tripmind.dto;

import java.util.Map;
import java.util.List;

public record BudgetView(
        String feasibility,
        String confidence,
        String seasonality,
        Integer totalBudget,
        Integer estimatedTripCost,
        Integer contingencyReserve,
        Integer planningTotal,
        Integer remainingAfterEstimate,
        Integer budgetPerPerson,
        Integer dailyPerPerson,
        Integer shortfall,
        Integer pressureScore,
        Map<String, Integer> costBreakdown,
        Map<String, Integer> categoryBudgets,
        Map<String, Integer> categoryPercentages,
        List<String> tradeoffLevers
) implements java.io.Serializable {
}
