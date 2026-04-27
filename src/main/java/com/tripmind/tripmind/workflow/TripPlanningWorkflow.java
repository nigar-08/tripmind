package com.tripmind.tripmind.workflow;

import com.tripmind.tripmind.dto.AdvisorView;
import com.tripmind.tripmind.dto.BudgetView;
import com.tripmind.tripmind.dto.FlightView;
import com.tripmind.tripmind.dto.HotelView;
import com.tripmind.tripmind.dto.ItineraryView;
import com.tripmind.tripmind.dto.PlanningIntelligenceView;
import com.tripmind.tripmind.dto.WeatherView;
import com.tripmind.tripmind.model.AgentResult;
import com.tripmind.tripmind.model.PlanningSnapshot;
import com.tripmind.tripmind.model.TripRequest;
import com.tripmind.tripmind.service.agent.BudgetAgentService;
import com.tripmind.tripmind.service.agent.FlightAgentService;
import com.tripmind.tripmind.service.agent.HotelAgentService;
import com.tripmind.tripmind.service.agent.ItineraryAgentService;
import com.tripmind.tripmind.service.agent.TripAdvisorAgentService;
import com.tripmind.tripmind.service.agent.WeatherAgentService;
import com.tripmind.tripmind.service.AgentTaskBus;
import com.tripmind.tripmind.service.DestinationArchetypeService;
import com.tripmind.tripmind.service.WorkflowEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class TripPlanningWorkflow {

    private final BudgetAgentService budgetAgentService;
    private final FlightAgentService flightAgentService;
    private final HotelAgentService hotelAgentService;
    private final WeatherAgentService weatherAgentService;
    private final TripAdvisorAgentService tripAdvisorAgentService;
    private final ItineraryAgentService itineraryAgentService;
    private final WorkflowEventPublisher workflowEventPublisher;
    private final AgentTaskBus agentTaskBus;
    private final DestinationArchetypeService destinationArchetypeService;

    public TripPlanningWorkflow(
            BudgetAgentService budgetAgentService,
            FlightAgentService flightAgentService,
            HotelAgentService hotelAgentService,
            WeatherAgentService weatherAgentService,
            TripAdvisorAgentService tripAdvisorAgentService,
            ItineraryAgentService itineraryAgentService,
            WorkflowEventPublisher workflowEventPublisher,
            AgentTaskBus agentTaskBus,
            DestinationArchetypeService destinationArchetypeService
    ) {
        this.budgetAgentService = budgetAgentService;
        this.flightAgentService = flightAgentService;
        this.hotelAgentService = hotelAgentService;
        this.weatherAgentService = weatherAgentService;
        this.tripAdvisorAgentService = tripAdvisorAgentService;
        this.itineraryAgentService = itineraryAgentService;
        this.workflowEventPublisher = workflowEventPublisher;
        this.agentTaskBus = agentTaskBus;
        this.destinationArchetypeService = destinationArchetypeService;
    }

    public PlanningSnapshot execute(TripRequest request) {
        List<AgentResult> stages = new ArrayList<>();
        workflowEventPublisher.workflowStarted(request);

        workflowEventPublisher.agentStarted(request, "budget-agent", "Checking trip feasibility and spend split.");
        BudgetAgentService.BudgetPlan budgetPlan = budgetAgentService.analyze(request);
        workflowEventPublisher.agentCompleted(request, budgetPlan.agentResult());

        CompletableFuture<FlightAgentService.FlightOutput> flightFuture =
                CompletableFuture.supplyAsync(() -> {
                    workflowEventPublisher.agentStarted(request, "flight-agent", "Preparing route, flight estimate, and budget lane context.");
                    return agentTaskBus.dispatch(
                            request,
                            "flight-agent",
                            "flight.search",
                            () -> flightAgentService.search(request, budgetPlan),
                            FlightAgentService.FlightOutput::agentResult,
                            output -> Map.of(
                                    "route", output.route(),
                                    "estimatedTotal", String.valueOf(output.estimatedTotal()),
                                    "allocatedBudget", String.valueOf(output.allocatedBudget()),
                                    "remainingAfterFlights", String.valueOf(output.remainingAfterFlights()),
                                    "source", output.source()
                            )
                    );
                })
                .completeOnTimeout(flightAgentService.fallback(request, budgetPlan, "agent timeout"), 4, java.util.concurrent.TimeUnit.SECONDS)
                .exceptionally(error -> flightAgentService.fallback(request, budgetPlan, shortReason(error)));
        CompletableFuture<WeatherAgentService.WeatherOutput> weatherFuture =
                CompletableFuture.supplyAsync(() -> {
                    workflowEventPublisher.agentStarted(request, "weather-agent", "Preparing weather context.");
                    return agentTaskBus.dispatch(
                            request,
                            "weather-agent",
                            "weather.forecast",
                            () -> weatherAgentService.forecast(request),
                            WeatherAgentService.WeatherOutput::agentResult,
                            output -> Map.of(
                                    "seasonSignal", output.seasonSignal(),
                                    "source", output.source()
                            )
                    );
                })
                .completeOnTimeout(weatherAgentService.fallback(request, "agent timeout"), 4, java.util.concurrent.TimeUnit.SECONDS)
                .exceptionally(error -> weatherAgentService.fallback(request, shortReason(error)));

        FlightAgentService.FlightOutput flightOutput = flightFuture.join();
        publishFinalAgentState(request, flightOutput.agentResult());

        CompletableFuture<HotelAgentService.HotelOutput> hotelFuture =
                CompletableFuture.supplyAsync(() -> {
                    workflowEventPublisher.agentStarted(request, "hotel-agent", "Preparing hotel stay strategy from remaining budget.");
                    return agentTaskBus.dispatch(
                            request,
                            "hotel-agent",
                            "hotel.search",
                            () -> hotelAgentService.search(request, budgetPlan, flightOutput.estimatedTotal()),
                            HotelAgentService.HotelOutput::agentResult,
                            output -> Map.of(
                                    "nights", String.valueOf(output.nights()),
                                    "estimatedTotal", String.valueOf(output.estimatedTotal()),
                                    "allocatedBudget", String.valueOf(output.allocatedBudget()),
                                    "remainingAfterHotels", String.valueOf(output.remainingAfterHotels()),
                                    "source", output.source()
                            )
                    );
                })
                .completeOnTimeout(hotelAgentService.fallback(request, budgetPlan, flightOutput.estimatedTotal(), "agent timeout"), 4, java.util.concurrent.TimeUnit.SECONDS)
                .exceptionally(error -> hotelAgentService.fallback(request, budgetPlan, flightOutput.estimatedTotal(), shortReason(error)));

        HotelAgentService.HotelOutput hotelOutput = hotelFuture.join();
        WeatherAgentService.WeatherOutput weatherOutput = weatherFuture.join();

        stages.add(budgetPlan.agentResult());
        stages.add(flightOutput.agentResult());
        stages.add(hotelOutput.agentResult());
        stages.add(weatherOutput.agentResult());
        publishFinalAgentState(request, hotelOutput.agentResult());
        publishFinalAgentState(request, weatherOutput.agentResult());

        if ("not_feasible".equalsIgnoreCase(budgetPlan.feasibility())) {
            workflowEventPublisher.agentStarted(request, "trip-advisor-agent", "Adding revision-first advice because the budget is infeasible.");
            TripAdvisorAgentService.AdvisorOutput advisorOutput = tripAdvisorAgentService.advise(request, budgetPlan);
            ItineraryAgentService.ItineraryOutput itineraryOutput = itineraryAgentService.revisionOnly(request, budgetPlan);
            stages.add(advisorOutput.agentResult());
            stages.add(itineraryOutput.agentResult());
            workflowEventPublisher.agentCompleted(request, advisorOutput.agentResult());
            workflowEventPublisher.agentDegraded(request, itineraryOutput.agentResult());
            stages.sort(Comparator.comparingInt(result -> stageOrder(result.agent())));

            Map<String, String> dataSources = new LinkedHashMap<>();
            dataSources.put("pricing", "centralized heuristic pricing engine");
            dataSources.put("weather", weatherOutput.source());
            dataSources.put("booking", "booking partner integration placeholder");

            PlanningIntelligenceView planningIntelligence = buildPlanningIntelligence(
                    request,
                    budgetPlan,
                    flightOutput,
                    hotelOutput,
                    weatherOutput,
                    itineraryOutput
            );

            PlanningSnapshot snapshot = new PlanningSnapshot(
                    budgetPlan.feasibility(),
                    budgetPlan.summary(),
                    planningIntelligence,
                    new BudgetView(
                            budgetPlan.feasibility(),
                            budgetPlan.confidence(),
                            budgetPlan.seasonality(),
                            budgetPlan.totalBudget(),
                            budgetPlan.estimatedTripCost(),
                            budgetPlan.contingencyReserve(),
                            budgetPlan.planningTotal(),
                            budgetPlan.remainingAfterEstimate(),
                            budgetPlan.budgetPerPerson(),
                            budgetPlan.dailyPerPerson(),
                            budgetPlan.shortfall(),
                            budgetPlan.pressureScore(),
                            budgetPlan.costBreakdown(),
                            budgetPlan.categoryBudgets(),
                            budgetPlan.categoryPercentages(),
                            budgetPlan.tradeoffLevers()
                    ),
                    new FlightView(
                            flightOutput.route(),
                            flightOutput.cabin(),
                            flightOutput.estimatedTotal(),
                            flightOutput.allocatedBudget(),
                            flightOutput.remainingAfterFlights(),
                            flightOutput.estimateType(),
                            flightOutput.bookingPath(),
                            flightOutput.source(),
                            flightOutput.bookingLinks()
                    ),
                    new HotelView(
                            hotelOutput.nights(),
                            hotelOutput.rooms(),
                            hotelOutput.estimatedNightly(),
                            hotelOutput.estimatedTotal(),
                            hotelOutput.allocatedBudget(),
                            hotelOutput.remainingAfterHotels(),
                            hotelOutput.stayStyle(),
                            hotelOutput.aiRecommendation(),
                            hotelOutput.source(),
                            hotelOutput.bookingLinks()
                    ),
                    new WeatherView(
                            weatherOutput.summary(),
                            weatherOutput.seasonSignal(),
                            weatherOutput.source(),
                            weatherOutput.travelTiming(),
                            weatherOutput.packingList()
                    ),
                    new AdvisorView(
                            advisorOutput.verdict(),
                            advisorOutput.summary(),
                            advisorOutput.recommendations(),
                            advisorOutput.alternatives(),
                            advisorOutput.proactiveTip(),
                            advisorOutput.visaNote(),
                            advisorOutput.nextSteps(),
                            advisorOutput.delightNote()
                    ),
                    new ItineraryView(
                            itineraryOutput.preview(),
                            itineraryOutput.budgetNarrative(),
                            itineraryOutput.aiNarrative(),
                            itineraryOutput.dayHighlights(),
                            itineraryOutput.days(),
                            itineraryOutput.realPlaces(),
                            itineraryOutput.placeSource()
                    ),
                    dataSources,
                    stages
            );
            workflowEventPublisher.workflowCompleted(request, budgetPlan.summary());
            return snapshot;
        }

        CompletableFuture<TripAdvisorAgentService.AdvisorOutput> advisorFuture =
                CompletableFuture.supplyAsync(() -> {
                    workflowEventPublisher.agentStarted(request, "trip-advisor-agent", "Adding feasibility verdict and alternatives.");
                    return tripAdvisorAgentService.advise(request, budgetPlan);
                });
        CompletableFuture<ItineraryAgentService.ItineraryOutput> itineraryFuture =
                CompletableFuture.supplyAsync(() -> {
                    workflowEventPublisher.agentStarted(request, "itinerary-agent", "Building itinerary preview.");
                    return itineraryAgentService.build(request, budgetPlan, flightOutput, hotelOutput, weatherOutput);
                });

        TripAdvisorAgentService.AdvisorOutput advisorOutput = advisorFuture.join();
        ItineraryAgentService.ItineraryOutput itineraryOutput = itineraryFuture.join();

        stages.add(advisorOutput.agentResult());
        stages.add(itineraryOutput.agentResult());
        workflowEventPublisher.agentCompleted(request, advisorOutput.agentResult());
        workflowEventPublisher.agentCompleted(request, itineraryOutput.agentResult());
        stages.sort(Comparator.comparingInt(result -> stageOrder(result.agent())));

        Map<String, String> dataSources = new LinkedHashMap<>();
        dataSources.put("pricing", "centralized heuristic pricing engine");
        dataSources.put("weather", weatherOutput.source());
        dataSources.put("booking", "booking partner integration placeholder");

        PlanningIntelligenceView planningIntelligence = buildPlanningIntelligence(
                request,
                budgetPlan,
                flightOutput,
                hotelOutput,
                weatherOutput,
                itineraryOutput
        );

        PlanningSnapshot snapshot = new PlanningSnapshot(
                budgetPlan.feasibility(),
                budgetPlan.summary(),
                planningIntelligence,
                new BudgetView(
                        budgetPlan.feasibility(),
                        budgetPlan.confidence(),
                        budgetPlan.seasonality(),
                        budgetPlan.totalBudget(),
                        budgetPlan.estimatedTripCost(),
                        budgetPlan.contingencyReserve(),
                        budgetPlan.planningTotal(),
                        budgetPlan.remainingAfterEstimate(),
                        budgetPlan.budgetPerPerson(),
                        budgetPlan.dailyPerPerson(),
                        budgetPlan.shortfall(),
                        budgetPlan.pressureScore(),
                        budgetPlan.costBreakdown(),
                        budgetPlan.categoryBudgets(),
                        budgetPlan.categoryPercentages(),
                        budgetPlan.tradeoffLevers()
                ),
                new FlightView(
                        flightOutput.route(),
                        flightOutput.cabin(),
                        flightOutput.estimatedTotal(),
                        flightOutput.allocatedBudget(),
                        flightOutput.remainingAfterFlights(),
                        flightOutput.estimateType(),
                        flightOutput.bookingPath(),
                        flightOutput.source(),
                        flightOutput.bookingLinks()
                ),
                new HotelView(
                        hotelOutput.nights(),
                        hotelOutput.rooms(),
                        hotelOutput.estimatedNightly(),
                        hotelOutput.estimatedTotal(),
                        hotelOutput.allocatedBudget(),
                        hotelOutput.remainingAfterHotels(),
                        hotelOutput.stayStyle(),
                        hotelOutput.aiRecommendation(),
                        hotelOutput.source(),
                        hotelOutput.bookingLinks()
                ),
                new WeatherView(
                        weatherOutput.summary(),
                        weatherOutput.seasonSignal(),
                        weatherOutput.source(),
                        weatherOutput.travelTiming(),
                        weatherOutput.packingList()
                ),
                new AdvisorView(
                        advisorOutput.verdict(),
                        advisorOutput.summary(),
                        advisorOutput.recommendations(),
                        advisorOutput.alternatives(),
                        advisorOutput.proactiveTip(),
                        advisorOutput.visaNote(),
                        advisorOutput.nextSteps(),
                        advisorOutput.delightNote()
                ),
                new ItineraryView(
                        itineraryOutput.preview(),
                        itineraryOutput.budgetNarrative(),
                        itineraryOutput.aiNarrative(),
                        itineraryOutput.dayHighlights(),
                        itineraryOutput.days(),
                        itineraryOutput.realPlaces(),
                        itineraryOutput.placeSource()
                ),
                dataSources,
                stages
        );
        workflowEventPublisher.workflowCompleted(request, budgetPlan.summary());
        return snapshot;
    }

    private PlanningIntelligenceView buildPlanningIntelligence(
            TripRequest request,
            BudgetAgentService.BudgetPlan budgetPlan,
            FlightAgentService.FlightOutput flightOutput,
            HotelAgentService.HotelOutput hotelOutput,
            WeatherAgentService.WeatherOutput weatherOutput,
            ItineraryAgentService.ItineraryOutput itineraryOutput
    ) {
        String archetype = destinationArchetypeService.classify(request);
        String travelerProfile = destinationArchetypeService.travelerProfile(request);
        boolean liveWeather = weatherOutput.source() != null && weatherOutput.source().toLowerCase().contains("openweather");
        boolean realPlaces = itineraryOutput.realPlaces() != null && !itineraryOutput.realPlaces().isEmpty();
        boolean degradedFlight = Boolean.TRUE.equals(flightOutput.agentResult().degraded());
        boolean degradedHotel = Boolean.TRUE.equals(hotelOutput.agentResult().degraded());
        boolean degradedWeather = Boolean.TRUE.equals(weatherOutput.agentResult().degraded());

        String planningMode = switch (budgetPlan.feasibility()) {
            case "good" -> "decision-ready planning";
            case "tight" -> "tradeoff-aware planning";
            default -> "revision-first planning";
        };

        String bookingReadiness;
        if (degradedFlight || degradedHotel) {
            bookingReadiness = "verify carefully on partner pages";
        } else if ("good".equalsIgnoreCase(budgetPlan.feasibility())) {
            bookingReadiness = "ready for live booking checks";
        } else if ("tight".equalsIgnoreCase(budgetPlan.feasibility())) {
            bookingReadiness = "bookable after tradeoff review";
        } else {
            bookingReadiness = "not ready without changes";
        }

        int liveSignals = 0;
        if (liveWeather) liveSignals++;
        if (realPlaces) liveSignals++;
        String liveDataCoverage = switch (liveSignals) {
            case 2 -> "moderate live grounding";
            case 1 -> "partial live grounding";
            default -> "estimate-led planning";
        };

        String sourceReliability;
        if (degradedFlight || degradedHotel || degradedWeather) {
            sourceReliability = "mixed with graceful fallbacks";
        } else if (liveSignals >= 1) {
            sourceReliability = "hybrid provider-backed planning";
        } else {
            sourceReliability = "heuristic planning with real booking handoff";
        }

        List<String> groundingNotes = new ArrayList<>();
        groundingNotes.add("Budget agent is the planning anchor and allocates spend lanes before downstream agents respond.");
        groundingNotes.add("Flight and hotel buttons carry route, dates, and traveler count into external booking search pages.");
        groundingNotes.add(liveWeather
                ? "Weather context is backed by a live provider feed for the destination window where available."
                : "Weather context is season-aware when live forecast coverage is unavailable or out of range.");
        groundingNotes.add(realPlaces
                ? "Itinerary places are filtered and ranked from provider results before being used in the schedule."
                : "Itinerary structure is still generated safely even if place providers are weak.");
        if (Boolean.TRUE.equals(flightOutput.agentResult().degraded()) || Boolean.TRUE.equals(hotelOutput.agentResult().degraded()) || Boolean.TRUE.equals(weatherOutput.agentResult().degraded())) {
            groundingNotes.add("At least one agent degraded gracefully instead of breaking the full trip workflow.");
        }

        return new PlanningIntelligenceView(
                archetype,
                planningMode,
                bookingReadiness,
                sourceReliability,
                liveDataCoverage,
                travelerProfile,
                groundingNotes
        );
    }

    private int stageOrder(String agent) {
        return switch (agent) {
            case "budget-agent" -> 1;
            case "flight-agent" -> 2;
            case "hotel-agent" -> 3;
            case "weather-agent" -> 4;
            case "trip-advisor-agent" -> 5;
            case "itinerary-agent" -> 6;
            default -> 99;
        };
    }

    private void publishFinalAgentState(TripRequest request, AgentResult result) {
        if (Boolean.TRUE.equals(result.degraded()) || "degraded".equalsIgnoreCase(result.status())) {
            workflowEventPublisher.agentDegraded(request, result);
            return;
        }
        workflowEventPublisher.agentCompleted(request, result);
    }

    private String shortReason(Throwable error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        return cause.getClass().getSimpleName();
    }
}
