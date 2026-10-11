package com.tripmind.tripmind.service.ai;

import com.tripmind.tripmind.dto.CopilotResponse.Evidence;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Small, curated lexical RAG corpus. No embeddings or live-source claims. */
@Service
public class PlanningKnowledgeService {
    private final List<Evidence> documents;

    public PlanningKnowledgeService() throws IOException {
        try (var stream = new ClassPathResource("knowledge/planning-guidance.tsv").getInputStream()) {
            documents = new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.isBlank()).map(line -> line.split("\t", 4))
                    .map(parts -> new Evidence(parts[0], parts[1], parts[2],
                            parts.length == 4 ? parts[3] : "TripMind curated planning guidance"))
                    .toList();
        }
    }

    public List<Evidence> retrieve(String query) {
        return retrieve(query, "");
    }

    public List<Evidence> retrieve(String query, String destination) {
        Set<String> words = tokens(query);
        return documents.stream()
                .filter(doc -> !doc.id().contains("_") || doc.id().startsWith(destination.toLowerCase(Locale.ROOT) + "_")
                        || tokens(query).contains(doc.id().split("_")[0]))
                .filter(doc -> score(doc, words) > 0)
                .sorted(Comparator.<Evidence>comparingLong(doc -> score(doc, words)).reversed())
                .limit(3).toList();
    }

    private long score(Evidence doc, Set<String> words) {
        Set<String> terms = tokens(doc.title() + " " + doc.content());
        return words.stream().filter(terms::contains).count();
    }

    private Set<String> tokens(String text) {
        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("\\b(cheaper|cheap|save|savings|affordable)\\b", "budget cost stay");
        return Arrays.stream(normalized.split("[^a-z]+"))
                .filter(word -> word.length() >= 3)
                .filter(word -> !Set.of("the", "and", "for", "can", "how", "you", "are", "this", "that", "with", "what", "have", "does", "from", "your").contains(word))
                .collect(Collectors.toSet());
    }
}
