package com.tripmind.tripmind.service.agent;

import com.tripmind.tripmind.dto.ItineraryDayView;
import com.tripmind.tripmind.model.AgentResult;
import com.tripmind.tripmind.model.TripRequest;
import com.tripmind.tripmind.service.ai.AiProviderService;
import com.tripmind.tripmind.service.provider.PlaceDiscoveryService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class ItineraryAgentService {

    private final PlaceDiscoveryService placeDiscoveryService;
    private final AiProviderService aiProviderService;

    public ItineraryAgentService(PlaceDiscoveryService placeDiscoveryService, AiProviderService aiProviderService) {
        this.placeDiscoveryService = placeDiscoveryService;
        this.aiProviderService = aiProviderService;
    }

    public ItineraryOutput build(TripRequest request, String feasibility) {
        return build(request, null, null, null, null);
    }

    public ItineraryOutput build(
            TripRequest request,
            BudgetAgentService.BudgetPlan budgetPlan,
            FlightAgentService.FlightOutput flightOutput,
            HotelAgentService.HotelOutput hotelOutput,
            WeatherAgentService.WeatherOutput weatherOutput
    ) {
        String feasibility = budgetPlan == null ? "planned" : budgetPlan.feasibility();
        int duration = (int) Math.max(1, ChronoUnit.DAYS.between(LocalDate.parse(request.departureDate()), LocalDate.parse(request.returnDate())));
        PlaceDiscoveryService.PlaceDiscovery placeDiscovery = placeDiscoveryService.discover(request.destination(), request.vibe(), Math.min(10, Math.max(4, duration + 2)));
        List<PlaceDiscoveryService.PlaceSuggestion> realPlaces = placeDiscovery.places();
        boolean areaBased = placeDiscovery.areaBased();
        List<String> highlights = new ArrayList<>();
        List<ItineraryDayView> days = new ArrayList<>();
        LocalDate start = LocalDate.parse(request.departureDate());
        String budgetNarrative = budgetNarrative(request, budgetPlan, flightOutput, hotelOutput);

        for (int day = 1; day <= duration; day++) {
            ItineraryDayView dayPlan = buildDayPlan(request, feasibility, start.plusDays(day - 1), day, duration, realPlaces, areaBased, weatherOutput);
            days.add(dayPlan);
            highlights.add("Day " + day + ": " + dayPlan.theme() + " - " + dayPlan.evening());
        }

        String preview = request.destination() + " looks " + feasibility
                + " for " + request.travelers() + " traveler(s). The itinerary balances arrival, anchor experiences, booking buffers, and "
                + (request.specialRequest() == null || request.specialRequest().isBlank() ? "your travel style." : request.specialRequest() + ".")
                + (!realPlaces.isEmpty()
                ? areaBased
                    ? " TripMind switched to area-based planning because the raw POIs were too weak or repetitive."
                    : " Real places were pulled from " + placeDiscovery.source() + "."
                : " Live place APIs were unavailable, so template planning was used.")
                + (weatherOutput == null ? "" : " Weather signal: " + weatherOutput.summary());
        String aiNarrative = itineraryNarrative(request, budgetNarrative, realPlaces, placeDiscovery.source(), weatherOutput, preview);
        return new ItineraryOutput(
                preview,
                budgetNarrative,
                aiNarrative,
                highlights,
                days,
                realPlaces.stream().map(PlaceDiscoveryService.PlaceSuggestion::name).toList(),
                placeDiscovery.source(),
                new AgentResult("itinerary-agent", "completed", "Built a duration-aware itinerary with real place discovery from " + placeDiscovery.source() + ".", "day-by-day synthesis", null, false)
        );
    }

    public ItineraryOutput revisionOnly(TripRequest request, BudgetAgentService.BudgetPlan budgetPlan) {
        List<String> highlights = List.of(
                "Revise budget, trip length, or hotel level before generating a day-by-day schedule.",
                "Use the booking links only to compare price pressure, not to treat this plan as ready.",
                "Re-run planning after budget or destination tradeoffs are confirmed."
        );
        List<ItineraryDayView> days = List.of(
                new ItineraryDayView(
                        1,
                        request.departureDate(),
                        "Revision-first planning state",
                        "TripMind paused detailed itinerary generation because the current trip is not feasible.",
                        "Adjust the route, dates, hotel level, or budget first.",
                        "Only continue to live booking checks after feasibility improves.",
                        "This is a revision state, not a travel-ready schedule.",
                        highlights
                )
        );
        return new ItineraryOutput(
                request.destination() + " is currently in revision-first planning mode because the budget verdict is not feasible.",
                "TripMind skipped normal itinerary generation until the budget conflict is resolved.",
                "No detailed itinerary was generated because the current plan is not feasible. Re-run after changing budget, duration, or trip style.",
                highlights,
                days,
                List.of(),
                "revision-first fallback",
                new AgentResult("itinerary-agent", "degraded", "Skipped full itinerary generation because the trip is not feasible at the current budget.", "day-by-day synthesis", null, true)
        );
    }

    private String itineraryNarrative(
            TripRequest request,
            String budgetNarrative,
            List<PlaceDiscoveryService.PlaceSuggestion> realPlaces,
            String placeSource,
            WeatherAgentService.WeatherOutput weatherOutput,
            String fallback
    ) {
        String prompt = """
                Write a concise itinerary overview for the generated trip.
                Destination: %s
                Origin: %s
                Travelers: %d
                Dates: %s to %s
                Vibe: %s
                Traveler needs: %s
                Budget context: %s
                Weather signal: %s
                Place source: %s
                Places used: %s
                Open with a short trip character line that gives the destination some personality.
                Keep it practical and transparent. Use the weather signal when suggesting activity mix. Respect traveler needs as hard constraints. Do not invent confirmed bookings.
                """.formatted(
                request.destination(),
                request.origin(),
                request.travelers(),
                request.departureDate(),
                request.returnDate(),
                request.vibe(),
                request.travelerNeeds() == null ? "none" : request.travelerNeeds(),
                budgetNarrative,
                weatherOutput == null ? "weather context pending" : weatherOutput.summary(),
                placeSource,
                realPlaces.stream().map(PlaceDiscoveryService.PlaceSuggestion::name).limit(8).toList()
        );
        return aiProviderService.enrichAgentOutput("itinerary-agent", prompt, fallback);
    }

    private String budgetNarrative(
            TripRequest request,
            BudgetAgentService.BudgetPlan budgetPlan,
            FlightAgentService.FlightOutput flightOutput,
            HotelAgentService.HotelOutput hotelOutput
    ) {
        if (budgetPlan == null || flightOutput == null || hotelOutput == null) {
            return "Itinerary was built from available trip context.";
        }
        int afterFlights = flightOutput.remainingAfterFlights();
        int afterHotels = hotelOutput.remainingAfterHotels();
        return "Budget context carried through agents: total budget ₹" + request.budget()
                + ", flight estimate ₹" + flightOutput.estimatedTotal()
                + " leaves ₹" + afterFlights
                + ", hotel estimate ₹" + hotelOutput.estimatedTotal()
                + " leaves ₹" + afterHotels
                + ", with pressure score " + budgetPlan.pressureScore() + "/100."
                + (request.travelerNeeds() == null || request.travelerNeeds().isBlank() ? "" : " Special traveler needs captured: " + request.travelerNeeds() + ".");
    }

    private ItineraryDayView buildDayPlan(
            TripRequest request,
            String feasibility,
            LocalDate date,
            int day,
            int duration,
            List<PlaceDiscoveryService.PlaceSuggestion> realPlaces,
            boolean areaBased,
            WeatherAgentService.WeatherOutput weatherOutput
    ) {
        String vibe = request.vibe() == null ? "" : request.vibe().toLowerCase();
        String destination = request.destination();
        boolean tightBudget = "tight".equalsIgnoreCase(feasibility);
        boolean rainRisk = weatherOutput != null && "rain-risk".equalsIgnoreCase(weatherOutput.seasonSignal());
        boolean hotSeason = weatherOutput != null && "hot-season".equalsIgnoreCase(weatherOutput.seasonSignal());
        boolean toddlerFriendly = request.travelerNeeds() != null && (request.travelerNeeds().contains("toddler") || request.travelerNeeds().contains("infant") || request.travelerNeeds().contains("kid"));
        boolean vegetarian = request.travelerNeeds() != null && (request.travelerNeeds().contains("vegetarian") || request.travelerNeeds().contains("vegan"));
        boolean halal = request.travelerNeeds() != null && request.travelerNeeds().contains("halal");
        String primaryPlace = placeName(realPlaces, day - 1, areaBased ? "the main city area" : "the main destination area");
        String secondaryPlace = placeName(realPlaces, day, areaBased ? "a second city area" : "a nearby local area");
        String foodOrEveningPlace = placeName(realPlaces, day + 1, areaBased ? "a food and evening area" : "a local food or evening district");
        String afternoonQualifier = rainRisk
                ? "Keep this block indoor-friendly or close to covered local areas if rain builds."
                : hotSeason
                    ? "Use this block for lighter activity and keep a shade or indoor reset nearby."
                    : "Keep this block flexible for weather and transit changes.";

        if (day == 1) {
            return new ItineraryDayView(
                day,
                date.toString(),
                "Arrival and local orientation",
                "Arrive from " + request.origin() + ", check in, and keep the first half light.",
                areaBased
                            ? "Use " + primaryPlace + " as a light first-day zone, check transit options, and keep SIM/payment setup practical. " + afternoonQualifier
                            : "Explore around " + primaryPlace + ", check transit options, and keep SIM/payment setup practical. " + afternoonQualifier,
                eveningPlan(destination, vibe, "low-pressure welcome walk near " + secondaryPlace + " and dinner", toddlerFriendly, vegetarian, halal),
                tightBudget ? "Avoid paid attractions on arrival day; save budget for the main experience days." : "Keep this day flexible because arrival delays can shift the plan.",
                bookingHints("Confirm airport transfer or metro route", "Check hotel check-in timing", rainRisk ? "Keep rain gear ready for arrival day" : "Keep dinner near " + primaryPlace + " optional", realPlaces, areaBased)
            );
        }

        if (day == duration) {
            return new ItineraryDayView(
                    day,
                    date.toString(),
                    "Departure buffer and relaxed wrap-up",
                    "Pack, check out, and keep luggage storage or late checkout ready.",
                    areaBased
                            ? "Choose one low-risk block inside " + primaryPlace + " instead of crossing the full city."
                            : "Choose one nearby low-risk activity around " + primaryPlace + " instead of crossing the city.",
                    "Reach the airport/station early with a backup cab or transit option.",
                    "Do not schedule expensive non-refundable activities on the final day.",
                    bookingHints("Reconfirm return flight/train timing", "Keep transport buffer", "Avoid prepaid final-day attractions", realPlaces, areaBased)
            );
        }

        if (day == 2) {
            return new ItineraryDayView(
                    day,
                date.toString(),
                "Signature sightseeing and destination highlights",
                areaBased
                            ? "Start early with " + primaryPlace + " as the day’s main sightseeing zone."
                            : "Start early with " + primaryPlace + " as the main landmark or sightseeing anchor.",
                areaBased
                            ? "Add " + secondaryPlace + " as a second district if time, weather, and budget allow. " + afternoonQualifier
                            : "Add " + secondaryPlace + " as the second experience if time, weather, and budget allow. " + afternoonQualifier,
                eveningPlan(destination, vibe, foodOrEveningPlace + " for local market, waterfront, or food district time", toddlerFriendly, vegetarian, halal),
                    tightBudget ? "Pick one paid anchor experience, then use free walking areas around it." : rainRisk ? "Prefer one covered anchor experience and keep an indoor backup nearby." : "This is the best day for the main paid attraction or guided experience.",
                    bookingHints("Pre-book " + primaryPlace + " if slots sell out", "Check opening hours", rainRisk ? "Keep one indoor fallback around " + secondaryPlace : "Keep route to " + secondaryPlace + " ready", realPlaces, areaBased)
            );
        }

        if (day == 3 && duration >= 4) {
            return new ItineraryDayView(
                    day,
                    date.toString(),
                    vibe.contains("nightlife") ? "Food, shopping, and nightlife focus" : "Culture, food, and flexible exploration",
                    areaBased
                            ? "Use the morning for a slower block inside " + primaryPlace + "."
                            : "Use the morning for a slower visit around " + primaryPlace + ".",
                areaBased
                            ? "Keep a flexible district block for " + secondaryPlace + " based on energy and live weather. " + afternoonQualifier
                            : "Keep a flexible block for " + secondaryPlace + " based on energy and live weather. " + afternoonQualifier,
                eveningPlan(destination, vibe, vibe.contains("nightlife") ? foodOrEveningPlace + " with safe return transport" : foodOrEveningPlace + " for relaxed dinner and optional evening activity", toddlerFriendly, vegetarian, halal),
                    tightBudget ? "Use this as the low-cost balancing day: public transport, street food, and free areas." : "Good day to add a mid-priced experience without overloading the trip.",
                    bookingHints("Shortlist food near " + foodOrEveningPlace, "Mark safe return route", rainRisk ? "Carry rain cover for evening movement" : "Avoid overbooking back-to-back activities", realPlaces, areaBased)
            );
        }

        return new ItineraryDayView(
                day,
                date.toString(),
                "Flexible experience day",
                areaBased
                        ? "Start with " + primaryPlace + " so the plan has one strong area anchor without feeling rushed."
                        : "Start with " + primaryPlace + " so the plan has one real anchor without feeling rushed.",
                areaBased
                        ? "Choose " + secondaryPlace + " if weather and budget still look comfortable. " + afternoonQualifier
                        : "Choose " + secondaryPlace + " if weather and budget still look comfortable. " + afternoonQualifier,
                eveningPlan(destination, vibe, foodOrEveningPlace + " for easy dinner plus optional local activity", toddlerFriendly, vegetarian, halal),
                tightBudget ? "Use this day to control overspend and keep meals/transport practical." : "Keep one flexible slot so live prices and weather do not break the plan.",
                bookingHints("Review weather before leaving", rainRisk ? "Keep one covered backup option" : "Keep one backup indoor option", "Book only if cancellation is flexible", realPlaces, areaBased)
        );
    }

    private String placeName(List<PlaceDiscoveryService.PlaceSuggestion> places, int index, String fallback) {
        if (places.isEmpty()) {
            return fallback;
        }
        return places.get(Math.floorMod(index, places.size())).name();
    }

    private List<String> bookingHints(String first, String second, String third, List<PlaceDiscoveryService.PlaceSuggestion> places, boolean areaBased) {
        List<String> hints = new ArrayList<>(List.of(first, second, third));
        if (!places.isEmpty()) {
            hints.add(areaBased
                    ? "TripMind switched to area-based planning because raw POIs were too weak or repetitive."
                    : "Place source: " + places.getFirst().source() + " and/or connected POI provider");
        }
        return hints;
    }

    private String eveningPlan(String destination, String vibe, String fallback, boolean toddlerFriendly, boolean vegetarian, boolean halal) {
        if (toddlerFriendly) {
            return "Keep the evening gentle with an early dinner, short walk, and toddler-friendly timing.";
        }
        if (vibe.contains("nightlife")) {
            return "Explore a nightlife-friendly area in " + destination + " with a planned safe return.";
        }
        if (vibe.contains("family")) {
            return "Keep the evening family-friendly with dinner, a short walk, and early rest.";
        }
        if (vegetarian || halal) {
            return "Choose a " + (vegetarian ? "vegetarian-friendly" : "halal-friendly") + " dinner area and keep transfers simple.";
        }
        if (vibe.contains("budget")) {
            return "Choose a budget-friendly local food area and avoid expensive late-night transfers.";
        }
        return fallback + ".";
    }

    public record ItineraryOutput(
            String preview,
            String budgetNarrative,
            String aiNarrative,
            List<String> dayHighlights,
            List<ItineraryDayView> days,
            List<String> realPlaces,
            String placeSource,
            AgentResult agentResult
    ) {
    }
}
