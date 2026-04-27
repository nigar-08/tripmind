package com.tripmind.tripmind.service;

import com.tripmind.tripmind.dto.AgentStageView;
import com.tripmind.tripmind.dto.TripPlanRequest;
import com.tripmind.tripmind.dto.TripPlanResponse;
import com.tripmind.tripmind.dto.TripRiskView;
import com.tripmind.tripmind.model.AgentResult;
import com.tripmind.tripmind.model.PlanningSnapshot;
import com.tripmind.tripmind.model.TripRequest;
import com.tripmind.tripmind.workflow.TripPlanningWorkflow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TripPlanningFacade {

    private final TripPlanningWorkflow tripPlanningWorkflow;
    private final PlanPersistenceService planPersistenceService;
    private final TripValidationService tripValidationService;

    public TripPlanningFacade(
            TripPlanningWorkflow tripPlanningWorkflow,
            PlanPersistenceService planPersistenceService,
            TripValidationService tripValidationService
    ) {
        this.tripPlanningWorkflow = tripPlanningWorkflow;
        this.planPersistenceService = planPersistenceService;
        this.tripValidationService = tripValidationService;
    }

    public TripPlanResponse planTrip(TripPlanRequest request) {
        TripRequest tripRequest = new TripRequest(
                UUID.randomUUID().toString(),
                request.origin(),
                request.destination(),
                request.travelers(),
                request.budget(),
                request.departureDate(),
                request.returnDate(),
                request.tripType() == null ? "round_trip" : request.tripType(),
                request.budgetCurrency() == null ? "INR" : request.budgetCurrency(),
                request.budgetScope() == null ? "total" : request.budgetScope(),
                request.vibe() == null ? "comfortable travel" : request.vibe(),
                request.specialRequest(),
                request.travelerNeeds()
        );
        return planTrip(tripRequest);
    }

    public TripPlanResponse planTrip(TripRequest tripRequest) {
        TripPlanResponse response = buildPlanResponse(tripRequest);
        planPersistenceService.savePlan(tripRequest, response);
        return response;
    }

    public TripPlanResponse planTripForSession(String sessionKey, TripRequest tripRequest) {
        TripPlanResponse response = buildPlanResponse(tripRequest);
        planPersistenceService.savePlanForSession(sessionKey, tripRequest, response);
        return response;
    }

    private TripPlanResponse buildPlanResponse(TripRequest tripRequest) {
        TripValidationService.ValidationResult validation = tripValidationService.validate(tripRequest);
        if (validation.hasBlockers()) {
            String message = validation.blockers().stream()
                    .map(TripRiskView::message)
                    .findFirst()
                    .orElse("Trip request could not be planned.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }

        PlanningSnapshot snapshot = tripPlanningWorkflow.execute(tripRequest);
        List<AgentStageView> stages = snapshot.stages().stream()
                .map(this::toStageView)
                .toList();
        List<TripRiskView> riskFlags = new ArrayList<>(validation.warnings());
        if ("tight".equalsIgnoreCase(snapshot.feasibility())) {
            riskFlags.add(new TripRiskView(
                    "warning",
                    "BUDGET_TIGHT",
                    "Budget agent marked this trip tight; review tradeoffs before treating the plan as bookable."
            ));
        }
        if (stages.stream().anyMatch(stage -> Boolean.TRUE.equals(stage.degraded()))) {
            riskFlags.add(new TripRiskView(
                    "warning",
                    "PARTIAL_AGENT_COVERAGE",
                    "One or more planning agents degraded gracefully, so some sections are estimate-led or partially grounded."
            ));
        }

        return new TripPlanResponse(
                tripRequest.requestId(),
                tripRequest.destination(),
                snapshot.feasibility(),
                snapshot.summary(),
                snapshot.planningIntelligence(),
                snapshot.budget(),
                snapshot.flights(),
                snapshot.hotels(),
                snapshot.weather(),
                snapshot.advisor(),
                stages,
                snapshot.itinerary(),
                snapshot.dataSources(),
                riskFlags
        );
    }

    private AgentStageView toStageView(AgentResult result) {
        return new AgentStageView(
                result.agent(),
                result.status(),
                result.summary(),
                result.capability(),
                result.durationMs(),
                result.degraded()
        );
    }
}
