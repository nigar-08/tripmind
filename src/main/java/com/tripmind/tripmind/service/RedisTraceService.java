package com.tripmind.tripmind.service;

import com.tripmind.tripmind.dto.RedisStreamEventView;
import com.tripmind.tripmind.dto.WorkflowTraceView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RedisTraceService {

    private final StringRedisTemplate redisTemplate;
    private final String workflowStream;
    private final String taskPrefix;
    private final String resultPrefix;

    public RedisTraceService(
            StringRedisTemplate redisTemplate,
            @Value("${tripmind.redis.events.stream:tripmind.workflow.events}") String workflowStream,
            @Value("${tripmind.redis.agent-bus.task-prefix:tripmind.agent}") String taskPrefix,
            @Value("${tripmind.redis.agent-bus.result-prefix:tripmind.agent}") String resultPrefix
    ) {
        this.redisTemplate = redisTemplate;
        this.workflowStream = workflowStream;
        this.taskPrefix = taskPrefix;
        this.resultPrefix = resultPrefix;
    }

    public WorkflowTraceView trace(String requestId) {
        List<RedisStreamEventView> events = new ArrayList<>();
        for (String stream : traceStreams()) {
            events.addAll(readMatchingEvents(stream, requestId));
        }

        events.sort(Comparator
                .comparing((RedisStreamEventView event) -> event.fields().getOrDefault("timestamp", ""))
                .thenComparing(RedisStreamEventView::id));

        return new WorkflowTraceView(requestId, events.size(), events);
    }

    private List<String> traceStreams() {
        return List.of(
                workflowStream,
                streamName(taskPrefix, "flight-agent", "tasks"),
                streamName(resultPrefix, "flight-agent", "results"),
                streamName(taskPrefix, "hotel-agent", "tasks"),
                streamName(resultPrefix, "hotel-agent", "results"),
                streamName(taskPrefix, "weather-agent", "tasks"),
                streamName(resultPrefix, "weather-agent", "results")
        );
    }

    private List<RedisStreamEventView> readMatchingEvents(String stream, String requestId) {
        try {
            List<MapRecord<String, Object, Object>> records =
                    redisTemplate.opsForStream().range(stream, Range.unbounded());
            if (records == null || records.isEmpty()) {
                return List.of();
            }

            List<RedisStreamEventView> matchingEvents = new ArrayList<>();
            for (MapRecord<String, Object, Object> record : records) {
                Map<String, String> fields = stringFields(record.getValue());
                if (requestId.equals(fields.get("requestId"))) {
                    matchingEvents.add(new RedisStreamEventView(stream, record.getId().getValue(), fields));
                }
            }
            return matchingEvents;
        } catch (RuntimeException ignored) {
            // Trace inspection should never break planning APIs if Redis is offline or empty.
            return List.of();
        }
    }

    private Map<String, String> stringFields(Map<Object, Object> rawFields) {
        Map<String, String> fields = new LinkedHashMap<>();
        rawFields.forEach((key, value) -> fields.put(String.valueOf(key), String.valueOf(value)));
        return fields;
    }

    private String streamName(String prefix, String agent, String suffix) {
        return prefix + "." + agent.replace("-agent", "").replace("-", ".") + "." + suffix;
    }
}
