package com.tripmind.tripmind.service.agent;

import com.tripmind.tripmind.dto.BookingLinkView;
import com.tripmind.tripmind.model.AgentResult;
import com.tripmind.tripmind.model.TripRequest;
import com.tripmind.tripmind.service.provider.BookingLinkService;
import com.tripmind.tripmind.service.provider.PricingEngineService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FlightAgentService {

    private final PricingEngineService pricingEngineService;
    private final BookingLinkService bookingLinkService;

    public FlightAgentService(PricingEngineService pricingEngineService, BookingLinkService bookingLinkService) {
        this.pricingEngineService = pricingEngineService;
        this.bookingLinkService = bookingLinkService;
    }

    public FlightOutput search(TripRequest request) {
        return search(request, null);
    }

    public FlightOutput search(TripRequest request, BudgetAgentService.BudgetPlan budgetPlan) {
        int estimatedTotal = pricingEngineService.estimateFlightCost(request.origin(), request.destination(), request.travelers());
        int allocatedBudget = budgetPlan == null
                ? Math.max(estimatedTotal, (int) Math.round(request.budget() * 0.35))
                : budgetPlan.categoryBudgets().getOrDefault("flights", estimatedTotal);
        int remainingAfterFlights = request.budget() - estimatedTotal;
        List<BookingLinkView> bookingLinks = bookingLinkService.flightLinks(request);
        String routeGuidance = pricingEngineService.routeSupportGuidance(request.origin(), request.destination());
        String summary = "Prepared flight estimate and booking-path context for "
                + request.origin() + " to " + request.destination()
                + " using a flight budget lane of ₹" + allocatedBudget + ". " + routeGuidance;
        return new FlightOutput(
                request.origin().toUpperCase() + " → " + request.destination().toUpperCase(),
                "Economy",
                estimatedTotal,
                allocatedBudget,
                remainingAfterFlights,
                "market-estimate",
                "Open live search on booking partners with route, trip type, and dates carried forward. " + routeGuidance,
                "centralized heuristic pricing engine",
                bookingLinks,
                new AgentResult("flight-agent", "completed", summary, "route pricing + booking paths", null, false)
        );
    }

    public FlightOutput fallback(TripRequest request, BudgetAgentService.BudgetPlan budgetPlan, String reason) {
        FlightOutput output = search(request, budgetPlan);
        return new FlightOutput(
                output.route(),
                output.cabin(),
                output.estimatedTotal(),
                output.allocatedBudget(),
                output.remainingAfterFlights(),
                output.estimateType(),
                output.bookingPath(),
                "degraded heuristic flight fallback",
                output.bookingLinks(),
                output.agentResult().degraded("Fallback used: " + reason)
        );
    }

    public record FlightOutput(
            String route,
            String cabin,
            Integer estimatedTotal,
            Integer allocatedBudget,
            Integer remainingAfterFlights,
            String estimateType,
            String bookingPath,
            String source,
            List<BookingLinkView> bookingLinks,
            AgentResult agentResult
    ) {
    }
}
