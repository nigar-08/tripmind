package com.tripmind.tripmind.model;

public record AgentResult(
        String agent,
        String status,
        String summary,
        String capability,
        Long durationMs,
        Boolean degraded
) {
    public AgentResult(String agent, String status, String summary) {
        this(agent, status, summary, "", null, false);
    }

    public AgentResult withDuration(long durationMs) {
        return new AgentResult(agent, status, summary, capability, durationMs, degraded);
    }

    public AgentResult degraded(String reason) {
        String message = reason == null || reason.isBlank() ? summary : summary + " " + reason;
        return new AgentResult(agent, "degraded", message, capability, durationMs, true);
    }
}
