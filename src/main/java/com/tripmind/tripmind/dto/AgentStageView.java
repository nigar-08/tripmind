package com.tripmind.tripmind.dto;

public record AgentStageView(
        String agent,
        String status,
        String summary,
        String capability,
        Long durationMs,
        Boolean degraded
) implements java.io.Serializable {
}
