package com.tripmind.tripmind.service.ai;

import java.util.List;
import java.util.Map;

public interface AiProviderService {

    String polishChatReply(
            String userMessage,
            String draftReply,
            Map<String, Object> tripSnapshot,
            List<String> missingFields,
            boolean readyToPlan
    );

    String enrichAgentOutput(String agentName, String prompt, String fallback);
}
