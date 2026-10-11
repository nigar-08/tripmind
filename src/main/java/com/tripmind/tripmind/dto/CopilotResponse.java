package com.tripmind.tripmind.dto;

import java.util.List;
import java.util.Map;

public record CopilotResponse(
        String mode, String answer, List<Evidence> evidence,
        List<String> toolsUsed, List<Scenario> scenarios
) {
    public record Evidence(String id, String title, String content, String source) {}
    public record Scenario(String id, String label, String tradeoff, int planningTotal,
                           int estimatedSavings, String feasibility, Map<String, Integer> costBreakdown,
                           ReplanRequest revision) {}
}
