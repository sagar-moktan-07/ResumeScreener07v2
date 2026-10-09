package com.example.aiservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class AiController {
    private final SemanticSearchService semanticSearchService;
    private final DatabaseService databaseService;
    private final OllamaService ollamaService;

    @Value("${app.name:resume-screener-ai}")
    private String serviceName;

    @Value("${app.version:1.0.0}")
    private String serviceVersion;

    @Value("${embedding.model:java-hash-embedding}")
    private String embeddingModel;

    public AiController(SemanticSearchService semanticSearchService, DatabaseService databaseService, OllamaService ollamaService) {
        this.semanticSearchService = semanticSearchService;
        this.databaseService = databaseService;
        this.ollamaService = ollamaService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("service", serviceName);
        response.put("version", serviceVersion);
        response.put("db_ready", true);
        response.put("embedding_model", embeddingModel);
        return response;
    }

    @GetMapping("/data-ready")
    public Map<String, Object> dataReady() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("service", serviceName);
        response.put("message", "AI service is ready");
        response.put("ready", true);
        return response;
    }

    @PostMapping("/data-ready")
    public Map<String, Object> dataReadyPost(@RequestBody Map<String, Object> payload) {
        String event = String.valueOf(payload.getOrDefault("event", ""));
        if (!"resume_uploaded".equals(event)) {
            throw new IllegalArgumentException("Unsupported event");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "accepted");
        response.put("event", event);
        response.put("batch_id", payload.get("batch_id"));
        response.put("candidate_ids", payload.getOrDefault("candidate_ids", java.util.List.of()));
        response.put("vacancy_id", payload.get("vacancy_id"));
        response.put("count", payload.getOrDefault("count", 0));
        response.put("message", "Java payload accepted by AI service");
        return response;
    }

    @PostMapping("/screening/run")
    public Map<String, Object> screeningRun(@RequestBody Map<String, Object> payload) {
        Integer vacancyId = parseInt(payload.get("vacancy_id"));
        Long candidateId = parseLong(payload.get("candidate_id"));
        String vacancyText = payload.get("vacancy_text") == null ? "" : payload.get("vacancy_text").toString();
        String resumeText = payload.get("resume_text") == null ? "" : payload.get("resume_text").toString();

        if (vacancyText == null || vacancyText.isBlank() || resumeText == null || resumeText.isBlank()) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Both vacancy text and resume text are required");
            return error;
        }

        Map<String, Object> result = semanticSearchService.runScreening(vacancyId, candidateId, vacancyText, resumeText);

        if ("ok".equals(result.get("status"))) {
            String ragContext = semanticSearchService.buildRagContext(vacancyId, vacancyText, resumeText);
            String prompt = ragContext + "\nUse the vacancy description, the current resume, and the ranked/semifinalized context above. Return exactly one sentence that concludes whether this candidate is a good fit for the role, and keep it concise. Do not use labels like shortlisted or recommended; just write a final concluding sentence.";
            String conclusion = ollamaService.generateConclusion(prompt);
            String normalized = normalizeConclusion(conclusion);
            result.put("reasoning", normalized);
        }

        return result;
    }

    @PostMapping("/semantic-search")
    public Map<String, Object> semanticSearch(@RequestBody Map<String, Object> payload) {
        Integer vacancyId = parseInt(payload.get("vacancy_id"));
        String vacancyText = payload.get("vacancy_text") == null ? "" : payload.get("vacancy_text").toString();
        int limit = payload.get("limit") == null ? 10 : Math.max(1, parseInt(payload.get("limit")));
        if (vacancyId == null || vacancyText == null || vacancyText.isBlank()) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "vacancy_id and vacancy_text are required");
            return error;
        }
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("vacancy_id", vacancyId);
        response.put("results", semanticSearchService.generateRankedCandidates(vacancyId, vacancyText, limit));
        return response;
    }

    private Integer parseInt(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeConclusion(String text) {
        if (text == null || text.isBlank()) {
            return "This candidate is a reasonable fit for the role based on the available evidence.";
        }
        String cleaned = text.replace("\n", " ").trim();
        String[] sentences = cleaned.split("\\.");
        if (sentences.length > 0 && !sentences[0].isBlank()) {
            String first = sentences[0].trim();
            if (!first.endsWith(".")) {
                first += ".";
            }
            return first;
        }
        return cleaned + ".";
    }
}
