package com.tripmind.tripmind.service.agent;

import com.tripmind.tripmind.model.AgentResult;
import com.tripmind.tripmind.model.TripRequest;
import com.tripmind.tripmind.service.provider.PricingEngineService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BudgetAgentService {

    private final PricingEngineService pricingEngineService;

    public BudgetAgentService(PricingEngineService pricingEngineService) {
        this.pricingEngineService = pricingEngineService;
    }

    public BudgetPlan analyze(TripRequest request) {
        Map<String, Integer> costBreakdown = new LinkedHashMap<>();
        int flights = pricingEngineService.estimateFlightCost(request.origin(), request.destination(), request.travelers());
        int hotels = pricingEngineService.estimateHotelCost(
                request.destination(),
                request.departureDate(),
                request.returnDate(),
                request.travelers(),
                request.vibe()
        );
        int activities = pricingEngineService.estimateActivityAndDailySpend(
                request.destination(),
                request.departureDate(),
                request.returnDate(),
                request.travelers(),
                request.vibe()
        );
        int hiddenCosts = pricingEngineService.estimateHiddenCosts(
                request.origin(),
                request.destination(),
                request.travelers(),
                request.departureDate()
        );
        int total = flights + hotels + activities + hiddenCosts;
        int contingencyReserve = Math.max(1000, (int) Math.round(total * 0.12));
        int planningTotal = total + contingencyReserve;
        int remainingAfterEstimate = request.budget() - planningTotal;
        int budgetPerPerson = request.budget() / Math.max(1, request.travelers());
        int dailyPerPerson = pricingEngineService.estimateDailyPerPerson(
                request.destination(),
                request.departureDate(),
                request.vibe()
        );

        costBreakdown.put("flights", flights);
        costBreakdown.put("hotels", hotels);
        costBreakdown.put("activities", activities);
        costBreakdown.put("hidden_costs", hiddenCosts);
        costBreakdown.put("contingency", contingencyReserve);

        Map<String, Integer> categoryBudgets = categoryBudgets(request, flights, hotels, activities);
        String feasibility = classifyFeasibility(request.budget(), planningTotal);
        String confidence = pricingEngineService.confidence(request.destination());
        String seasonality = pricingEngineService.seasonalityLabel(request.departureDate(), request.destination());
        int shortfall = Math.max(0, planningTotal - request.budget());
        int pressureScore = pressureScore(request.budget(), planningTotal);
        Map<String, Integer> categoryPercentages = categoryPercentages(costBreakdown, planningTotal);
        List<String> tradeoffLevers = tradeoffLevers(feasibility, hotels, activities, flights, request);
        String summary = switch (feasibility) {
            case "good" -> "Budget fits the planning estimate including hidden costs and a supplier-price buffer.";
            case "tight" -> "Budget can work, but the plan has limited buffer after hidden costs and needs careful booking choices.";
            default -> "Budget is below the planning estimate once hidden costs are included, so a route, hotel, duration, or destination tradeoff is needed.";
        };

        return new BudgetPlan(
                feasibility,
                summary,
                confidence,
                seasonality,
                request.budget(),
                total,
                contingencyReserve,
                planningTotal,
                remainingAfterEstimate,
                budgetPerPerson,
                dailyPerPerson,
                shortfall,
                pressureScore,
                costBreakdown,
                categoryBudgets,
                categoryPercentages,
                tradeoffLevers,
                new AgentResult("budget-agent", "completed", summary, "feasibility + budget allocation", null, false)
        );
    }

    private String classifyFeasibility(int budget, int planningTotal) {
        if (budget >= planningTotal) {
            return "good";
        }
        if (budget >= planningTotal * 0.88) {
            return "tight";
        }
        return "not_feasible";
    }

    private int pressureScore(int budget, int planningTotal) {
        if (budget <= 0) {
            return 100;
        }
        double ratio = (double) planningTotal / budget;
        return (int) Math.max(0, Math.min(100, Math.round((ratio - 0.75) * 100)));
    }

    private Map<String, Integer> categoryPercentages(Map<String, Integer> costBreakdown, int planningTotal) {
        Map<String, Integer> percentages = new LinkedHashMap<>();
        costBreakdown.forEach((category, value) -> percentages.put(
                category,
                planningTotal == 0 ? 0 : (int) Math.round((value * 100.0) / planningTotal)
        ));
        return percentages;
    }

    private Map<String, Integer> categoryBudgets(TripRequest request, int flights, int hotels, int activities) {
        Map<String, Integer> budgets = new LinkedHashMap<>();
        int totalBudget = request.budget();
        int flightBudget = boundedLane(flights, (int) Math.round(totalBudget * 0.35));
        int remaining = Math.max(0, totalBudget - flightBudget);
        double hotelShare = "budget".equalsIgnoreCase(request.vibe()) ? 0.50 : "luxury".equalsIgnoreCase(request.vibe()) ? 0.62 : 0.55;
        int hotelBudget = boundedLane(hotels, (int) Math.round(remaining * hotelShare));
        int activitiesBudget = boundedLane(activities, (int) Math.round(remaining * (1 - hotelShare)));
        if (hotelBudget + activitiesBudget > remaining && hotelBudget + activitiesBudget > 0) {
            double scale = remaining / (double) (hotelBudget + activitiesBudget);
            hotelBudget = (int) Math.round(hotelBudget * scale);
            activitiesBudget = remaining - hotelBudget;
        }
        int buffer = Math.max(0, remaining - hotelBudget - activitiesBudget);

        budgets.put("flights", flightBudget);
        budgets.put("hotels", hotelBudget);
        budgets.put("activities", activitiesBudget);
        budgets.put("buffer", buffer);
        return budgets;
    }

    private int boundedLane(int estimate, int percentageLane) {
        int practicalCeiling = Math.max(estimate + 5_000, (int) Math.round(estimate * 1.35));
        return Math.max(estimate, Math.min(percentageLane, practicalCeiling));
    }

    private List<String> tradeoffLevers(String feasibility, int hotels, int activities, int flights, TripRequest request) {
        List<String> levers = new ArrayList<>();
        if ("good".equals(feasibility)) {
            levers.add("Keep the 12% contingency untouched until live supplier prices are checked.");
            levers.add("Book refundable hotels first, then lock flights after comparing live fare links.");
            return levers;
        }

        if (hotels >= activities && hotels >= flights) {
            levers.add("Reduce hotel category or change area before cutting core trip experiences.");
        }
        if (activities >= hotels) {
            levers.add("Keep one paid anchor experience and replace secondary activities with free/low-cost options.");
        }
        levers.add("Shift dates by 2-3 days if live flight links show a fare spike.");
        levers.add("Shorten the trip by one night if the shortfall remains after hotel and activity changes.");
        levers.add("Double-check airport transfers, baggage, insurance, and destination entry costs before treating the plan as final.");
        if (request.specialRequest() != null && !request.specialRequest().isBlank()) {
            levers.add("Protect the special request first, then optimize the remaining categories around it.");
        }
        return levers;
    }

    public record BudgetPlan(
            String feasibility,
            String summary,
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
            List<String> tradeoffLevers,
            AgentResult agentResult
    ) {
    }
}
