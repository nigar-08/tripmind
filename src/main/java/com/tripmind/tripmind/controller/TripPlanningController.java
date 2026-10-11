package com.tripmind.tripmind.controller;

import com.tripmind.tripmind.dto.ChatRequest;
import com.tripmind.tripmind.dto.CopilotRequest;
import com.tripmind.tripmind.dto.CopilotResponse;
import com.tripmind.tripmind.service.ai.PlanCopilotService;
import com.tripmind.tripmind.dto.ChatResponse;
import com.tripmind.tripmind.dto.FeedbackRequest;
import com.tripmind.tripmind.dto.ReplanRequest;
import com.tripmind.tripmind.dto.TripPlanRequest;
import com.tripmind.tripmind.dto.TripPlanResponse;
import com.tripmind.tripmind.dto.TripPlanSummaryView;
import com.tripmind.tripmind.dto.WorkflowTraceView;
import com.tripmind.tripmind.entity.TripPlanEntity;
import com.tripmind.tripmind.service.ChatService;
import com.tripmind.tripmind.service.PlanPersistenceService;
import com.tripmind.tripmind.service.RedisTraceService;
import com.tripmind.tripmind.service.TripPlanningFacade;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/trips")
public class TripPlanningController {

    private final TripPlanningFacade tripPlanningFacade;
    private final PlanPersistenceService planPersistenceService;
    private final ChatService chatService;
    private final RedisTraceService redisTraceService;
    private final PlanCopilotService copilotService;

    public TripPlanningController(
            TripPlanningFacade tripPlanningFacade,
            PlanPersistenceService planPersistenceService,
            ChatService chatService,
            RedisTraceService redisTraceService,
            PlanCopilotService copilotService
    ) {
        this.tripPlanningFacade = tripPlanningFacade;
        this.planPersistenceService = planPersistenceService;
        this.chatService = chatService;
        this.redisTraceService = redisTraceService;
        this.copilotService = copilotService;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("TripMind Java backend is healthy.");
    }

    @PostMapping("/{requestId}/copilot")
    public CopilotResponse copilot(@PathVariable String requestId, @Valid @RequestBody CopilotRequest request) {
        return copilotService.answer(requestId, request.message());
    }

    @PostMapping("/{requestId}/scenarios/{scenarioId}/apply")
    public TripPlanResponse applyScenario(@PathVariable String requestId, @PathVariable String scenarioId) {
        return copilotService.applyScenario(requestId, scenarioId);
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(chatService.handleChat(request));
    }

    @PostMapping("/plan")
    public ResponseEntity<TripPlanResponse> planTrip(@Valid @RequestBody TripPlanRequest request) {
        return ResponseEntity.ok(tripPlanningFacade.planTrip(request));
    }

    @PostMapping("/{requestId}/replan")
    public ResponseEntity<TripPlanResponse> replanTrip(
            @PathVariable String requestId,
            @Valid @RequestBody ReplanRequest request
    ) {
        TripPlanEntity existingPlan = planPersistenceService.findPlan(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip plan not found"));

        TripPlanRequest replan = new TripPlanRequest(
                existingPlan.getOrigin(),
                existingPlan.getDestination(),
                existingPlan.getTravelers(),
                request.budget() != null ? request.budget() : existingPlan.getBudget(),
                firstNonBlank(request.departureDate(), existingPlan.getDepartureDate()),
                firstNonBlank(request.returnDate(), existingPlan.getReturnDate()),
                firstNonBlank(existingPlan.getTripType(), "round_trip"),
                firstNonBlank(existingPlan.getBudgetCurrency(), "INR"),
                firstNonBlank(existingPlan.getBudgetScope(), "total"),
                firstNonBlank(request.vibe(), existingPlan.getVibe()),
                firstNonBlank(request.specialRequest(), existingPlan.getSpecialRequest()),
                existingPlan.getTravelerNeeds()
        );

        return ResponseEntity.ok(tripPlanningFacade.planTrip(replan));
    }

    @GetMapping
    public ResponseEntity<java.util.List<TripPlanSummaryView>> recentPlans() {
        return ResponseEntity.ok(planPersistenceService.getRecentPlans());
    }

    @GetMapping("/session/{sessionId}/history")
    public ResponseEntity<java.util.List<TripPlanSummaryView>> sessionHistory(@PathVariable String sessionId) {
        return ResponseEntity.ok(planPersistenceService.getRecentPlansForSession(sessionId));
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<TripPlanResponse> getPlan(@PathVariable String requestId) {
        TripPlanResponse plan = planPersistenceService.findPlanResponse(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip plan not found"));
        return ResponseEntity.ok(plan);
    }

    @GetMapping("/{requestId}/trace")
    public ResponseEntity<WorkflowTraceView> workflowTrace(@PathVariable String requestId) {
        return ResponseEntity.ok(redisTraceService.trace(requestId));
    }

    @PostMapping("/feedback")
    public ResponseEntity<Void> submitFeedback(@Valid @RequestBody FeedbackRequest request) {
        planPersistenceService.saveFeedback(request);
        return ResponseEntity.accepted().build();
    }

    private String firstNonBlank(String candidate, String fallback) {
        return candidate != null && !candidate.isBlank() ? candidate : fallback;
    }
}
