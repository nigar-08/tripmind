package com.tripmind.tripmind.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.tripmind.tripmind.dto.FeedbackRequest;
import com.tripmind.tripmind.dto.TripPlanResponse;
import com.tripmind.tripmind.dto.TripPlanSummaryView;
import com.tripmind.tripmind.entity.FeedbackEntity;
import com.tripmind.tripmind.entity.TripPlanEntity;
import com.tripmind.tripmind.entity.TripSessionEntity;
import com.tripmind.tripmind.model.TripRequest;
import com.tripmind.tripmind.repository.FeedbackRepository;
import com.tripmind.tripmind.repository.TripPlanRepository;
import com.tripmind.tripmind.repository.TripSessionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class PlanPersistenceService {

    private final TripPlanRepository tripPlanRepository;
    private final TripSessionRepository tripSessionRepository;
    private final FeedbackRepository feedbackRepository;
    private final ObjectMapper objectMapper;

    public PlanPersistenceService(
            TripPlanRepository tripPlanRepository,
            TripSessionRepository tripSessionRepository,
            FeedbackRepository feedbackRepository,
            ObjectMapper objectMapper
    ) {
        this.tripPlanRepository = tripPlanRepository;
        this.tripSessionRepository = tripSessionRepository;
        this.feedbackRepository = feedbackRepository;
        this.objectMapper = objectMapper;
    }

    public void savePlan(TripRequest request, TripPlanResponse response) {
        savePlanRecord(null, request, response);

        TripSessionEntity session = tripSessionRepository.findByRequestId(response.requestId()).orElseGet(TripSessionEntity::new);
        if (session.getSessionKey() == null || session.getSessionKey().isBlank()) {
            session.setSessionKey(response.requestId());
        }
        applyTripFields(session, request);
        session.setRequestId(response.requestId());
        session.setConversationState("plan_ready");
        session.setTripSnapshotJson(serialize(response));
        session.setUpdatedAt(Instant.now());
        saveSession(session);
    }

    public void savePlanForSession(String sessionKey, TripRequest request, TripPlanResponse response) {
        savePlanRecord(sessionKey, request, response);

        TripSessionEntity session = getOrCreateSession(sessionKey);
        session.setSessionKey(sessionKey);
        session.setRequestId(response.requestId());
        applyTripFields(session, request);
        session.setConversationState("plan_ready");
        session.setTripSnapshotJson(serialize(response));
        session.setUpdatedAt(Instant.now());
        saveSession(session);
    }

    public List<TripPlanSummaryView> getRecentPlans() {
        return tripPlanRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(20)
                .map(plan -> new TripPlanSummaryView(
                        plan.getRequestId(),
                        plan.getOrigin(),
                        plan.getDestination(),
                        plan.getFeasibility(),
                        plan.getBudget(),
                        plan.getDepartureDate(),
                        plan.getReturnDate(),
                        plan.getCreatedAt(),
                        plan.getSummary()
                ))
                .toList();
    }

    public List<TripPlanSummaryView> getRecentPlansForSession(String sessionKey) {
        return tripPlanRepository.findTop3BySessionKeyOrderByCreatedAtDesc(sessionKey).stream()
                .map(plan -> new TripPlanSummaryView(
                        plan.getRequestId(),
                        plan.getOrigin(),
                        plan.getDestination(),
                        plan.getFeasibility(),
                        plan.getBudget(),
                        plan.getDepartureDate(),
                        plan.getReturnDate(),
                        plan.getCreatedAt(),
                        plan.getSummary()
                ))
                .toList();
    }

    public void saveFeedback(FeedbackRequest request) {
        FeedbackEntity feedback = new FeedbackEntity();
        feedback.setRequestId(request.requestId());
        feedback.setUseful(request.useful());
        feedback.setBudgetRealistic(request.budgetRealistic());
        feedback.setWouldBook(request.wouldBook());
        feedback.setComments(request.comments());
        feedback.setCreatedAt(Instant.now());
        feedbackRepository.save(feedback);
    }

    public Optional<TripPlanEntity> findPlan(String requestId) {
        return tripPlanRepository.findByRequestId(requestId);
    }

    public Optional<TripPlanResponse> findPlanResponse(String requestId) {
        return tripPlanRepository.findByRequestId(requestId)
                .flatMap(plan -> deserialize(plan.getResponseJson()));
    }

    public Optional<TripSessionEntity> findSession(String requestId) {
        return tripSessionRepository.findByRequestId(requestId);
    }

    public TripSessionEntity getOrCreateSession(String sessionKey) {
        return tripSessionRepository.findBySessionKey(sessionKey).orElseGet(() -> {
            TripSessionEntity session = new TripSessionEntity();
            session.setSessionKey(sessionKey);
            session.setRequestId("session-" + sessionKey);
            session.setDestination("pending");
            session.setConversationState("collecting");
            session.setTripSnapshotJson("{}");
            session.setUpdatedAt(Instant.now());
            return session;
        });
    }

    public TripSessionEntity saveSession(TripSessionEntity session) {
        if (session.getSessionKey() == null || session.getSessionKey().isBlank()) {
            String fallback = session.getRequestId() == null || session.getRequestId().isBlank() ? "session-" + Instant.now().toEpochMilli() : session.getRequestId();
            session.setSessionKey(fallback);
        }
        if (session.getRequestId() == null || session.getRequestId().isBlank()) {
            session.setRequestId("session-" + session.getSessionKey());
        }
        if (session.getDestination() == null || session.getDestination().isBlank()) {
            session.setDestination("pending");
        }
        if (session.getConversationState() == null || session.getConversationState().isBlank()) {
            session.setConversationState("collecting");
        }
        if (session.getTripSnapshotJson() == null || session.getTripSnapshotJson().isBlank()) {
            session.setTripSnapshotJson("{}");
        }
        if (session.getUpdatedAt() == null) {
            session.setUpdatedAt(Instant.now());
        }
        return tripSessionRepository.save(session);
    }

    public void refreshPlanRecord(String sessionKey, TripRequest request, TripPlanResponse response) {
        savePlanRecord(sessionKey, request, response);
    }

    private void savePlanRecord(String sessionKey, TripRequest request, TripPlanResponse response) {
        TripPlanEntity plan = tripPlanRepository.findByRequestId(response.requestId()).orElseGet(TripPlanEntity::new);
        plan.setRequestId(response.requestId());
        plan.setSessionKey(sessionKey);
        plan.setOrigin(request.origin());
        plan.setDestination(request.destination());
        plan.setTravelers(request.travelers());
        plan.setBudget(request.budget());
        plan.setDepartureDate(request.departureDate());
        plan.setReturnDate(request.returnDate());
        plan.setTripType(request.tripType());
        plan.setBudgetCurrency(request.budgetCurrency());
        plan.setBudgetScope(request.budgetScope());
        plan.setVibe(request.vibe());
        plan.setSpecialRequest(request.specialRequest());
        plan.setTravelerNeeds(request.travelerNeeds());
        plan.setFeasibility(response.feasibility());
        plan.setSummary(response.summary());
        plan.setResponseJson(serialize(response));
        if (plan.getCreatedAt() == null) {
            plan.setCreatedAt(Instant.now());
        }
        tripPlanRepository.save(plan);
    }

    private void applyTripFields(TripSessionEntity session, TripRequest request) {
        session.setOrigin(request.origin());
        session.setDestination(request.destination());
        session.setTravelers(request.travelers());
        session.setBudget(request.budget());
        session.setDepartureDate(request.departureDate());
        session.setReturnDate(request.returnDate());
        session.setTripType(request.tripType());
        session.setBudgetCurrency(request.budgetCurrency());
        session.setBudgetScope(request.budgetScope());
        session.setVibe(request.vibe());
        session.setSpecialRequest(request.specialRequest());
        session.setTravelerNeeds(request.travelerNeeds());
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize persisted plan payload", exception);
        }
    }

    private Optional<TripPlanResponse> deserialize(String json) {
        try {
            return Optional.of(objectMapper.readValue(json, TripPlanResponse.class));
        } catch (JacksonException exception) {
            return Optional.empty();
        }
    }
}
