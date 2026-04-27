package com.tripmind.tripmind.service.agent;

import com.tripmind.tripmind.dto.BookingLinkView;
import com.tripmind.tripmind.model.AgentResult;
import com.tripmind.tripmind.model.TripRequest;
import com.tripmind.tripmind.service.ai.AiProviderService;
import com.tripmind.tripmind.service.provider.BookingLinkService;
import com.tripmind.tripmind.service.provider.PricingEngineService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class HotelAgentService {

    private final PricingEngineService pricingEngineService;
    private final BookingLinkService bookingLinkService;
    private final AiProviderService aiProviderService;

    public HotelAgentService(
            PricingEngineService pricingEngineService,
            BookingLinkService bookingLinkService,
            AiProviderService aiProviderService
    ) {
        this.pricingEngineService = pricingEngineService;
        this.bookingLinkService = bookingLinkService;
        this.aiProviderService = aiProviderService;
    }

    public HotelOutput search(TripRequest request) {
        return search(request, null, 0);
    }

    public HotelOutput search(TripRequest request, BudgetAgentService.BudgetPlan budgetPlan, int spentBeforeHotels) {
        int nights = (int) Math.max(1, ChronoUnit.DAYS.between(LocalDate.parse(request.departureDate()), LocalDate.parse(request.returnDate())));
        int rooms = Math.max(1, (request.travelers() + 1) / 2);
        int nightly = pricingEngineService.estimateHotelNightly(request.destination(), request.departureDate(), request.vibe());
        int total = nightly * nights * rooms;
        int allocatedBudget = budgetPlan == null
                ? Math.max(total, (int) Math.round(request.budget() * 0.45))
                : budgetPlan.categoryBudgets().getOrDefault("hotels", total);
        int remainingAfterHotels = request.budget() - spentBeforeHotels - total;
        String summary = "Prepared hotel recommendation context for "
                + request.destination() + " using remaining budget after flights.";
        String stayStyle = "budget".equalsIgnoreCase(request.vibe()) ? "Budget-friendly stay strategy" : "Comfortable stay strategy";
        String aiRecommendation = hotelRecommendation(request, nightly, total, allocatedBudget, remainingAfterHotels);
        List<BookingLinkView> bookingLinks = bookingLinkService.hotelLinks(request);
        return new HotelOutput(
                nights,
                rooms,
                nightly,
                total,
                allocatedBudget,
                remainingAfterHotels,
                stayStyle,
                aiRecommendation + " This is still an estimate until you verify live inventory and taxes on the booking partner page.",
                "estimated hotel planning with live booking handoff",
                bookingLinks,
                new AgentResult("hotel-agent", "completed", summary, "stay strategy + hotel booking paths", null, false)
        );
    }

    public HotelOutput fallback(TripRequest request, BudgetAgentService.BudgetPlan budgetPlan, int spentBeforeHotels, String reason) {
        HotelOutput output = search(request, budgetPlan, spentBeforeHotels);
        return new HotelOutput(
                output.nights(),
                output.rooms(),
                output.estimatedNightly(),
                output.estimatedTotal(),
                output.allocatedBudget(),
                output.remainingAfterHotels(),
                output.stayStyle(),
                output.aiRecommendation(),
                "degraded heuristic hotel fallback",
                output.bookingLinks(),
                output.agentResult().degraded("Fallback used: " + reason)
        );
    }

    private String hotelRecommendation(
            TripRequest request,
            int nightly,
            int total,
            int allocatedBudget,
            int remainingAfterHotels
    ) {
        String areaGuidance = recommendedArea(request.destination(), request.vibe());
        String fallback = "Look for a " + request.vibe()
                + " stay in " + areaGuidance
                + " around ₹" + nightly + " per room/night. Treat this as a market estimate, not live hotel inventory. Confirm live inventory, taxes, and cancellation rules on the booking partner page.";
        String prompt = """
                Create a hotel planning recommendation, not live hotel inventory.
                Destination: %s
                Travelers: %d
                Dates: %s to %s
                Vibe: %s
                Suggested area strategy: %s
                Estimated nightly per room: ₹%d
                Estimated hotel total: ₹%d
                Hotel budget lane: ₹%d
                Remaining after hotels: ₹%d
                Mention area strategy, neighborhood guidance, cancellation, and booking verification. Do not invent confirmed availability.
                """.formatted(
                request.destination(),
                request.travelers(),
                request.departureDate(),
                request.returnDate(),
                request.vibe(),
                areaGuidance,
                nightly,
                total,
                allocatedBudget,
                remainingAfterHotels
        );
        return aiProviderService.enrichAgentOutput("hotel-agent", prompt, fallback);
    }

    private String recommendedArea(String destination, String vibe) {
        String signals = ((destination == null ? "" : destination) + " " + (vibe == null ? "" : vibe)).toLowerCase();
        if (signals.contains("goa")) {
            return "Candolim or Calangute for beach access, or Panjim for a calmer city base";
        }
        if (signals.contains("dubai")) {
            return "Downtown Dubai for landmark access, or Dubai Marina for a more leisure-led stay";
        }
        if (signals.contains("paris")) {
            return "the central Paris / Latin Quarter side for transit access and easy sightseeing";
        }
        if (signals.contains("bangkok")) {
            return "Sukhumvit for connectivity, or Riverside / Old City for culture-focused stays";
        }
        if (signals.contains("beach") || signals.contains("island")) {
            return "a well-connected beachfront or central leisure district with easy evening access";
        }
        return "a well-connected central district so sightseeing and transfers stay practical";
    }

    public record HotelOutput(
            Integer nights,
            Integer rooms,
            Integer estimatedNightly,
            Integer estimatedTotal,
            Integer allocatedBudget,
            Integer remainingAfterHotels,
            String stayStyle,
            String aiRecommendation,
            String source,
            List<BookingLinkView> bookingLinks,
            AgentResult agentResult
    ) {
    }
}
