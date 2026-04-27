package com.tripmind.tripmind.service.agent;

import com.tripmind.tripmind.dto.AlternativeView;
import com.tripmind.tripmind.model.AgentResult;
import com.tripmind.tripmind.model.TripRequest;
import com.tripmind.tripmind.service.ai.AiProviderService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TripAdvisorAgentService {

    private final AiProviderService aiProviderService;

    public TripAdvisorAgentService(AiProviderService aiProviderService) {
        this.aiProviderService = aiProviderService;
    }

    public AdvisorOutput advise(TripRequest request, BudgetAgentService.BudgetPlan budgetPlan) {
        List<String> recommendations = new ArrayList<>();
        recommendations.add("Keep the ₹" + budgetPlan.contingencyReserve() + " contingency reserve flexible for final supplier prices.");
        recommendations.add("Use the trip plan as a planning guide, then confirm live inventory before booking.");
        if ("tight".equals(budgetPlan.feasibility())) {
            recommendations.add("Budget pressure score is " + budgetPlan.pressureScore() + "/100, so prioritize the largest spend category first.");
        }
        if ("not_feasible".equals(budgetPlan.feasibility())) {
            recommendations.add("This plan is not feasible at the current budget without changing destination, duration, hotel level, or travel dates.");
        }
        recommendations.addAll(budgetPlan.tradeoffLevers());
        if (request.specialRequest() != null && !request.specialRequest().isBlank()) {
            recommendations.add("Special request captured: " + request.specialRequest());
        }

        List<AlternativeView> alternatives = buildAlternatives(request, budgetPlan);
        String proactiveTip = proactiveTip(request, budgetPlan);
        String visaNote = visaNote(request);
        List<String> nextSteps = nextSteps(request, budgetPlan);
        String delightNote = delightNote(request);
        String fallbackSummary = "Trip advisor marked this plan as " + budgetPlan.feasibility() + " and added practical recommendations.";
        String summary = aiAdvisorSummary(request, budgetPlan, fallbackSummary);
        return new AdvisorOutput(
                budgetPlan.feasibility(),
                summary,
                recommendations,
                alternatives,
                proactiveTip,
                visaNote,
                nextSteps,
                delightNote,
                new AgentResult("trip-advisor-agent", "completed", summary, "feasibility advice + tradeoffs", null, false)
        );
    }

    private String aiAdvisorSummary(TripRequest request, BudgetAgentService.BudgetPlan budgetPlan, String fallback) {
        String prompt = """
                Give a 1-2 sentence advisor verdict for this trip.
                Origin: %s
                Destination: %s
                Travelers: %d
                Budget: ₹%d
                Planning total: ₹%d
                Feasibility: %s
                Pressure score: %d/100
                Seasonality: %s
                Do not claim booking is confirmed.
                """.formatted(
                request.origin(),
                request.destination(),
                request.travelers(),
                request.budget(),
                budgetPlan.planningTotal(),
                budgetPlan.feasibility(),
                budgetPlan.pressureScore(),
                budgetPlan.seasonality()
        );
        return aiProviderService.enrichAgentOutput("trip-advisor-agent", prompt, fallback);
    }

    private List<AlternativeView> buildAlternatives(TripRequest request, BudgetAgentService.BudgetPlan budgetPlan) {
        int shorterStayBudget = Math.max(1_000, (int) Math.round(budgetPlan.planningTotal() * 0.82));
        int lowerComfortBudget = Math.max(1_000, (int) Math.round(budgetPlan.planningTotal() * 0.9));
        return List.of(
                new AlternativeView(
                        request.destination() + " shorter stay",
                        "Keep the same destination but reduce one or two nights if live prices create budget pressure.",
                        shorterStayBudget
                ),
                new AlternativeView(
                        request.destination() + " lower-comfort version",
                        "Keep the same route and dates but optimize hotel category, local transport, and paid activities.",
                        lowerComfortBudget
                )
        );
    }

    private String proactiveTip(TripRequest request, BudgetAgentService.BudgetPlan budgetPlan) {
        String destination = request.destination().toLowerCase();
        if (destination.contains("bali") || destination.contains("paris") || destination.contains("london")) {
            return "You could save roughly ₹8,000–12,000 by checking one-stop routes and mid-week departures before locking flights.";
        }
        if ("tight".equalsIgnoreCase(budgetPlan.feasibility())) {
            return "You could unlock a more comfortable plan by shifting one week earlier or trimming one night from the stay.";
        }
        return "If live fares jump, compare flights 1–2 days earlier or mid-week before changing the whole trip.";
    }

    private String visaNote(TripRequest request) {
        String destination = request.destination().toLowerCase();
        if (destination.contains("bali") || destination.contains("thailand") || destination.contains("maldives")) {
            return "Visa rules can change, but this destination is often planned as visa-on-arrival or simplified-entry for Indian travelers. Check the official embassy or immigration site before booking.";
        }
        if (destination.contains("paris") || destination.contains("london") || destination.contains("switzerland")) {
            return "This destination may require an advance visa for Indian travelers. Check appointment timelines and official embassy guidance before locking non-refundable bookings.";
        }
        return "Check passport validity and destination entry rules before booking. Use the official embassy or immigration website as the source of truth.";
    }

    private List<String> nextSteps(TripRequest request, BudgetAgentService.BudgetPlan budgetPlan) {
        return List.of(
                "Check live flight prices now and compare them against the estimate before changing the whole trip.",
                "Confirm passport validity and visa requirements for " + request.destination() + " before paying for non-refundable bookings.",
                ("tight".equalsIgnoreCase(budgetPlan.feasibility())
                        ? "Lock flexible accommodation first so you keep room to absorb supplier price changes."
                        : "Lock accommodation in the preferred area once live pricing still fits the plan.")
        );
    }

    private String delightNote(TripRequest request) {
        String destination = request.destination().toLowerCase();
        if (destination.contains("jaipur") && request.departureDate().contains("-03-")) {
            return "If your dates align with Holi in Jaipur, it can become one of the most memorable cultural moments of the whole trip.";
        }
        if (destination.contains("goa")) {
            return "Goa works best when you treat each day as one area at a time instead of zig-zagging across the coast.";
        }
        if (destination.contains("bali")) {
            return "Bali usually feels best when you split your days between one cultural inland block and one lighter coastal block.";
        }
        return "The strongest version of this trip usually comes from one anchor experience each day and one flexible local block around it.";
    }

    public record AdvisorOutput(
            String verdict,
            String summary,
            List<String> recommendations,
            List<AlternativeView> alternatives,
            String proactiveTip,
            String visaNote,
            List<String> nextSteps,
            String delightNote,
            AgentResult agentResult
    ) {
    }
}
