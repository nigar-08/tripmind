package com.tripmind.tripmind.dto;

import java.util.Map;

public record RedisStreamEventView(
        String stream,
        String id,
        Map<String, String> fields
) {
}
