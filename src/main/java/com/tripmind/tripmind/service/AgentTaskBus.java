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
import java.util.function.Function;
import java.util.function.Supplier;

@Service
public class AgentTaskBus {

    private final StringRedisTemplate redisTemplate;
    private final boolean enabled;
    private final String taskPrefix;
    private final String resultPrefix;

    public AgentTaskBus(
            StringRedisTemplate redisTemplate,
            @Value("${tripmind.redis.agent-bus.enabled:true}") boolean enabled,
            @Value("${tripmind.redis.agent-bus.task-prefix:tripmind.agent}") String taskPrefix,
            @Value("${tripmind.redis.agent-bus.result-prefix:tripmind.agent}") String resultPrefix
    ) {
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
        this.taskPrefix = taskPrefix;
        this.resultPrefix = resultPrefix;
    }

    public <T> T dispatch(
            TripRequest request,
            String agent,
            String taskType,
            Supplier<T> worker,
            Function<T, AgentResult> resultExtractor,
            Function<T, Map<String, String>> resultPayload
    ) {
        publishTask(request, agent, taskType);
        long startedAt = System.nanoTime();
        T output = worker.get();
        long durationMs = Math.max(1, (System.nanoTime() - startedAt) / 1_000_000);
        publishResult(request, agent, taskType, resultExtractor.apply(output).withDuration(durationMs), resultPayload.apply(output));
        return output;
    }

    private void publishTask(TripRequest request, String agent, String taskType) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("requestId", request.requestId());
        body.put("agent", agent);
        body.put("taskType", taskType);
        body.put("origin", request.origin());
        body.put("destination", request.destination());
        body.put("travelers", String.valueOf(request.travelers()));
        body.put("budget", String.valueOf(request.budget()));
        body.put("departureDate", request.departureDate());
        body.put("returnDate", request.returnDate());
        body.put("vibe", request.vibe() == null ? "" : request.vibe());
        body.put("timestamp", Instant.now().toString());
        publish(streamName(taskPrefix, agent, "tasks"), body);
    }

    private void publishResult(
            TripRequest request,
            String agent,
            String taskType,
            AgentResult result,
            Map<String, String> payload
    ) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("requestId", request.requestId());
        body.put("agent", agent);
        body.put("taskType", taskType);
        body.put("status", result.status());
        body.put("summary", result.summary());
        body.put("durationMs", result.durationMs() == null ? "" : String.valueOf(result.durationMs()));
        body.put("degraded", String.valueOf(Boolean.TRUE.equals(result.degraded())));
        body.put("timestamp", Instant.now().toString());
        body.putAll(payload);
        publish(streamName(resultPrefix, agent, "results"), body);
    }

    private String streamName(String prefix, String agent, String suffix) {
        return prefix + "." + agent.replace("-agent", "").replace("-", ".") + "." + suffix;
    }

    private void publish(String stream, Map<String, String> body) {
        if (!enabled) {
            return;
        }
        try {
            redisTemplate.opsForStream().add(MapRecord.create(stream, body));
        } catch (RuntimeException ignored) {
            // Redis task/result streams are an orchestration enhancement. Planning must still work without Redis.
        }
    }
}
