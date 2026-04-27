package com.tripmind.tripmind.dto;

import java.util.List;

public record WorkflowTraceView(
        String requestId,
        int eventCount,
        List<RedisStreamEventView> events
) {
}
