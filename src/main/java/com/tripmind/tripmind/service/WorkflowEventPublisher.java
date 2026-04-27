package com.tripmind.tripmind.service;

import com.tripmind.tripmind.model.AgentResult;
import com.tripmind.tripmind.model.TripRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class WorkflowEventPublisher {

    private final StringRedisTemplate redisTemplate;
    private final String streamKey;
    private final boolean enabled;

    public WorkflowEventPublisher(
            StringRedisTemplate redisTemplate,
            @Value("${tripmind.redis.events.enabled:true}") boolean enabled,
            @Value("${tripmind.redis.events.stream:tripmind.workflow.events}") String streamKey
    ) {
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
        this.streamKey = streamKey;
    }

    public void workflowStarted(TripRequest request) {
        publish(request.requestId(), "workflow.started", "orchestrator", "started",
                "Workflow started for " + request.origin() + " to " + request.destination());
    }

    public void agentStarted(TripRequest request, String agent, String summary) {
        publish(request.requestId(), "agent.started", agent, "running", summary);
    }

    public void agentCompleted(TripRequest request, AgentResult result) {
        publish(request.requestId(), "agent.completed", result.agent(), result.status(), result.summary());
    }

    public void agentDegraded(TripRequest request, AgentResult result) {
        publish(request.requestId(), "agent.degraded", result.agent(), result.status(), result.summary());
    }

    public void workflowCompleted(TripRequest request, String summary) {
        publish(request.requestId(), "workflow.completed", "orchestrator", "completed", summary);
    }

    private void publish(String requestId, String eventType, String agent, String status, String summary) {
        if (!enabled) {
            return;
        }

        Map<String, String> body = new LinkedHashMap<>();
        body.put("requestId", requestId);
        body.put("eventType", eventType);
        body.put("agent", agent);
        body.put("status", status);
        body.put("summary", summary == null ? "" : summary);
        body.put("timestamp", Instant.now().toString());

        try {
            redisTemplate.opsForStream().add(MapRecord.create(streamKey, body));
        } catch (RuntimeException ignored) {
            // Redis is an event stream enhancement in this stage. Planning must still work without it.
        }
    }
}
