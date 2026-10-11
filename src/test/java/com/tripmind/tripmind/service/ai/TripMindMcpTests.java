package com.tripmind.tripmind.service.ai;

import com.tripmind.tripmind.model.TripRequest;
import com.tripmind.tripmind.service.TripPlanningFacade;
import com.tripmind.tripmind.service.PlanPersistenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("mcp")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.ai.mcp.server.enabled=true", "tripmind.places.provider=disabled",
                "spring.datasource.url=jdbc:h2:mem:mcp_test;DB_CLOSE_DELAY=-1"})
class TripMindMcpTests {
    @LocalServerPort int port;
    @Autowired TripPlanningFacade planner;
    @Autowired ObjectMapper mapper;
    @Autowired PlanPersistenceService persistence;

    private String rpc(String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", "2025-03-26")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            return response.body();
        }
    }

    @Test
    void negotiatesDiscoversAndExecutesReadOnlyToolsOverMcp() throws Exception {
        var initialize = rpc("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"tripmind-test","version":"1.0"}}}
                """);
        assertThat(initialize).contains("tripmind", "serverInfo");
        var list = rpc("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");
        assertThat(list).contains("getTripPlan", "compareTripOptions", "searchTripGuidance")
                .doesNotContain("applyScenario", "recentPlans");
        var plan = planner.planTrip(new TripRequest(UUID.randomUUID().toString(), "Mumbai", "Goa", 2, 50000,
                "2027-02-10", "2027-02-14", "round_trip", "INR", "total", "comfortable",
                "historical places", "vegetarian"));
        for (String name : new String[]{"getTripPlan", "compareTripOptions", "searchTripGuidance"}) {
            String arguments = "{\"requestId\":\"" + plan.requestId() + "\""
                    + (name.equals("searchTripGuidance") ? ",\"query\":\"historical heritage places\"" : "") + "}";
            String result = rpc("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\""
                    + name + "\",\"arguments\":" + arguments + "}}");
            assertThat(result).doesNotContain("\"isError\":true");
            assertThat(result).contains(switch (name) {
                case "getTripPlan" -> plan.requestId();
                case "compareTripOptions" -> "shorter_stay";
                default -> "goa_";
            });
        }
        assertThat(persistence.findPlanResponse(plan.requestId()).orElseThrow()).isEqualTo(plan);
        var missing = rpc("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"getTripPlan\",\"arguments\":{\"requestId\":\"invalid\"}}}");
        assertThat(mapper.readTree(missing).path("result").path("isError").asBoolean()).isTrue();
    }
}
