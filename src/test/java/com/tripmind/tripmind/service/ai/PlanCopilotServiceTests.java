package com.tripmind.tripmind.service.ai;

import com.sun.net.httpserver.HttpServer;
import com.tripmind.tripmind.model.TripRequest;
import com.tripmind.tripmind.service.PlanPersistenceService;
import com.tripmind.tripmind.service.TripPlanningFacade;
import com.tripmind.tripmind.service.TripValidationService;
import com.tripmind.tripmind.service.agent.BudgetAgentService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {"tripmind.ai.provider=rule_based", "tripmind.places.provider=disabled", "spring.ai.model.chat=none"})
class PlanCopilotServiceTests {
    @Autowired PlanCopilotService copilot;
    @Autowired TripPlanningFacade planner;
    @Autowired PlanPersistenceService persistence;
    @Autowired BudgetAgentService budget;
    @Autowired TripValidationService validation;
    @Autowired PlanningKnowledgeService knowledge;

    private TripRequest request(String vibe, String returning) {
        return new TripRequest(UUID.randomUUID().toString(), "Mumbai", "Goa", 2, 50000,
                "2027-02-10", returning, "round_trip", "INR", "total", vibe,
                "Prefer historical places", "vegetarian");
    }

    @Test
    void comparesRecalculatedCostsAndAppliesRevisionWithinSameSession() {
        var originalRequest = request("comfortable", "2027-02-14");
        String session = UUID.randomUUID().toString();
        var original = planner.planTripForSession(session, originalRequest);
        var answer = copilot.answer(original.requestId(), "Compare budget and shorter stay options");
        assertThat(answer.mode()).isEqualTo("offline");
        assertThat(answer.scenarios()).hasSize(2);
        assertThat(answer.toolsUsed()).contains("compareBudgetOptions", "retrievePlanningGuidance");
        assertThat(answer.evidence()).isNotEmpty();
        var shorter = answer.scenarios().stream().filter(item -> item.id().equals("shorter_stay")).findFirst().orElseThrow();
        assertThat(shorter.planningTotal()).isLessThan(original.budget().planningTotal());
        assertThat(shorter.estimatedSavings()).isEqualTo(original.budget().planningTotal() - shorter.planningTotal());
        var revised = copilot.applyScenario(original.requestId(), "shorter_stay");
        var record = persistence.findPlan(revised.requestId()).orElseThrow();
        assertThat(record.getSessionKey()).isEqualTo(session);
        assertThat(record.getReturnDate()).isEqualTo("2027-02-13");
        assertThat(record.getTravelerNeeds()).isEqualTo("vegetarian");
        assertThat(record.getSpecialRequest()).isEqualTo("Prefer historical places");
        assertThat(record.getBudget()).isEqualTo(50000);
        assertThat(persistence.findPlan(original.requestId()).orElseThrow().getReturnDate()).isEqualTo("2027-02-14");
        assertThat(persistence.getOrCreateSession(session).getRequestId()).isEqualTo(revised.requestId());
    }

    @Test
    void doesNotOfferShorterThanOneNightOrRelaxExplicitLuxury() {
        var plan = planner.planTrip(request("luxury", "2027-02-11"));
        assertThat(copilot.answer(plan.requestId(), "Make it cheaper").scenarios()).isEmpty();
    }

    @Test
    void applyingScenarioPreservesPerPersonBudgetScopeWithoutMultiplyingAgain() {
        String session = UUID.randomUUID().toString();
        var request = new TripRequest(UUID.randomUUID().toString(), "Mumbai", "Goa", 2, 50000,
                "2027-02-10", "2027-02-14", "round_trip", "INR", "per_person", "comfortable", null, null);
        var plan = planner.planTripForSession(session, request);
        var revised = copilot.applyScenario(plan.requestId(), "shorter_stay");
        assertThat(revised.budget().totalBudget()).isEqualTo(50000);
        var saved = persistence.getOrCreateSession(session);
        assertThat(saved.getBudget()).isEqualTo(25000);
        assertThat(saved.getBudgetScope()).isEqualTo("per_person");
    }

    @Test
    void rejectsUnknownPlanAndScenario() {
        assertThatThrownBy(() -> copilot.answer("missing", "budget")).isInstanceOf(ResponseStatusException.class);
        var plan = planner.planTrip(request("comfortable", "2027-02-14"));
        assertThatThrownBy(() -> copilot.applyScenario(plan.requestId(), "invented"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void retrievesRelevantGuidanceAndDoesNotInventMatches() {
        assertThat(knowledge.retrieve("weather forecast")).anyMatch(item -> item.id().equals("weather"));
        assertThat(knowledge.retrieve("zzzzzzzz")).isEmpty();
        assertThat(knowledge.retrieve("historical places heritage", "Goa"))
                .anyMatch(item -> item.id().startsWith("goa_"))
                .noneMatch(item -> item.id().startsWith("jaipur_"));
    }

    @Test
    void springAiExecutesModelRequestedToolAndReturnsEvidence() throws Exception {
        List<String> bodies = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String message = bodies.size() == 1 ? """
                    {"role":"assistant","content":null,"tool_calls":[{"id":"call_1","type":"function","function":{"name":"compareBudgetOptions","arguments":"{}"}}]}
                    """ : """
                    {"role":"assistant","content":"The calculated shorter stay reduces the planning total. [shorter]"}
                    """;
            String json = "{\"id\":\"test\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"test-model\",\"choices\":[{\"index\":0,\"message\":"
                    + message + ",\"finish_reason\":\"" + (bodies.size() == 1 ? "tool_calls" : "stop") + "\"}]}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            ChatModel model = OpenAiChatModel.builder().options(OpenAiChatOptions.builder()
                    .apiKey("local-test-only").baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                    .model("test-model").timeout(Duration.ofSeconds(3)).maxRetries(0).build()).build();
            StaticListableBeanFactory factory = new StaticListableBeanFactory();
            factory.addBean("model", model);
            var enabled = new PlanCopilotService(persistence, planner, budget, validation, knowledge,
                    factory.getBeanProvider(ChatModel.class));
            var plan = planner.planTrip(request("comfortable", "2027-02-14"));
            var answer = enabled.answer(plan.requestId(), "Compare a shorter stay with this budget");
            assertThat(answer.mode()).isEqualTo("spring_ai");
            assertThat(answer.toolsUsed()).containsExactly("inspectPlan", "compareBudgetOptions", "retrievePlanningGuidance", "compareBudgetOptions");
            assertThat(bodies).hasSize(2);
            assertThat(bodies.getLast()).contains("\"role\":\"tool\"").contains("shorter_stay");
            assertThat(answer.evidence()).anyMatch(item -> item.id().equals("shorter"));
        } finally { server.stop(0); }
    }
}
