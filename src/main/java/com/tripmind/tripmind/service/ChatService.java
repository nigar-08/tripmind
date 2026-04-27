package com.tripmind.tripmind.service;

import com.tripmind.tripmind.dto.ChatRequest;
import com.tripmind.tripmind.dto.ChatResponse;
import com.tripmind.tripmind.dto.ComparisonView;
import com.tripmind.tripmind.dto.TripPlanResponse;
import com.tripmind.tripmind.dto.TripPlanSummaryView;
import com.tripmind.tripmind.dto.TripRiskView;
import com.tripmind.tripmind.entity.TripSessionEntity;
import com.tripmind.tripmind.model.TripRequest;
import com.tripmind.tripmind.service.ai.AiProviderService;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatService {

    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern RANGE_WITH_MONTH_PATTERN = Pattern.compile("(?i)(?:from\\s+)?(\\d{1,2})\\s*(?:to|-|until)\\s*(\\d{1,2})\\s+([a-zA-Z]+)");
    private static final Pattern RANGE_WITH_TWO_MONTHS_PATTERN = Pattern.compile("(?i)(?:from\\s+)?(\\d{1,2})\\s+([a-zA-Z]+)\\s*(?:to|-|until)\\s*(\\d{1,2})\\s+([a-zA-Z]+)(?=\\s+from\\b|\\s+and\\b|[,.]|$)");
    private static final Pattern TRAVELERS_PATTERN = Pattern.compile("(?i)(\\d+)\\s*(?:people|poeple|pepole|person|persons|pax|travellers|travelers|adults?)");
    private static final Pattern WE_ARE_TRAVELERS_PATTERN = Pattern.compile("(?i)\\b(?:we are|we're|for|group of)\\s*(\\d{1,2})\\b");
    private static final Pattern COMPANION_TRAVELERS_PATTERN = Pattern.compile("(?i)\\b(?:me and my (?:wife|husband|partner|boyfriend|girlfriend|friend)|my (?:wife|husband|partner|boyfriend|girlfriend|friend) and i)\\b");
    private static final Pattern BUDGET_PATTERN = Pattern.compile("(?i)(?:budget|under|around|cost|spend|total)\\s*(?:is|of|about|approx(?:imately)?)?\\s*([$€₹]|usd|eur|aed|inr|rs\\.?)?\\s*([\\d,]+(?:\\.\\d+)?)\\s*(lakh|lakhs|l)?(?:\\s*(per person|each))?");
    private static final Pattern MONEY_PATTERN = Pattern.compile("(?i)([$€₹]|usd|eur|aed|inr|rs\\.?)\\s*([\\d,]+(?:\\.\\d+)?)\\s*(lakh|lakhs|l)?(?:\\s*(per person|each))?");
    private static final Pattern PLAIN_BUDGET_REPLY_PATTERN = Pattern.compile("(?i)^(?:it'?s|its|it is|budget|budget is|total|total budget|total is|around|about)?\\s*([$€₹]|usd|eur|aed|inr|rs\\.?)?\\s*([\\d,]{3,}(?:\\.\\d+)?)\\s*(lakh|lakhs|l)?(?:\\s*(per person|each))?\\s*$");
    private static final Pattern BUDGET_AFTER_AMOUNT_PATTERN = Pattern.compile("(?i)(?:budget|under|around|cost|spend|total)?\\s*(?:is|of|about|approx(?:imately)?)?\\s*([\\d,]+(?:\\.\\d+)?)\\s*(lakh|lakhs|l)?\\s*(usd|eur|aed|inr|rs\\.?)\\s*(per person|each)?");
    private static final Pattern ORIGIN_PATTERN = Pattern.compile("(?i)(?:i live in|i am from|i'm from|from|leaving from|starting from|based in)\\s+([a-zA-Z ]{2,40}?)(?=\\s+to\\b|\\s+and\\b|\\s+with\\b|\\s+for\\b|\\s+from\\s+\\d|['\".,]|$)");
    private static final Pattern DESTINATION_PATTERN = Pattern.compile("(?i)(?:want\\s+(?:to\\s+)?go\\s+(?:to\\s+)?|wanna\\s+go\\s+(?:to\\s+)?|go\\s+(?:to\\s+)?|visit\\s+|travel\\s+to\\s+|trip\\s+to\\s+|planning\\s+for\\s+|plan(?:\\s+a)?(?:\\s+trip)?(?:\\s+to)?\\s+|build(?:\\s+a)?(?:\\s+trip)?(?:\\s+to)?\\s+|book(?:\\s+a)?(?:\\s+trip)?(?:\\s+to)?\\s+)([a-zA-Z ]{2,40}?)(?=\\s+from\\b|\\s+and\\b|\\s+with\\b|\\s+for\\b|\\s+on\\b|\\s+between\\b|\\s+we\\s+are\\b|\\s+we're\\b|\\s+our\\s+budget\\b|\\s+budget\\b|\\s+from\\s+\\d|[,.]|$)");
    private static final Pattern DESTINATION_CHANGE_PATTERN = Pattern.compile("(?i)(?:change|switch|update)\\s+(?:destination|trip|place)?\\s*(?:to)?\\s+([a-zA-Z ]{2,40}?)(?=\\s+and\\b|\\s+with\\b|\\s+for\\b|[,.]|$)");
    private static final Pattern COMPARE_PATTERN = Pattern.compile("(?i)(?:compare|comparison(?:\\s+between)?)\\s+([a-zA-Z ]{2,30}?)\\s+(?:and|vs|versus)\\s+([a-zA-Z ]{2,30}?)(?=\\s+for\\b|\\s+with\\b|[,.]|$)");
    private static final Pattern SINGLE_WORD_PLACE_PATTERN = Pattern.compile("(?i)^['\"]?([a-zA-Z]{3,30}(?:\\s+[a-zA-Z]{3,30})?)['\"]?$");
    private static final Pattern AMBIGUOUS_DATE_PATTERN = Pattern.compile("(?i)\\b(next month|this summer|christmas week|next week|next weekend|this weekend|summer holidays?)\\b");
    private static final Pattern SPECIAL_NEEDS_PATTERN = Pattern.compile("(?i)\\b(wheelchair|halal|vegan|vegetarian|infant|baby|toddler|senior|elderly|kid-friendly|kids|accessible)\\b");
    private static final Pattern EXPLORATORY_QUESTION_PATTERN = Pattern.compile("(?i)^(how much|what(?:'s| is)?|tell me about|is |can i |do i need|will it|would it|is it|should i|how expensive|how many days|visa|weather in |can you suggest|suggest|recommend|which place|what place)");
    private static final String EXPLORATORY_TOPIC_PREFIX = "EXPLORATORY_TOPIC:";
    private static final Set<String> GENERIC_DESTINATION_TOKENS = Set.of(
            "some", "somewhere", "place", "places", "asian", "international", "cool", "cold", "hot",
            "cheap", "budget", "affordable", "romantic", "honeymoon", "solo", "adventure", "relaxing",
            "relax", "family", "beach", "mountain", "city", "trip", "destination", "hill", "hills",
            "summer", "winter", "not", "too", "crowded"
    );

    private static final List<String> KNOWN_DESTINATIONS = List.of(
            "Goa", "Dubai", "Delhi", "Paris", "London", "Pune", "Mumbai", "Jaipur", "Kerala", "Bali",
            "Phuket", "Barcelona", "Doha", "Singapore", "Bangkok", "Maldives", "Tokyo", "Kashmir",
            "Manali", "Leh", "Switzerland", "Kochi", "Thailand", "Vietnam", "Nepal",
            "Beijing", "Shanghai", "Guangzhou", "Shenzhen", "Chengdu", "Rome", "Milan", "Venice",
            "Florence", "Zurich", "Lucerne", "Interlaken", "Kathmandu", "Pokhara", "Ho Chi Minh City",
            "Hanoi", "Chiang Mai", "New Zealand", "Auckland", "Wellington", "Queenstown", "France",
            "Paris", "Nice", "Lyon", "Strasbourg", "Bora Bora"
    );
    private static final Map<String, List<String>> BROAD_DESTINATION_SUGGESTIONS = Map.of(
            "China", List.of("Beijing", "Shanghai", "Guangzhou", "Shenzhen", "Chengdu"),
            "Italy", List.of("Rome", "Milan", "Venice", "Florence"),
            "Thailand", List.of("Bangkok", "Phuket", "Chiang Mai"),
            "Vietnam", List.of("Hanoi", "Ho Chi Minh City"),
            "Switzerland", List.of("Zurich", "Lucerne", "Interlaken"),
            "Nepal", List.of("Kathmandu", "Pokhara"),
            "New Zealand", List.of("Auckland", "Wellington", "Queenstown"),
            "France", List.of("Paris", "Nice", "Lyon", "Strasbourg")
    );
    private static final Set<String> NON_PLACE_TOKENS = Set.of(
            "it", "trip", "plan", "generate", "yes", "okay", "ok", "both", "group", "total",
            "round", "round trip", "one way", "one-way", "budget", "traveler", "travelers",
            "people", "person", "for both", "for total", "for group", "heeyy", "wooh", "hey",
            "hello", "hii", "heyy"
    );

    private final PlanPersistenceService planPersistenceService;
    private final TripPlanningFacade tripPlanningFacade;
    private final AiProviderService aiProviderService;

    public ChatService(
            PlanPersistenceService planPersistenceService,
            TripPlanningFacade tripPlanningFacade,
            AiProviderService aiProviderService
    ) {
        this.planPersistenceService = planPersistenceService;
        this.tripPlanningFacade = tripPlanningFacade;
        this.aiProviderService = aiProviderService;
    }

    public ChatResponse handleChat(ChatRequest request) {
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? UUID.randomUUID().toString()
                : request.sessionId();
        String normalizedMessage = normalize(request.message());
        boolean greetingOnly = isGreetingOnly(normalizedMessage);

        TripSessionEntity session = planPersistenceService.getOrCreateSession(sessionId);
        SessionState before = SessionState.from(session);

        if (shouldHandleAsExploratoryChat(session, normalizedMessage, request.message())) {
            extractVibe(session, normalizedMessage);
            rememberExploratoryTopic(session, request.message());
            session.setConversationState("chatting");
            session.setUpdatedAt(Instant.now());
            planPersistenceService.saveSession(session);
            List<String> missing = missingFields(session);
            return new ChatResponse(
                    sessionId,
                    "chatting",
                    answerExploratoryQuestion(session, request.message()),
                    snapshotMap(session),
                    missing,
                    missing.isEmpty(),
                    null,
                    null,
                    planPersistenceService.getRecentPlansForSession(sessionId)
            );
        }

        if (shouldAnswerFollowUp(session, normalizedMessage)) {
            List<String> missing = missingFields(session);
            return new ChatResponse(
                    sessionId,
                    "plan_follow_up",
                    answerFollowUpQuestion(session, request.message()),
                    snapshotMap(session),
                    missing,
                    missing.isEmpty(),
                    null,
                    null,
                    planPersistenceService.getRecentPlansForSession(sessionId)
            );
        }

        TripPlanResponse previousPlan = before.requestId() == null
                ? null
                : planPersistenceService.findPlanResponse(before.requestId()).orElse(null);
        mergeMessageIntoSession(session, request.message());
        session.setUpdatedAt(Instant.now());

        List<String> missingFields = missingFields(session);
        boolean readyToPlan = missingFields.isEmpty();
        boolean explicitPlanIntent = readyToPlan && hasExplicitPlanIntent(normalizedMessage);
        boolean shouldPlan = readyToPlan && shouldGeneratePlan(session, normalizedMessage);
        TripPlanResponse plan = null;
        ComparisonView comparison = null;

        if (shouldPlan) {
            session.setConversationState("plan_ready");
            if (session.getId() == null) {
                session = planPersistenceService.saveSession(session);
            }
            try {
                plan = tripPlanningFacade.planTripForSession(sessionId, toTripRequest(session));
            } catch (ResponseStatusException exception) {
                session.setConversationState("needs_revision");
                session.setTripSnapshotJson(snapshotMap(session).toString());
                planPersistenceService.saveSession(session);
                String draftReply = "I can’t generate a safe plan yet: " + exception.getReason() + " Please correct that detail and I’ll re-run the agents.";
                return new ChatResponse(
                        sessionId,
                        session.getConversationState(),
                        polishReply(request.message(), draftReply, snapshotMap(session), missingFields, readyToPlan),
                        snapshotMap(session),
                        missingFields,
                        readyToPlan,
                        null,
                        null,
                        planPersistenceService.getRecentPlansForSession(sessionId)
                );
            }
            if (previousPlan != null && shouldPreserveExistingItinerary(before, session, normalizedMessage)) {
                plan = preserveItinerary(plan, previousPlan);
                planPersistenceService.refreshPlanRecord(sessionId, toTripRequest(session), plan);
            }
            comparison = buildComparisonView(session, plan);
            session.setRequestId(plan.requestId());
            session.setTripSnapshotJson(String.valueOf(plan));
        } else {
            session.setConversationState(readyToPlan
                    ? "confirming"
                    : session.getPendingClarificationCode() != null ? "clarifying" : "collecting");
            session.setTripSnapshotJson(snapshotMap(session).toString());
        }

        planPersistenceService.saveSession(session);

        Map<String, Object> snapshot = snapshotMap(session);
        String draftReply = buildAssistantReply(session, missingFields, shouldPlan, greetingOnly, explicitPlanIntent, before);

        return new ChatResponse(
                sessionId,
                session.getConversationState(),
                polishReply(request.message(), draftReply, snapshot, missingFields, readyToPlan),
                snapshot,
                missingFields,
                readyToPlan,
                plan,
                comparison,
                planPersistenceService.getRecentPlansForSession(sessionId)
        );
    }

    private ComparisonView buildComparisonView(TripSessionEntity session, TripPlanResponse primaryPlan) {
        CompareIntent compareIntent = parseCompareIntent(session.getSpecialRequest());
        if (compareIntent == null) {
            return null;
        }

        TripRequest secondaryRequest = new TripRequest(
                UUID.randomUUID().toString(),
                session.getOrigin(),
                compareIntent.secondaryDestination(),
                session.getTravelers(),
                effectiveBudget(session),
                session.getDepartureDate(),
                session.getReturnDate(),
                session.getTripType() == null ? "round_trip" : session.getTripType(),
                session.getBudgetCurrency() == null ? "INR" : session.getBudgetCurrency(),
                session.getBudgetScope() == null ? "total" : session.getBudgetScope(),
                session.getVibe() == null || session.getVibe().isBlank() ? "comfortable travel" : session.getVibe(),
                "Comparison against " + primaryPlan.destination(),
                session.getTravelerNeeds()
        );
        TripPlanResponse secondaryPlan = tripPlanningFacade.planTrip(secondaryRequest);

        return new ComparisonView(
                session.getSpecialRequest(),
                compareIntent.secondaryDestination(),
                secondaryPlan,
                "Primary plan is built for " + primaryPlan.destination() + " and secondary comparison is built for " + compareIntent.secondaryDestination() + "."
        );
    }

    private String polishReply(
            String userMessage,
            String draftReply,
            Map<String, Object> snapshot,
            List<String> missingFields,
            boolean readyToPlan
    ) {
        return aiProviderService.polishChatReply(userMessage, draftReply, snapshot, missingFields, readyToPlan);
    }

    private boolean shouldGeneratePlan(TripSessionEntity session, String lower) {
        if (!hasExplicitPlanIntent(lower)) {
            return false;
        }
        return session.getPendingClarificationCode() == null;
    }

    private boolean shouldHandleAsExploratoryChat(TripSessionEntity session, String normalizedMessage, String rawMessage) {
        if (isConcreteDestinationSetupMessage(normalizedMessage, rawMessage)) {
            return false;
        }
        if (isVagueDestinationDiscoveryRequest(normalizedMessage)) {
            return true;
        }
        if (isExploratoryKnowledgeFollowUp(session, normalizedMessage, rawMessage)) {
            return true;
        }
        if (session.getPendingClarificationCode() != null) {
            return false;
        }
        if (shouldAnswerFollowUp(session, normalizedMessage)) {
            return false;
        }
        if (hasExplicitPlanIntent(normalizedMessage) || hasPlanningSetupIntent(normalizedMessage)) {
            return false;
        }
        if (containsEnoughStructuredTripData(rawMessage)) {
            return false;
        }
        return looksLikeExploratoryTravelQuestion(normalizedMessage);
    }

    private boolean isConcreteDestinationSetupMessage(String normalizedMessage, String rawMessage) {
        String lower = normalizedMessage.toLowerCase(Locale.ENGLISH);
        String likelyDestination = extractLikelyDestinationMention(rawMessage);
        return likelyDestination != null
                && !BROAD_DESTINATION_SUGGESTIONS.containsKey(likelyDestination)
                && (lower.contains("i want to go")
                || lower.contains("want to travel")
                || lower.contains("trip to")
                || lower.contains("plan a trip")
                || lower.contains("going to"));
    }

    private boolean isExploratoryKnowledgeFollowUp(TripSessionEntity session, String normalizedMessage, String rawMessage) {
        if (session == null || hasConcreteDestination(session)) {
            return false;
        }
        if (session.getPendingClarificationCode() != null || hasPlanningSetupIntent(normalizedMessage) || containsEnoughStructuredTripData(rawMessage)) {
            return false;
        }
        if (lastExploratoryTopic(session) == null) {
            return false;
        }
        String lower = normalizedMessage.toLowerCase(Locale.ENGLISH);
        return lower.contains("which country")
                || lower.contains("what country")
                || lower.contains("in which country")
                || lower.contains("where is")
                || lower.contains("what is")
                || lower.contains("tell me about")
                || lower.contains("is it");
    }

    private boolean isVagueDestinationDiscoveryRequest(String normalizedMessage) {
        String lower = normalizedMessage.toLowerCase(Locale.ENGLISH);
        boolean genericPlaceLanguage = lower.contains("some asian place")
                || lower.contains("some place")
                || lower.contains("somewhere")
                || lower.contains("which place")
                || lower.contains("what place")
                || lower.contains("where should i go")
                || lower.contains("some international place");
        boolean preferenceDrivenChoice = (lower.contains("cool")
                || lower.contains("not hot")
                || lower.contains("beach")
                || lower.contains("mountain")
                || lower.contains("honeymoon")
                || lower.contains("solo")
                || lower.contains("family")
                || lower.contains("adventure")
                || lower.contains("romantic"))
                && (lower.contains("suggest")
                || lower.contains("recommend")
                || lower.contains("somewhere")
                || lower.contains("some place")
                || lower.contains("which place")
                || lower.contains("what place")
                || lower.contains("where should i go")
                || lower.contains("want to go"));
        return (genericPlaceLanguage || preferenceDrivenChoice)
                && extractLikelyDestinationMention(lower) == null
                && !lower.contains("go to paris")
                && !lower.contains("go to dubai");
    }

    private boolean shouldAnswerFollowUp(TripSessionEntity session, String normalizedMessage) {
        if (!"plan_ready".equalsIgnoreCase(session.getConversationState())) {
            return false;
        }
        if (normalizedMessage.contains("generate") || normalizedMessage.contains("new trip")) {
            return false;
        }
        if (normalizedMessage.contains("change")
                || normalizedMessage.contains("update")
                || normalizedMessage.contains("budget")
                || normalizedMessage.contains("traveler")
                || normalizedMessage.contains("people")
                || normalizedMessage.contains("date")
                || normalizedMessage.contains("destination")) {
            return false;
        }
        return normalizedMessage.contains("?")
                || normalizedMessage.contains("visa")
                || normalizedMessage.contains("worth")
                || normalizedMessage.contains("can i")
                || normalizedMessage.contains("should i")
                || normalizedMessage.contains("what about");
    }

    private boolean isCorrectionLikeMessage(String normalizedMessage) {
        String lower = normalizedMessage.toLowerCase(Locale.ENGLISH);
        return lower.contains("actually")
                || lower.contains("make it")
                || lower.contains("change it")
                || lower.contains("change the")
                || lower.contains("update it")
                || lower.contains("not paris")
                || lower.contains("not ")
                || lower.contains("instead");
    }

    private String answerExploratoryQuestion(TripSessionEntity session, String question) {
        String mentionedDestination = extractLikelyDestinationMention(question);
        String knownDestination = session.getDestination() != null && !"pending".equalsIgnoreCase(session.getDestination())
                ? session.getDestination()
                : (mentionedDestination != null ? mentionedDestination : lastExploratoryTopic(session));
        String fallback = exploratoryFallback(question, knownDestination);
        String prompt = """
                Answer the user's travel question conversationally without starting trip planning yet.
                If the user seems exploratory, give a helpful short answer and end with one sentence that invites planning later.
                Current trip context: %s
                User question: %s
                """.formatted(snapshotMap(session), question);
        return aiProviderService.enrichAgentOutput("exploratory-chat", prompt, fallback);
    }

    private String answerFollowUpQuestion(TripSessionEntity session, String question) {
        TripPlanResponse latestPlan = session.getRequestId() == null
                ? null
                : planPersistenceService.findPlanResponse(session.getRequestId()).orElse(null);
        String fallback = "TripMind kept your current " + session.getDestination() + " plan in context. Ask anything about that trip and I’ll answer without rebuilding the whole plan.";
        if (latestPlan == null) {
            return fallback;
        }
        String prompt = """
                Answer the user's follow-up question about an already generated trip plan.
                Keep the answer concise and grounded in the trip.
                Destination: %s
                Origin: %s
                Dates: %s to %s
                Travelers: %s
                Budget: ₹%s
                Plan summary: %s
                Advisor: %s
                Weather: %s
                User question: %s
                """.formatted(
                session.getDestination(),
                session.getOrigin(),
                session.getDepartureDate(),
                session.getReturnDate(),
                session.getTravelers(),
                effectiveBudget(session),
                latestPlan.summary(),
                latestPlan.advisor() == null ? "n/a" : latestPlan.advisor().summary(),
                latestPlan.weather() == null ? "n/a" : latestPlan.weather().summary(),
                question
        );
        return aiProviderService.enrichAgentOutput("post-plan-chat", prompt, fallback);
    }

    private String exploratoryFallback(String question, String knownDestination) {
        String lower = normalize(question).toLowerCase(Locale.ENGLISH);
        Integer exploratoryBudget = extractBudgetAmountForExploration(question);
        if (knownDestination != null && (lower.contains("which country") || lower.contains("what country") || lower.contains("in which country"))) {
            return countryAnswerFor(knownDestination);
        }
        if (knownDestination != null && lower.contains("what is")) {
            return descriptionAnswerFor(knownDestination);
        }
        if (isVagueDestinationDiscoveryRequest(lower) && (lower.contains("cool") || lower.contains("not hot") || lower.contains("summer") || lower.contains("asian"))) {
            return """
                    If you want a cooler Asian trip in the next few weeks, I’d shortlist:
                    1. Kathmandu or Pokhara — cooler than Mumbai, scenic, and usually easier on budget.
                    2. Leh — very cool and dramatic, but better if you’re comfortable with altitude.
                    3. Kashmir — one of the safest “cool escape” choices if you want nature and lighter travel complexity.
                    4. Chiang Mai — milder than many beach destinations, with a calmer city vibe.
                    Pick one of these and I’ll turn it into a proper TripMind plan with dates, flights, hotels, and itinerary.
                    """;
        }
        if (isVagueDestinationDiscoveryRequest(lower)) {
            return """
                    A few strong options I’d shortlist are:
                    1. Kathmandu or Pokhara — scenic, easier to pace, and usually good value.
                    2. Kashmir — the safest all-round “cool escape” if you want nature and lighter travel complexity.
                    3. Leh — dramatic and memorable if you’re comfortable with altitude.
                    4. Chiang Mai — calmer city vibe with better weather than many hot beach destinations.
                    Pick one and I’ll turn it into a proper TripMind plan with dates, flights, hotels, and itinerary.
                    """;
        }
        if (isCoolWeatherSuggestionQuestion(lower) && exploratoryBudget != null) {
            return coolWeatherSuggestionWithBudget(exploratoryBudget);
        }
        if (isCoolWeatherSuggestionQuestion(lower)) {
            return """
                    If you want somewhere cooler than Mumbai in summer, a few strong options are:
                    1. Manali — mountain weather, cafes, and easy 4-6 day planning.
                    2. Kashmir — scenic, much cooler, and great for a proper escape.
                    3. Leh — very cool and dramatic, but better if you're okay with altitude and a slightly more demanding trip.
                    4. Kathmandu or Pokhara — good if you want an international option without going into a hot beach destination.
                    If you want, pick one of these and I’ll switch into planning mode for that trip.
                    """;
        }
        if ((lower.contains("how much money") || lower.contains("how much will it take") || lower.contains("budget"))
                && knownDestination != null) {
            return roughBudgetAnswer(knownDestination);
        }
        if (knownDestination != null) {
            return "I can help with a quick travel answer about " + knownDestination
                    + ". If you want, I can also switch into planning mode once you share origin, dates, travelers, and budget.";
        }
        return "I can answer general travel questions first. When you’re ready to plan, tell me the destination, origin, dates, travelers, and budget and I’ll build the trip.";
    }

    private String descriptionAnswerFor(String destination) {
        return switch (destination.toLowerCase(Locale.ENGLISH)) {
            case "bora bora" ->
                    "Bora Bora is a small island in French Polynesia, known for turquoise lagoons, overwater bungalows, and a very luxury-leaning honeymoon vibe. It feels remote, scenic, and expensive, so it’s usually better for a special-occasion trip than a quick budget escape.";
            default ->
                    destination + " is a well-known travel destination. If you want, I can also help compare what kind of trip it suits best before we start planning.";
        };
    }

    private String countryAnswerFor(String destination) {
        return switch (destination.toLowerCase(Locale.ENGLISH)) {
            case "bora bora" ->
                    "Bora Bora is part of French Polynesia, which is an overseas territory of France in the South Pacific. So politically it’s connected to France, but it feels very different from planning a mainland France trip like Paris or Nice.";
            default ->
                    "If you want, I can help place " + destination + " geographically and then turn it into a proper plan once you choose whether you actually want to travel there.";
        };
    }

    private String coolWeatherSuggestionWithBudget(int budget) {
        if (budget <= 80_000) {
            return """
                    If you want a cooler trip on this budget, I’d shortlist Manali, Kashmir, or Jaipur's shoulder-season alternatives only if the weather outlook stays mild.
                    The strongest bets are Manali and Kashmir because they feel like a real escape from Mumbai heat without forcing a luxury budget.
                    Pick one and I can plan it properly from your city and dates.
                    """;
        }
        if (budget <= 180_000) {
            return """
                    With that budget, a cooler-weather shortlist would be Manali, Kashmir, Leh if you're comfortable with altitude, or Kathmandu/Pokhara for an international option.
                    If you want the safest all-round choice, Kashmir or Kathmandu usually balance weather, scenery, and value well.
                    Pick one and I’ll turn it into a TripMind plan.
                    """;
        }
        return """
                With that budget, you have room for both Indian hill destinations and cooler international options like Kathmandu/Pokhara or selected Europe shoulder-season city trips.
                If your priority is guaranteed cooler weather from Mumbai heat, I’d start with Kashmir, Leh, or Kathmandu before looking at warmer beach destinations.
                Choose one and I’ll build a proper plan around it.
                """;
    }

    private boolean isCoolWeatherSuggestionQuestion(String lower) {
        return (lower.contains("suggest") || lower.contains("recommend"))
                && (lower.contains("cool") || lower.contains("not hot") || lower.contains("summer"));
    }

    private String roughBudgetAnswer(String destination) {
        String normalized = destination.toLowerCase(Locale.ENGLISH);
        return switch (normalized) {
            case "paris", "london", "switzerland", "rome", "milan", "venice", "florence" ->
                    "A rough answer: " + destination + " usually needs a mid-to-high budget from India. For 2 travelers, a short trip often starts around ₹1.8L to ₹3.2L depending on dates, flights, and hotel level. If you want, I can turn that into a real TripMind plan once you share dates.";
            case "goa", "jaipur", "kerala", "manali", "kashmir" ->
                    "A rough answer: " + destination + " can usually be planned on a much more flexible budget. For 2 travelers, a short trip often lands somewhere around ₹35k to ₹1.1L depending on flights, hotel style, and season. If you want, I can plan it properly with your dates and budget.";
            case "dubai", "bali", "thailand", "singapore", "maldives", "phuket", "bangkok" ->
                    "A rough answer: " + destination + " is usually a medium-to-high budget international trip from India. For 2 travelers, a short trip often falls around ₹1.1L to ₹2.5L depending on season and hotel level. If you want, I can build a real plan around your dates.";
            default ->
                    "A rough answer: total cost depends mostly on flights, hotel style, and season. If you tell me your destination, dates, traveler count, and budget, I can turn it into a proper TripMind plan.";
        };
    }

    private Integer extractBudgetAmountForExploration(String rawMessage) {
        String message = normalize(rawMessage);
        Matcher plainBudgetReply = PLAIN_BUDGET_REPLY_PATTERN.matcher(message);
        if (plainBudgetReply.matches()) {
            return parseBudget(plainBudgetReply.group(2), plainBudgetReply.group(3), normalizeCurrency(plainBudgetReply.group(1)));
        }
        Matcher afterAmountMatcher = BUDGET_AFTER_AMOUNT_PATTERN.matcher(message);
        if (afterAmountMatcher.find()) {
            return parseBudget(afterAmountMatcher.group(1), afterAmountMatcher.group(2), normalizeCurrency(afterAmountMatcher.group(3)));
        }
        Matcher matcher = BUDGET_PATTERN.matcher(message);
        if (matcher.find()) {
            return parseBudget(matcher.group(2), matcher.group(3), normalizeCurrency(matcher.group(1)));
        }
        Matcher moneyMatcher = MONEY_PATTERN.matcher(message);
        if (moneyMatcher.find()) {
            return parseBudget(moneyMatcher.group(2), moneyMatcher.group(3), normalizeCurrency(moneyMatcher.group(1)));
        }
        return null;
    }

    private boolean shouldPreserveExistingItinerary(SessionState before, TripSessionEntity session, String normalizedMessage) {
        if (!before.readyForPlan()) {
            return false;
        }
        boolean travelerChanged = !java.util.Objects.equals(before.travelers(), session.getTravelers());
        boolean budgetChanged = !java.util.Objects.equals(before.budget(), session.getBudget())
                || !java.util.Objects.equals(before.budgetScope(), session.getBudgetScope());
        boolean unchangedDates = java.util.Objects.equals(before.departureDate(), session.getDepartureDate())
                && java.util.Objects.equals(before.returnDate(), session.getReturnDate());
        boolean unchangedDestination = java.util.Objects.equals(before.destination(), session.getDestination());
        boolean itinerarySensitive = normalizedMessage.contains("itinerary")
                || normalizedMessage.contains("day ")
                || normalizedMessage.contains("activity")
                || normalizedMessage.contains("hotel")
                || normalizedMessage.contains("weather");
        return (travelerChanged || budgetChanged) && unchangedDates && unchangedDestination && !itinerarySensitive;
    }

    private TripPlanResponse preserveItinerary(TripPlanResponse currentPlan, TripPlanResponse previousPlan) {
        List<TripRiskView> risks = new ArrayList<>(currentPlan.riskFlags());
        risks.add(new TripRiskView(
                "info",
                "ITINERARY_REUSED",
                "TripMind updated budget, flight, and hotel sections while keeping the earlier itinerary structure unchanged."
        ));
        return new TripPlanResponse(
                currentPlan.requestId(),
                currentPlan.destination(),
                currentPlan.feasibility(),
                currentPlan.summary() + " Updated: flight estimate and cost breakdown were refreshed. Itinerary unchanged.",
                currentPlan.planningIntelligence(),
                currentPlan.budget(),
                currentPlan.flights(),
                currentPlan.hotels(),
                currentPlan.weather(),
                currentPlan.advisor(),
                currentPlan.stages(),
                previousPlan.itinerary(),
                currentPlan.dataSources(),
                risks
        );
    }

    private boolean hasExplicitPlanIntent(String lower) {
        return isAffirmative(lower)
                || lower.contains("looks good")
                || lower.contains("go ahead")
                || lower.contains("continue")
                || lower.contains("generate")
                || lower.contains("build")
                || lower.contains("plan now")
                || lower.contains("generate")
                || lower.equals("plan")
                || lower.equals("generate")
                || lower.equals("yes")
                || lower.equals("yes generate")
                || lower.equals("generate the trip plan now")
                || lower.equals("looks good, generate plan");
    }

    private boolean hasPlanningSetupIntent(String lower) {
        return lower.contains("i want to go")
                || lower.contains("want to travel")
                || lower.contains("plan a trip")
                || lower.contains("trip to")
                || lower.contains("going to")
                || lower.contains("budget")
                || lower.contains("travelers")
                || lower.contains("people")
                || lower.contains("round trip")
                || lower.contains("one way")
                || lower.contains("one-way");
    }

    private TripRequest toTripRequest(TripSessionEntity session) {
        return new TripRequest(
                UUID.randomUUID().toString(),
                session.getOrigin(),
                session.getDestination(),
                session.getTravelers(),
                effectiveBudget(session),
                session.getDepartureDate(),
                session.getReturnDate(),
                session.getTripType() == null ? "round_trip" : session.getTripType(),
                session.getBudgetCurrency() == null ? "INR" : session.getBudgetCurrency(),
                session.getBudgetScope() == null ? "total" : session.getBudgetScope(),
                session.getVibe() == null || session.getVibe().isBlank() ? "comfortable travel" : session.getVibe(),
                session.getSpecialRequest(),
                session.getTravelerNeeds()
        );
    }

    private int effectiveBudget(TripSessionEntity session) {
        if (session.getBudget() == null) {
            return 0;
        }
        if ("per_person".equalsIgnoreCase(session.getBudgetScope()) && session.getTravelers() != null) {
            return session.getBudget() * session.getTravelers();
        }
        return session.getBudget();
    }

    private void mergeMessageIntoSession(TripSessionEntity session, String rawMessage) {
        String message = normalize(rawMessage);
        if (isGreetingOnly(message)) {
            return;
        }
        if (startsNewTrip(message)) {
            resetSession(session);
            return;
        }
        if (hasPlanningSetupIntent(message) || containsEnoughStructuredTripData(rawMessage)) {
            clearExploratoryTopic(session);
        }
        String directDestinationMention = extractLikelyDestinationMention(rawMessage);
        if (directDestinationMention != null && hasPlanningSetupIntent(message) && !isVagueDestinationPhrase(message)) {
            applyDestinationCandidate(session, directDestinationMention);
        }

        resolvePendingClarification(session, message, rawMessage);
        extractComparisonIntent(session, message);
        extractTripType(session, message);
        extractDestinationChange(session, message);
        extractDestination(session, message);
        extractOrigin(session, message);
        extractTravelers(session, message);
        extractBudget(session, rawMessage);
        extractDates(session, message);
        extractVibe(session, message);
        extractTravelerNeeds(session, rawMessage);
        extractSpecialRequest(session, rawMessage);
        inferStandaloneLocation(session, message);

        if (session.getPendingClarificationCode() == null
                && hasConcreteDestination(session)
                && session.getTripType() == null) {
            setPendingClarification(session, "TRIP_TYPE", null, "Just to get your flight estimate right — do you want a round trip or a one-way route?");
        } else if (session.getPendingClarificationCode() == null
                && hasConcreteDestination(session)
                && session.getTravelers() != null
                && session.getTravelers() > 1
                && session.getBudget() != null
                && session.getBudgetScope() == null) {
            setPendingClarification(session, "BUDGET_SCOPE", String.valueOf(session.getBudget()), "Just to size the trip correctly — is your budget total for the group or per person?");
        } else if (session.getPendingClarificationCode() != null && canClearPendingFromCurrentState(session)) {
            clearPendingClarification(session);
        }
    }

    private void rememberExploratoryTopic(TripSessionEntity session, String rawMessage) {
        String topic = extractLikelyDestinationMention(rawMessage);
        if (topic == null || hasConcreteDestination(session)) {
            return;
        }
        session.setSpecialRequest(EXPLORATORY_TOPIC_PREFIX + topic);
    }

    private String lastExploratoryTopic(TripSessionEntity session) {
        if (session == null || session.getSpecialRequest() == null) {
            return null;
        }
        if (!session.getSpecialRequest().startsWith(EXPLORATORY_TOPIC_PREFIX)) {
            return null;
        }
        return session.getSpecialRequest().substring(EXPLORATORY_TOPIC_PREFIX.length()).trim();
    }

    private void clearExploratoryTopic(TripSessionEntity session) {
        if (session != null && session.getSpecialRequest() != null && session.getSpecialRequest().startsWith(EXPLORATORY_TOPIC_PREFIX)) {
            session.setSpecialRequest(null);
        }
    }

    private boolean canClearPendingFromCurrentState(TripSessionEntity session) {
        return switch (String.valueOf(session.getPendingClarificationCode())) {
            case "TRIP_TYPE" -> session.getTripType() != null;
            case "BUDGET_SCOPE" -> session.getBudgetScope() != null;
            case "DATE_RANGE" -> session.getDepartureDate() != null && session.getReturnDate() != null;
            case "DESTINATION_CONFIRMATION" -> session.getDestination() != null
                    && !"pending".equalsIgnoreCase(session.getDestination())
                    && session.getDestination().equalsIgnoreCase(String.valueOf(session.getPendingClarificationValue()));
            case "MULTI_DESTINATION" -> session.getDestination() != null;
            case "BROAD_DESTINATION" -> session.getDestination() != null && !"pending".equalsIgnoreCase(session.getDestination());
            default -> false;
        };
    }

    private void resetSession(TripSessionEntity session) {
        session.setDestination("pending");
        session.setOrigin(null);
        session.setTravelers(null);
        session.setBudget(null);
        session.setDepartureDate(null);
        session.setReturnDate(null);
        session.setTripType(null);
        session.setBudgetCurrency(null);
        session.setBudgetScope(null);
        session.setVibe(null);
        session.setSpecialRequest(null);
        session.setTravelerNeeds(null);
        clearPendingClarification(session);
        session.setConversationState("collecting");
    }

    private void resolvePendingClarification(TripSessionEntity session, String message, String rawMessage) {
        if (session.getPendingClarificationCode() == null) {
            return;
        }
        switch (session.getPendingClarificationCode()) {
            case "TRIP_TYPE" -> {
                String tripType = parseTripType(message);
                if (tripType != null) {
                    session.setTripType(tripType);
                    clearPendingClarification(session);
                }
            }
            case "BUDGET_SCOPE" -> {
                if (message.contains("per person") || message.contains("each")) {
                    session.setBudgetScope("per_person");
                    clearPendingClarification(session);
                } else if (message.contains("total")
                        || message.contains("group")
                        || message.contains("both")
                        || message.contains("for all")
                        || message.contains("together")
                        || isAffirmative(message)) {
                    session.setBudgetScope("total");
                    clearPendingClarification(session);
                }
            }
            case "DATE_RANGE" -> {
                extractDates(session, message);
                if (session.getDepartureDate() != null && session.getReturnDate() != null) {
                    clearPendingClarification(session);
                }
            }
            case "DESTINATION_CONFIRMATION" -> {
                if (isAffirmative(message)) {
                    session.setDestination(session.getPendingClarificationValue());
                    clearPendingClarification(session);
                } else if (isNegative(message)) {
                    session.setDestination("pending");
                    clearPendingClarification(session);
                }
            }
            case "MULTI_DESTINATION" -> {
                extractDestination(session, message);
                if (session.getDestination() != null && !"pending".equalsIgnoreCase(session.getDestination())) {
                    clearPendingClarification(session);
                }
            }
            case "BROAD_DESTINATION" -> {
                extractDestination(session, message);
                inferStandaloneLocation(session, message);
                if (session.getDestination() != null && !"pending".equalsIgnoreCase(session.getDestination())) {
                    clearPendingClarification(session);
                }
            }
            default -> {
            }
        }
        extractBudget(session, rawMessage);
        extractTravelerNeeds(session, rawMessage);
    }

    private void extractDestination(TripSessionEntity session, String message) {
        if (message.toLowerCase(Locale.ENGLISH).contains("change destination")) {
            return;
        }
        if (hasMultiDestinationWithoutOrder(message)) {
            setPendingClarification(session, "MULTI_DESTINATION", null, "I detected multiple destinations. Please give the trip order or tell me which destination to plan first so the routing makes sense.");
            return;
        }
        Matcher matcher = DESTINATION_PATTERN.matcher(message);
        if (matcher.find()) {
            applyDestinationCandidate(session, cleanPlace(matcher.group(1)));
            return;
        }
        String likelyMention = extractLikelyDestinationMention(message);
        if (likelyMention != null && hasPlanningSetupIntent(message) && !isVagueDestinationPhrase(message)) {
            applyDestinationCandidate(session, likelyMention);
        }
    }

    private void extractDestinationChange(TripSessionEntity session, String message) {
        Matcher matcher = DESTINATION_CHANGE_PATTERN.matcher(message);
        if (matcher.find()) {
            applyDestinationCandidate(session, cleanPlace(matcher.group(1)));
        }
    }

    private void applyDestinationCandidate(TripSessionEntity session, String rawPlace) {
        String place = titleize(rawPlace);
        if (!isLikelyPlaceCandidate(place) || isVagueDestinationPhrase(place)) {
            return;
        }
        if (BROAD_DESTINATION_SUGGESTIONS.containsKey(place)) {
            session.setDestination("pending");
            setPendingClarification(
                    session,
                    "BROAD_DESTINATION",
                    place,
                    broadDestinationClarificationNote(session, place)
            );
            return;
        }
        String corrected = fuzzyDestination(place);
        if (corrected != null && !corrected.equalsIgnoreCase(place)) {
            setPendingClarification(session, "DESTINATION_CONFIRMATION", corrected, "Just to avoid planning the wrong city — did you mean " + corrected + "?");
            return;
        }
        session.setDestination(corrected == null ? place : corrected);
        if ("DESTINATION_CONFIRMATION".equals(session.getPendingClarificationCode())) {
            clearPendingClarification(session);
        }
    }

    private String broadDestinationClarificationNote(TripSessionEntity session, String place) {
        List<String> suggestions = BROAD_DESTINATION_SUGGESTIONS.getOrDefault(place, List.of());
        String joined = String.join(", ", suggestions);
        String vibe = session.getVibe() == null ? "" : session.getVibe().toLowerCase(Locale.ENGLISH);
        String recommendation = recommendedCitiesForVibe(place, vibe);
        if (recommendation != null) {
            return recommendation;
        }
        return switch (place) {
            case "New Zealand" -> "New Zealand is broad, so I should narrow it to a city first — please pick a city first. Queenstown is best for dramatic scenery, Auckland is the easiest all-round city base, and Wellington is better for a calmer city vibe. Which one should I plan?";
            case "China" -> "China is broad, so I should narrow it to a city first — please pick a city first. Beijing is best for classic landmarks, Shanghai feels more modern, and Chengdu is better for a slower culture-food trip. Which one should I plan?";
            default -> "I can plan " + place + " much better if you pick a city first — for example " + joined + ". Which one do you want?";
        };
    }

    private String recommendedCitiesForVibe(String place, String vibe) {
        return switch (place) {
            case "France" -> {
                if (vibe.contains("romantic")) {
                    yield "Since you were looking for a romantic trip, if you want France I’d usually suggest Paris first. You could also consider Nice for a coastal vibe or Lyon for a calmer city break. Which one should I plan for you?";
                }
                if (vibe.contains("luxury")) {
                    yield "If you want a more luxury-leaning France trip, Paris is the strongest fit, with Nice as a good Riviera option. Lyon is better if you want a calmer upscale city break. Which one should I plan?";
                }
                if (vibe.contains("family")) {
                    yield "If you want a family-friendly France trip, Paris is the easiest all-round base, while Nice works well if you want a lighter coastal pace. Lyon is a calmer city option. Which one should I plan?";
                }
                if (vibe.contains("nightlife")) {
                    yield "If nightlife matters, Paris is the strongest France choice. Nice works if you want more evening energy near the coast, and Lyon is better for a calmer city vibe. Which one should I plan?";
                }
                yield null;
            }
            case "Italy" -> {
                if (vibe.contains("romantic")) {
                    yield "If you want a romantic Italy trip, Venice or Florence are usually the strongest fits, with Rome as the classic all-round option. Which one should I plan?";
                }
                if (vibe.contains("history") || vibe.contains("culture")) {
                    yield "If you want a culture-heavy Italy trip, Rome is the strongest starting point, with Florence for art and Venice for a more scenic feel. Which one should I plan?";
                }
                yield null;
            }
            case "New Zealand" -> {
                if (vibe.contains("adventure")) {
                    yield "If you want an adventure-focused New Zealand trip, I’d suggest Queenstown first. Auckland is easier as an all-round city base, and Wellington is better for a calmer city vibe. Which one should I plan?";
                }
                if (vibe.contains("romantic")) {
                    yield "If you want a romantic New Zealand trip, Queenstown is usually the strongest fit for scenery and atmosphere. Auckland is easier for a broader city trip, and Wellington is calmer. Which one should I plan?";
                }
                yield null;
            }
            case "Thailand" -> {
                if (vibe.contains("nightlife")) {
                    yield "If nightlife is the priority, Bangkok is the strongest Thailand base, with Phuket as the beach-and-nightlife option and Chiang Mai as the calmer city alternative. Which one should I plan?";
                }
                if (vibe.contains("beach")) {
                    yield "If you want a beach-first Thailand trip, Phuket is the strongest pick, with Bangkok better for city energy and Chiang Mai for a calmer cultural vibe. Which one should I plan?";
                }
                if (vibe.contains("budget")) {
                    yield "If budget matters, Chiang Mai is usually the easiest Thailand city to stretch your money in, while Bangkok is the best all-round base and Phuket is more beach-focused. Which one should I plan?";
                }
                yield null;
            }
            case "Switzerland" -> {
                if (vibe.contains("mountain") || vibe.contains("adventure") || vibe.contains("cool")) {
                    yield "If you want mountain scenery and cooler weather, Interlaken is usually the strongest Switzerland base. Lucerne is a calmer scenic option, and Zurich is the easiest city base. Which one should I plan?";
                }
                yield null;
            }
            case "Nepal" -> {
                if (vibe.contains("adventure") || vibe.contains("mountain")) {
                    yield "If you want an adventure-style Nepal trip, Pokhara is the stronger scenic base, while Kathmandu is better if you want culture and easier city access. Which one should I plan?";
                }
                yield null;
            }
            default -> null;
        };
    }

    private void extractOrigin(TripSessionEntity session, String message) {
        Matcher matcher = ORIGIN_PATTERN.matcher(message);
        if (matcher.find()) {
            session.setOrigin(titleize(cleanPlace(matcher.group(1))));
            return;
        }
        Matcher fromMatcher = Pattern.compile("(?i)\\bfrom\\s+([a-zA-Z ]{2,40})$").matcher(message);
        if (fromMatcher.find() && hasDestinationIntent(message)) {
            String origin = cleanPlace(fromMatcher.group(1));
            String[] words = origin.split("\\s+");
            session.setOrigin(titleize(words.length == 0 ? origin : words[words.length - 1]));
        }
    }

    private void extractTravelers(TripSessionEntity session, String message) {
        Matcher matcher = TRAVELERS_PATTERN.matcher(message);
        if (matcher.find()) {
            session.setTravelers(Integer.parseInt(matcher.group(1)));
            return;
        }
        Matcher groupMatcher = WE_ARE_TRAVELERS_PATTERN.matcher(message);
        if (groupMatcher.find()) {
            session.setTravelers(Integer.parseInt(groupMatcher.group(1)));
            return;
        }
        Matcher companionMatcher = COMPANION_TRAVELERS_PATTERN.matcher(message);
        if (companionMatcher.find()) {
            session.setTravelers(2);
            return;
        }
        if (session.getTravelers() == null && message.matches("\\d{1,2}")) {
            session.setTravelers(Integer.parseInt(message));
        }
    }

    private void extractBudget(TripSessionEntity session, String rawMessage) {
        String message = normalize(rawMessage);
        String lower = message.toLowerCase(Locale.ENGLISH);

        Matcher plainBudgetReply = PLAIN_BUDGET_REPLY_PATTERN.matcher(message);
        if (plainBudgetReply.matches()) {
            applyBudgetMatch(session, plainBudgetReply.group(1), plainBudgetReply.group(2), plainBudgetReply.group(3), explicitBudgetScopeToken(lower, plainBudgetReply.group(4)));
            return;
        }
        Matcher afterAmountMatcher = BUDGET_AFTER_AMOUNT_PATTERN.matcher(message);
        if (afterAmountMatcher.find()) {
            applyBudgetMatch(session, afterAmountMatcher.group(3), afterAmountMatcher.group(1), afterAmountMatcher.group(2), explicitBudgetScopeToken(lower, afterAmountMatcher.group(4)));
            return;
        }
        Matcher matcher = BUDGET_PATTERN.matcher(message);
        if (matcher.find()) {
            applyBudgetMatch(session, matcher.group(1), matcher.group(2), matcher.group(3), explicitBudgetScopeToken(lower, matcher.group(4)));
            return;
        }
        Matcher moneyMatcher = MONEY_PATTERN.matcher(message);
        if (moneyMatcher.find() && (lower.contains("budget") || lower.contains("₹") || lower.contains("$") || lower.contains("€") || lower.contains("aed") || lower.contains("usd") || lower.contains("eur") || lower.contains("rs") || lower.contains("inr"))) {
            applyBudgetMatch(session, moneyMatcher.group(1), moneyMatcher.group(2), moneyMatcher.group(3), explicitBudgetScopeToken(lower, moneyMatcher.group(4)));
            return;
        }
        if (lower.contains("cheaper") && session.getBudget() != null) {
            session.setBudget((int) Math.max(1000, Math.round(session.getBudget() * 0.9)));
            return;
        }
        if ((lower.contains("more luxury") || lower.contains("luxury hotel")) && session.getBudget() != null) {
            session.setBudget((int) Math.round(session.getBudget() * 1.15));
        }
    }

    private void applyBudgetMatch(TripSessionEntity session, String currencyToken, String amount, String scale, String scopeToken) {
        String currency = normalizeCurrency(currencyToken);
        int normalizedAmount = parseBudget(amount, scale, currency);
        session.setBudget(normalizedAmount);
        session.setBudgetCurrency(currency);
        if (scopeToken != null && !scopeToken.isBlank()) {
            session.setBudgetScope("per_person");
            clearPendingClarificationIf(session, "BUDGET_SCOPE");
        } else if (session.getTravelers() != null && session.getTravelers() > 1) {
            session.setBudgetScope(null);
            if (session.getPendingClarificationCode() == null && session.getTripType() != null) {
                setPendingClarification(session, "BUDGET_SCOPE", String.valueOf(normalizedAmount), "Just to size the trip correctly — is this budget total for the group or per person?");
            }
        } else {
            session.setBudgetScope("total");
            clearPendingClarificationIf(session, "BUDGET_SCOPE");
        }
    }

    private String explicitBudgetScopeToken(String lowerMessage, String capturedScopeToken) {
        if (capturedScopeToken != null && !capturedScopeToken.isBlank()) {
            return capturedScopeToken;
        }
        if (lowerMessage.contains("total")
                || lowerMessage.contains("group")
                || lowerMessage.contains("both")
                || lowerMessage.contains("for all")
                || lowerMessage.contains("together")) {
            return "total";
        }
        return null;
    }

    private void extractDates(TripSessionEntity session, String message) {
        Matcher ambiguousMatcher = AMBIGUOUS_DATE_PATTERN.matcher(message);
        if (ambiguousMatcher.find()) {
            setPendingClarification(session, "DATE_RANGE", ambiguousMatcher.group(1), "I can plan the trip more accurately with exact departure and return dates — please give departure and return dates instead of '" + ambiguousMatcher.group(1) + "'.");
            return;
        }

        Matcher isoMatcher = ISO_DATE_PATTERN.matcher(message);
        List<String> dates = new ArrayList<>();
        while (isoMatcher.find()) {
            dates.add(isoMatcher.group(1));
        }
        if (dates.size() >= 2) {
            session.setDepartureDate(dates.get(0));
            session.setReturnDate(dates.get(1));
            clearPendingClarificationIf(session, "DATE_RANGE");
            return;
        }

        Matcher twoMonthRangeMatcher = RANGE_WITH_TWO_MONTHS_PATTERN.matcher(message);
        if (twoMonthRangeMatcher.find()) {
            int startDay = Integer.parseInt(twoMonthRangeMatcher.group(1));
            Month startMonth = monthFrom(twoMonthRangeMatcher.group(2));
            int endDay = Integer.parseInt(twoMonthRangeMatcher.group(3));
            Month endMonth = monthFrom(twoMonthRangeMatcher.group(4));
            if (startMonth != null && endMonth != null) {
                int startYear = Year.now().getValue();
                int endYear = endMonth.getValue() < startMonth.getValue() ? startYear + 1 : startYear;
                session.setDepartureDate(String.format("%d-%02d-%02d", startYear, startMonth.getValue(), startDay));
                session.setReturnDate(String.format("%d-%02d-%02d", endYear, endMonth.getValue(), endDay));
                clearPendingClarificationIf(session, "DATE_RANGE");
                return;
            }
        }

        Matcher rangeMatcher = RANGE_WITH_MONTH_PATTERN.matcher(message);
        if (rangeMatcher.find()) {
            int startDay = Integer.parseInt(rangeMatcher.group(1));
            int endDay = Integer.parseInt(rangeMatcher.group(2));
            Month month = monthFrom(rangeMatcher.group(3));
            if (month != null) {
                int year = Year.now().getValue();
                LocalDate start = LocalDate.of(year, month, startDay);
                if (start.isBefore(LocalDate.now().minusDays(1))) {
                    year += 1;
                }
                session.setDepartureDate(String.format("%d-%02d-%02d", year, month.getValue(), startDay));
                session.setReturnDate(String.format("%d-%02d-%02d", year, month.getValue(), endDay));
                clearPendingClarificationIf(session, "DATE_RANGE");
            }
        }
    }

    private void extractTripType(TripSessionEntity session, String message) {
        String tripType = parseTripType(message);
        if (tripType != null) {
            session.setTripType(tripType);
            clearPendingClarificationIf(session, "TRIP_TYPE");
        }
    }

    private String parseTripType(String message) {
        String lower = message.toLowerCase(Locale.ENGLISH);
        if (lower.contains("one way") || lower.contains("one-way")) {
            return "one_way";
        }
        if (lower.contains("round trip") || lower.contains("return trip") || lower.contains("round-trip") || lower.equals("round")) {
            return "round_trip";
        }
        return null;
    }

    private void extractVibe(TripSessionEntity session, String message) {
        String lower = message.toLowerCase(Locale.ENGLISH);
        if (lower.contains("luxury hotel") && (lower.contains("cheap flight") || lower.contains("cheap flights") || lower.contains("budget flight"))) {
            return;
        }
        if (lower.contains("luxury") || lower.contains("5 star") || lower.contains("5-star")) {
            session.setVibe("luxury");
        } else if (lower.contains("budget")) {
            session.setVibe("budget");
        } else if (lower.contains("romantic") || lower.contains("honeymoon")) {
            session.setVibe("romantic");
        } else if (lower.contains("adventure") || lower.contains("trek") || lower.contains("hiking")) {
            session.setVibe("adventure");
        } else if (lower.contains("family")) {
            session.setVibe("family-friendly");
        } else if (lower.contains("nightlife")) {
            session.setVibe("nightlife-focused");
        } else if (lower.contains("beach")) {
            session.setVibe("beach holiday");
        }
    }

    private void extractTravelerNeeds(TripSessionEntity session, String rawMessage) {
        Matcher matcher = SPECIAL_NEEDS_PATTERN.matcher(rawMessage);
        Set<String> tags = new LinkedHashSet<>();
        while (matcher.find()) {
            tags.add(matcher.group(1).toLowerCase(Locale.ENGLISH));
        }
        if (!tags.isEmpty()) {
            session.setTravelerNeeds(String.join(", ", tags));
        }
    }

    private void extractSpecialRequest(TripSessionEntity session, String rawMessage) {
        String message = rawMessage.trim();
        String lower = message.toLowerCase(Locale.ENGLISH);
        if (lower.contains("keep same dates")
                || lower.contains("luxury hotel")
                || lower.contains("cheap flight")
                || lower.contains("cheap flights")
                || lower.contains("budget flight")
                || lower.contains("compare")
                || lower.contains("make it")
                || lower.contains("more ")
                || lower.contains("less ")
                || lower.contains("focus on")
                || lower.contains("change")
                || lower.contains("modify")
                || lower.contains("update")
                || SPECIAL_NEEDS_PATTERN.matcher(lower).find()) {
            session.setSpecialRequest(message);
        }
    }

    private void extractComparisonIntent(TripSessionEntity session, String message) {
        Matcher matcher = COMPARE_PATTERN.matcher(message);
        if (matcher.find()) {
            String primary = titleize(cleanPlace(matcher.group(1)));
            String secondary = titleize(cleanPlace(matcher.group(2)));
            session.setDestination(primary);
            session.setSpecialRequest("Compare " + primary + " and " + secondary);
        }
    }

    private CompareIntent parseCompareIntent(String specialRequest) {
        if (specialRequest == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("(?i)^compare\\s+([a-zA-Z ]{2,30}?)\\s+and\\s+([a-zA-Z ]{2,30})$").matcher(specialRequest.trim());
        if (!matcher.matches()) {
            return null;
        }
        return new CompareIntent(
                titleize(cleanPlace(matcher.group(1))),
                titleize(cleanPlace(matcher.group(2)))
        );
    }

    private void inferStandaloneLocation(TripSessionEntity session, String rawMessage) {
        String normalized = normalize(rawMessage);
        if (isGreetingOnly(normalized) || normalized.split("\\s+").length > 3) {
            return;
        }
        Matcher matcher = SINGLE_WORD_PLACE_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return;
        }
        String place = titleize(cleanPlace(matcher.group(1)));
        if (!isLikelyPlaceCandidate(place) || isVagueDestinationPhrase(place)) {
            return;
        }
        String corrected = fuzzyDestination(place);
        if (corrected == null) {
            return;
        }
        String finalPlace = corrected;
        if ((session.getDestination() == null || session.getDestination().equalsIgnoreCase("pending")) && session.getOrigin() == null) {
            session.setDestination(finalPlace);
            return;
        }
        if ((session.getOrigin() == null || session.getOrigin().isBlank()) && session.getDestination() != null && !session.getDestination().equalsIgnoreCase(finalPlace)) {
            session.setOrigin(finalPlace);
        }
    }

    private List<String> missingFields(TripSessionEntity session) {
        List<String> missing = new ArrayList<>();
        if (session.getPendingClarificationCode() != null) {
            missing.add(syntheticMissingField(session.getPendingClarificationCode()));
            return missing;
        }
        if (session.getOrigin() == null || session.getOrigin().isBlank()) {
            missing.add("origin");
        }
        if (session.getDestination() == null || session.getDestination().isBlank() || "pending".equalsIgnoreCase(session.getDestination())) {
            missing.add("destination");
        }
        if (session.getTravelers() == null) {
            missing.add("travelers");
        }
        if (session.getBudget() == null) {
            missing.add("budget");
        }
        if (session.getTripType() == null || session.getTripType().isBlank()) {
            missing.add("tripType");
        }
        if (session.getBudget() != null && session.getTravelers() != null && session.getTravelers() > 1
                && (session.getBudgetScope() == null || session.getBudgetScope().isBlank())) {
            missing.add("budgetScope");
        }
        if (session.getDepartureDate() == null || session.getDepartureDate().isBlank()) {
            missing.add("departureDate");
        }
        if (session.getReturnDate() == null || session.getReturnDate().isBlank()) {
            missing.add("returnDate");
        }
        return missing;
    }

    private String syntheticMissingField(String clarificationCode) {
        return switch (clarificationCode) {
            case "TRIP_TYPE" -> "tripType";
            case "BUDGET_SCOPE" -> "budgetScope";
            case "DATE_RANGE" -> "exactDates";
            case "DESTINATION_CONFIRMATION" -> "destinationConfirmation";
            case "MULTI_DESTINATION" -> "destinationOrder";
            case "BROAD_DESTINATION" -> "destinationCity";
            default -> "clarification";
        };
    }

    private String buildAssistantReply(TripSessionEntity session, List<String> missingFields, boolean generatedPlan, boolean greetingOnly, boolean explicitPlanIntent, SessionState before) {
        if (generatedPlan) {
            boolean travelerChanged = !java.util.Objects.equals(before.travelers(), session.getTravelers());
            boolean budgetChanged = !java.util.Objects.equals(before.budget(), session.getBudget())
                    || !java.util.Objects.equals(before.budgetScope(), session.getBudgetScope());
            if ((travelerChanged || budgetChanged) && before.readyForPlan()) {
                return "Updated — now planning for "
                        + session.getTravelers() + " traveler(s) with an effective budget of ₹" + effectiveBudget(session)
                        + ". Flight estimate and cost breakdown were refreshed first, and the itinerary stayed unchanged unless you explicitly asked to rebuild it.";
            }
            if (session.getSpecialRequest() != null && session.getSpecialRequest().toLowerCase(Locale.ENGLISH).startsWith("compare ")) {
                return "I generated a primary trip plan for "
                        + session.getDestination()
                        + " and kept the comparison request in context.";
            }
            return "I generated an updated trip plan for "
                    + session.getOrigin() + " to " + session.getDestination()
                    + " with an effective budget of ₹" + effectiveBudget(session)
                    + " for " + session.getTravelers() + " traveler(s).";
        }
        if (session.getPendingClarificationNote() != null && !session.getPendingClarificationNote().isBlank()) {
            return session.getPendingClarificationNote();
        }
        boolean changedExistingPlan = before.hasCompleteTripDetails() && (
                !java.util.Objects.equals(before.travelers(), session.getTravelers())
                        || !java.util.Objects.equals(before.budget(), session.getBudget())
                        || !java.util.Objects.equals(before.departureDate(), session.getDepartureDate())
                        || !java.util.Objects.equals(before.returnDate(), session.getReturnDate())
                        || !java.util.Objects.equals(before.destination(), session.getDestination())
        );
        if (missingFields.isEmpty()) {
            if (changedExistingPlan) {
                return "Updated — I’ve saved the revised trip details: "
                        + session.getOrigin() + " to " + session.getDestination()
                        + " · " + session.getDepartureDate() + " to " + session.getReturnDate()
                        + " · " + session.getTravelers() + " traveler(s)"
                        + " · budget ₹" + effectiveBudget(session)
                        + ". If this looks right, say generate plan. Otherwise, tell me what to change.";
            }
            if (session.getSpecialRequest() != null && session.getSpecialRequest().toLowerCase(Locale.ENGLISH).startsWith("compare ")) {
                return "Here’s what I understood. If it looks right, say generate and I’ll build the primary trip while keeping the comparison request in context.";
            }
            return "Here’s what I understood: "
                    + session.getOrigin() + " to " + session.getDestination()
                    + " · " + session.getDepartureDate() + " to " + session.getReturnDate()
                    + " · " + session.getTravelers() + " traveler(s)"
                    + " · budget ₹" + effectiveBudget(session)
                    + ". If this looks right, say generate plan. Otherwise, tell me what to change.";
        }
        if (greetingOnly) {
            return "Hey! I’m TripMind. We can chat casually about travel first, or start planning whenever you’re ready. If you want a plan, just tell me where you want to go, from where, your dates, travelers, and budget.";
        }
        if (!hasConcreteDestination(session) && hasPartialPlanningContext(session)) {
            return buildDestinationDiscoveryReply(session);
        }
        if (changedExistingPlan && !missingFields.isEmpty()) {
            return "Updated — now planning for "
                    + (session.getTravelers() == null ? "your revised traveler count" : session.getTravelers() + " traveler(s)")
                    + (session.getBudget() != null ? " and a budget of ₹" + effectiveBudget(session) : "")
                    + ". Once the remaining detail is confirmed, I can regenerate the plan with this change.";
        }

        StringBuilder builder = new StringBuilder("I’ve captured ");
        List<String> captured = new ArrayList<>();
        if (session.getDestination() != null && !"pending".equalsIgnoreCase(session.getDestination())) {
            captured.add("destination " + session.getDestination());
        }
        if (session.getOrigin() != null) {
            captured.add("origin " + session.getOrigin());
        }
        if (session.getTripType() != null) {
            captured.add("trip type " + session.getTripType().replace('_', ' '));
        }
        if (session.getTravelers() != null) {
            captured.add(session.getTravelers() + " traveler(s)");
        }
        if (session.getBudget() != null) {
            captured.add("budget ₹" + effectiveBudget(session)
                    + ("per_person".equalsIgnoreCase(session.getBudgetScope()) ? " (derived from per-person budget)" : ""));
        }
        if (session.getDepartureDate() != null && session.getReturnDate() != null) {
            captured.add("dates " + session.getDepartureDate() + " to " + session.getReturnDate());
        }
        if (session.getTravelerNeeds() != null) {
            captured.add("needs " + session.getTravelerNeeds());
        }
        builder.append(captured.isEmpty() ? "the trip intent" : String.join(", ", captured));
        builder.append(". Still needed: ").append(String.join(", ", missingFields)).append(".");
        return builder.toString();
    }

    private boolean hasPartialPlanningContext(TripSessionEntity session) {
        return session.getOrigin() != null
                || session.getTravelers() != null
                || session.getBudget() != null
                || session.getDepartureDate() != null
                || session.getReturnDate() != null
                || session.getTravelerNeeds() != null;
    }

    private String buildDestinationDiscoveryReply(TripSessionEntity session) {
        List<String> captured = new ArrayList<>();
        if (session.getOrigin() != null) {
            captured.add("from " + session.getOrigin());
        }
        if (session.getDepartureDate() != null && session.getReturnDate() != null) {
            captured.add("for " + session.getDepartureDate() + " to " + session.getReturnDate());
        }
        if (session.getTravelers() != null) {
            captured.add(session.getTravelers() + " traveler(s)");
        }
        if (session.getBudget() != null) {
            captured.add("budget ₹" + effectiveBudget(session));
        }
        String prefix = captured.isEmpty()
                ? "We’re still choosing the destination."
                : "I’ve noted " + String.join(", ", captured) + ", but we’re still choosing the destination.";
        return prefix + " If you want cooler options, I’d shortlist Kathmandu or Pokhara, Kashmir, Leh, and Chiang Mai. Pick one of those, or name another place, and I’ll switch into proper trip planning.";
    }

    private Map<String, Object> snapshotMap(TripSessionEntity session) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        putIfPresent(snapshot, "latestRequestId", session.getRequestId());
        putIfPresent(snapshot, "origin", session.getOrigin());
        if (session.getDestination() != null && !"pending".equalsIgnoreCase(session.getDestination())) {
            snapshot.put("destination", session.getDestination());
        }
        putIfPresent(snapshot, "travelers", session.getTravelers());
        if (session.getBudget() != null) {
            snapshot.put("budget", effectiveBudget(session));
        }
        putIfPresent(snapshot, "budgetCurrency", session.getBudgetCurrency());
        putIfPresent(snapshot, "budgetScope", session.getBudgetScope());
        putIfPresent(snapshot, "tripType", session.getTripType());
        putIfPresent(snapshot, "departureDate", session.getDepartureDate());
        putIfPresent(snapshot, "returnDate", session.getReturnDate());
        putIfPresent(snapshot, "vibe", session.getVibe());
        putIfPresent(snapshot, "specialRequest", session.getSpecialRequest());
        putIfPresent(snapshot, "travelerNeeds", session.getTravelerNeeds());
        putIfPresent(snapshot, "pendingClarification", session.getPendingClarificationNote());
        return snapshot;
    }

    private void putIfPresent(Map<String, Object> snapshot, String key, Object value) {
        if (value != null && !(value instanceof String str && str.isBlank())) {
            snapshot.put(key, value);
        }
    }

    private String normalize(String rawMessage) {
        return rawMessage
                .replace("form ", "from ")
                .replace("fpr ", "for ")
                .replace("whihc", "which")
                .replace("didnt", "didn't")
                .replace("budge t", "budget")
                .replace("budge ti", "budget ")
                .replace("budegt", "budget")
                .replace("budget si", "budget is")
                .replace("fro goroup", "for group")
                .replace("for goroup", "for group")
                .replace("gor th egourp", "for the group")
                .replace("new zeland", "new zealand")
                .replace("wan t", "want")
                .replace("apirl", "april")
                .replace("arpil", "april")
                .replace("aprill", "april")
                .replace("travellers", "travelers")
                .replace("poeple", "people")
                .replace("pepole", "people")
                .replace("pnly", "only")
                .replace(" mat", " may")
                .replaceAll("[’`]", "'")
                .replaceAll("[\\n\\r]+", " ")
                .trim();
    }

    private boolean hasDestinationIntent(String message) {
        return DESTINATION_PATTERN.matcher(message).find();
    }

    private boolean containsEnoughStructuredTripData(String rawMessage) {
        String normalized = normalize(rawMessage);
        int signals = 0;
        if (DESTINATION_PATTERN.matcher(normalized).find()) {
            signals++;
        }
        if (ORIGIN_PATTERN.matcher(normalized).find()) {
            signals++;
        }
        if (TRAVELERS_PATTERN.matcher(normalized).find() || WE_ARE_TRAVELERS_PATTERN.matcher(normalized).find()) {
            signals++;
        }
        if (BUDGET_PATTERN.matcher(normalized).find() || MONEY_PATTERN.matcher(normalized).find() || PLAIN_BUDGET_REPLY_PATTERN.matcher(normalized).matches()) {
            signals++;
        }
        if (ISO_DATE_PATTERN.matcher(normalized).find()
                || RANGE_WITH_MONTH_PATTERN.matcher(normalized).find()
                || RANGE_WITH_TWO_MONTHS_PATTERN.matcher(normalized).find()) {
            signals++;
        }
        if (parseTripType(normalized) != null) {
            signals++;
        }
        return signals >= 2;
    }

    private boolean looksLikeExploratoryTravelQuestion(String normalizedMessage) {
        return EXPLORATORY_QUESTION_PATTERN.matcher(normalizedMessage).find()
                || normalizedMessage.endsWith("?")
                || normalizedMessage.contains("how much money")
                || normalizedMessage.contains("how much will it take")
                || normalizedMessage.contains("suggest some places")
                || normalizedMessage.contains("cool place")
                || normalizedMessage.contains("not hot")
                || normalizedMessage.contains("summer season")
                || normalizedMessage.contains("is it expensive")
                || normalizedMessage.contains("good time to visit")
                || normalizedMessage.contains("what should i know");
    }

    private boolean hasConcreteDestination(TripSessionEntity session) {
        return session.getDestination() != null
                && !session.getDestination().isBlank()
                && !"pending".equalsIgnoreCase(session.getDestination());
    }

    private boolean isGreetingOnly(String message) {
        String cleaned = message.toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.equals("hi")
                || cleaned.equals("hii")
                || cleaned.equals("hey")
                || cleaned.equals("heyy")
                || cleaned.equals("heeyy")
                || cleaned.equals("hello")
                || cleaned.equals("helo")
                || cleaned.equals("yo")
                || cleaned.equals("namaste");
    }

    private boolean startsNewTrip(String message) {
        String lower = message.toLowerCase(Locale.ENGLISH);
        return lower.contains("new trip") || lower.contains("another trip") || lower.contains("start over") || lower.contains("reset trip");
    }

    private String cleanPlace(String value) {
        return value.replaceAll("[^a-zA-Z ]", " ")
                .replaceAll("\\s+", " ")
                .replaceFirst("(?i)^for\\s+", "")
                .trim();
    }

    private String titleize(String value) {
        String[] parts = value.toLowerCase(Locale.ENGLISH).split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private String extractLikelyDestinationMention(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return null;
        }
        String normalized = normalize(rawMessage);
        for (String candidate : KNOWN_DESTINATIONS) {
            if (normalized.toLowerCase(Locale.ENGLISH).contains(candidate.toLowerCase(Locale.ENGLISH))) {
                return candidate;
            }
        }
        return null;
    }

    private Month monthFrom(String token) {
        String normalized = token.toLowerCase(Locale.ENGLISH);
        return switch (normalized.substring(0, Math.min(normalized.length(), 3))) {
            case "jan" -> Month.JANUARY;
            case "feb" -> Month.FEBRUARY;
            case "mar" -> Month.MARCH;
            case "apr" -> Month.APRIL;
            case "may" -> Month.MAY;
            case "jun" -> Month.JUNE;
            case "jul" -> Month.JULY;
            case "aug" -> Month.AUGUST;
            case "sep" -> Month.SEPTEMBER;
            case "oct" -> Month.OCTOBER;
            case "nov" -> Month.NOVEMBER;
            case "dec" -> Month.DECEMBER;
            default -> null;
        };
    }

    private int parseBudget(String rawAmount, String rawScale, String currency) {
        double amount = Double.parseDouble(rawAmount.replace(",", ""));
        String scale = rawScale == null ? "" : rawScale.toLowerCase(Locale.ENGLISH);
        if (scale.equals("l") || scale.startsWith("lakh")) {
            amount *= 100000;
        }
        double rate = switch (currency) {
            case "USD" -> 83.0;
            case "EUR" -> 90.0;
            case "AED" -> 22.6;
            default -> 1.0;
        };
        return (int) Math.round(amount * rate);
    }

    private String normalizeCurrency(String currencyToken) {
        if (currencyToken == null || currencyToken.isBlank()) {
            return "INR";
        }
        String token = currencyToken.toLowerCase(Locale.ENGLISH);
        if (token.contains("$") || token.contains("usd")) {
            return "USD";
        }
        if (token.contains("€") || token.contains("eur")) {
            return "EUR";
        }
        if (token.contains("aed")) {
            return "AED";
        }
        return "INR";
    }

    private boolean isAffirmative(String lowerMessage) {
        String cleaned = lowerMessage.replaceAll("[^a-z ]", " ").replaceAll("\\s+", " ").trim();
        return cleaned.equals("yes")
                || cleaned.equals("yess")
                || cleaned.equals("yeah")
                || cleaned.equals("yep")
                || cleaned.equals("correct")
                || cleaned.equals("right")
                || cleaned.equals("ok")
                || cleaned.equals("okay")
                || cleaned.equals("go ahead")
                || cleaned.equals("plan it")
                || cleaned.equals("generate it");
    }

    private boolean isNegative(String lowerMessage) {
        String cleaned = lowerMessage.replaceAll("[^a-z ]", " ").replaceAll("\\s+", " ").trim();
        return cleaned.equals("no")
                || cleaned.equals("nah")
                || cleaned.equals("nope")
                || cleaned.equals("wrong");
    }

    private boolean hasMultiDestinationWithoutOrder(String message) {
        String lower = message.toLowerCase(Locale.ENGLISH);
        if (lower.contains("compare") || lower.contains(" from ")) {
            return false;
        }
        long matches = KNOWN_DESTINATIONS.stream()
                .map(String::toLowerCase)
                .filter(lower::contains)
                .count();
        return matches >= 2 && !lower.contains("first") && !lower.contains("then");
    }

    private boolean isVagueDestinationPhrase(String rawPlace) {
        if (rawPlace == null || rawPlace.isBlank()) {
            return false;
        }
        String lower = rawPlace.toLowerCase(Locale.ENGLISH).replaceAll("\\s+", " ").trim();
        if (lower.contains("somewhere")
                || lower.contains("some place")
                || lower.contains("which place")
                || lower.contains("what place")
                || lower.contains("where should i go")) {
            return true;
        }
        String[] tokens = lower.split(" ");
        long genericCount = Arrays.stream(tokens)
                .filter(GENERIC_DESTINATION_TOKENS::contains)
                .count();
        return genericCount > 0 && extractLikelyDestinationMention(lower) == null;
    }

    private String fuzzyDestination(String rawPlace) {
        if (rawPlace == null || rawPlace.isBlank()) {
            return null;
        }
        String normalized = rawPlace.toLowerCase(Locale.ENGLISH);
        if (KNOWN_DESTINATIONS.stream().anyMatch(place -> place.equalsIgnoreCase(rawPlace))) {
            return titleize(rawPlace);
        }
        String closest = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : KNOWN_DESTINATIONS) {
            int distance = levenshtein(normalized, candidate.toLowerCase(Locale.ENGLISH));
            if (distance < bestDistance) {
                bestDistance = distance;
                closest = candidate;
            }
        }
        return bestDistance <= 2 ? closest : null;
    }

    private boolean isLikelyPlaceCandidate(String rawPlace) {
        if (rawPlace == null || rawPlace.isBlank()) {
            return false;
        }
        String normalized = rawPlace.toLowerCase(Locale.ENGLISH).replaceAll("\\s+", " ").trim();
        if (normalized.length() < 3) {
            return false;
        }
        if (NON_PLACE_TOKENS.contains(normalized)) {
            return false;
        }
        if (isVagueDestinationPhrase(normalized)) {
            return false;
        }
        return Arrays.stream(normalized.split(" "))
                .filter(token -> !token.isBlank())
                .noneMatch(token -> NON_PLACE_TOKENS.contains(token));
    }

    private int levenshtein(String a, String b) {
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j < costs.length; j++) {
            costs[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int northwest = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int upper = costs[j];
                int candidate = a.charAt(i - 1) == b.charAt(j - 1)
                        ? northwest
                        : 1 + Math.min(Math.min(costs[j - 1], upper), northwest);
                northwest = upper;
                costs[j] = candidate;
            }
        }
        return costs[b.length()];
    }

    private void setPendingClarification(TripSessionEntity session, String code, String value, String note) {
        session.setPendingClarificationCode(code);
        session.setPendingClarificationValue(value);
        session.setPendingClarificationNote(note);
    }

    private void clearPendingClarification(TripSessionEntity session) {
        session.setPendingClarificationCode(null);
        session.setPendingClarificationValue(null);
        session.setPendingClarificationNote(null);
    }

    private void clearPendingClarificationIf(TripSessionEntity session, String code) {
        if (code.equals(session.getPendingClarificationCode())) {
            clearPendingClarification(session);
        }
    }

    private record CompareIntent(String primaryDestination, String secondaryDestination) {
    }

    private record SessionState(
            String requestId,
            String destination,
            Integer travelers,
            Integer budget,
            String departureDate,
            String returnDate,
            String budgetScope,
            String conversationState
    ) {
        static SessionState from(TripSessionEntity session) {
            return new SessionState(
                    session.getRequestId(),
                    session.getDestination(),
                    session.getTravelers(),
                    session.getBudget(),
                    session.getDepartureDate(),
                    session.getReturnDate(),
                    session.getBudgetScope(),
                    session.getConversationState()
            );
        }

        boolean readyForPlan() {
            return "plan_ready".equalsIgnoreCase(conversationState);
        }

        boolean hasCompleteTripDetails() {
            return destination != null
                    && !"pending".equalsIgnoreCase(destination)
                    && travelers != null
                    && budget != null
                    && departureDate != null
                    && returnDate != null
                    && budgetScope != null;
        }
    }
}
