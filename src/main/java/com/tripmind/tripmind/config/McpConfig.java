package com.tripmind.tripmind.config;

import com.tripmind.tripmind.service.ai.TripMindMcpTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("mcp")
public class McpConfig {
    @Bean
    ToolCallbackProvider tripMindMcpToolCallbacks(TripMindMcpTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
