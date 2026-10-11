package com.tripmind.tripmind.service.ai;

import com.tripmind.tripmind.dto.CopilotResponse;
import com.tripmind.tripmind.dto.TripPlanResponse;
import com.tripmind.tripmind.service.PlanPersistenceService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Read-only MCP tools: use the existing UI to approve and apply revisions. */
@Service
@Profile("mcp")
public class TripMindMcpTools {
    private final PlanPersistenceService persistence;
    private final PlanCopilotService copilot;
    private final PlanningKnowledgeService knowledge;

    public TripMindMcpTools(PlanPersistenceService persistence, PlanCopilotService copilot,
                            PlanningKnowledgeService knowledge) {
        this.persistence = persistence;
        this.copilot = copilot;
        this.knowledge = knowledge;
    }

    @Tool(description = "Read a saved TripMind plan by its request UUID, including budget, itinerary, weather and source labels. Estimates are not booking quotes.")
    public TripPlanResponse getTripPlan(@ToolParam(description = "Request UUID from the TripMind plan URL") String requestId) {
        validateId(requestId);
        return persistence.findPlanResponse(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Trip plan not found"));
    }

    @Tool(description = "Compare calculated budget-stay and shorter-stay revisions for a saved plan. Preserves traveler needs. Read-only; apply a chosen option in TripMind.")
    public List<CopilotResponse.Scenario> compareTripOptions(@ToolParam(description = "Saved plan request UUID") String requestId) {
        validateId(requestId);
        return copilot.compareScenarios(requestId);
    }

    @Tool(description = "Retrieve source-attributed internal planning guidance and curated tourism summaries for a saved plan's destination. Not live search; coverage is limited.")
    public List<CopilotResponse.Evidence> searchTripGuidance(
            @ToolParam(description = "Saved plan request UUID") String requestId,
            @ToolParam(description = "Travel question, 1 to 2000 characters") String query) {
        validateId(requestId);
        if (query == null || query.isBlank() || query.length() > 2000) {
            throw new IllegalArgumentException("Query must contain 1 to 2000 characters");
        }
        var entity = persistence.findPlan(requestId).orElseThrow();
        return knowledge.retrieve(query, entity.getDestination());
    }

    private void validateId(String requestId) {
        if (requestId == null || !requestId.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new IllegalArgumentException("A valid saved plan UUID is required");
        }
        UUID.fromString(requestId);
    }
}
