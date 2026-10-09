package com.example.aiservice;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SemanticSearchService {
    private final EmbeddingService embeddingService;
    private final RerankerService rerankerService;
    private final DatabaseService databaseService;

    public SemanticSearchService(EmbeddingService embeddingService, RerankerService rerankerService, DatabaseService databaseService) {
        this.embeddingService = embeddingService;
        this.rerankerService = rerankerService;
        this.databaseService = databaseService;
    }

    public Map<String, Object> runScreening(Integer vacancyId, Long candidateId, String vacancyText, String resumeText) {
        if (vacancyText == null || vacancyText.isBlank() || resumeText == null || resumeText.isBlank()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("status", "error");
            payload.put("score", 0.0);
            payload.put("match_percentage", 0);
            payload.put("semantic_similarity", 0.0);
            payload.put("rerank_score", 0.0);
            return payload;
        }

        float[] vacancyVector = embeddingService.embedText(vacancyText);
        float[] resumeVector = embeddingService.embedText(resumeText);
        double semanticSimilarity = embeddingService.cosineSimilarity(vacancyVector, resumeVector);
        double rerankScore = rerankerService.rerankPair(vacancyText, resumeText);
        double score = Math.max(0.0, Math.min(1.0, (semanticSimilarity + rerankScore) / 2.0));
        int matchPercentage = (int) Math.round(score * 100);

        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "ok");
        payload.put("vacancy_id", vacancyId);
        payload.put("candidate_id", candidateId);
        payload.put("score", round(score));
        payload.put("match_percentage", matchPercentage);
        payload.put("semantic_similarity", round(semanticSimilarity));
        payload.put("rerank_score", round(rerankScore));
        payload.put("reasoning", "Semantic search and Java reranking were combined to evaluate this candidate.");
        payload.put("embedding_size", vacancyVector.length);
        return payload;
    }

    public List<Map<String, Object>> generateRankedCandidates(Integer vacancyId, String vacancyText, int limit) {
        List<Map<String, Object>> candidateRows = databaseService.fetchCandidates(Math.max(10, limit * 5));
        if (candidateRows.isEmpty()) {
            return List.of();
        }

        float[] vacancyVector = embeddingService.embedText(vacancyText);
        List<Map<String, Object>> ranked = new ArrayList<>();

        for (Map<String, Object> row : candidateRows) {
            Object id = row.get("id");
            String resumeText = buildResumeText(row);
            if (resumeText.isBlank()) {
                continue;
            }

            float[] resumeVector = embeddingService.embedText(resumeText);
            double semanticSimilarity = embeddingService.cosineSimilarity(vacancyVector, resumeVector);
            double rerankScore = rerankerService.rerankPair(vacancyText, resumeText);
            double finalScore = Math.max(0.0, Math.min(1.0, (semanticSimilarity + rerankScore) / 2.0));

            Map<String, Object> result = new HashMap<>();
            result.put("candidate_id", id instanceof Number number ? number.longValue() : Long.valueOf(id.toString()));
            result.put("candidate_name", row.getOrDefault("full_name", "Unknown Candidate"));
            result.put("email", row.get("email"));
            result.put("score", round(finalScore));
            result.put("semantic_similarity", round(semanticSimilarity));
            result.put("rerank_score", round(rerankScore));
            result.put("match_percentage", (int) Math.round(finalScore * 100));
            ranked.add(result);
        }

        ranked.sort((left, right) -> Double.compare(
            ((Number) right.get("score")).doubleValue(),
            ((Number) left.get("score")).doubleValue()
        ));
        return ranked.stream().limit(limit).toList();
    }

    public String buildRagContext(Integer vacancyId, String vacancyText, String resumeText) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are evaluating a candidate for a hiring decision.\n");
        builder.append("Vacancy: ").append(truncate(vacancyText, 800)).append("\n");
        builder.append("Current candidate resume: ").append(truncate(resumeText, 1200)).append("\n");

        List<Map<String, Object>> ranked = generateRankedCandidates(vacancyId, vacancyText, 5);
        if (!ranked.isEmpty()) {
            builder.append("Recent top-ranked candidates for this vacancy:\n");
            for (Map<String, Object> item : ranked) {
                builder.append("- ")
                    .append(item.getOrDefault("candidate_name", "Unknown"))
                    .append(" (candidate_id=")
                    .append(item.get("candidate_id"))
                    .append("): score=")
                    .append(item.get("score"))
                    .append(", fit=")
                    .append(item.get("match_percentage"))
                    .append("%\n");
            }
        }

        List<Map<String, Object>> semifinalized = databaseService.fetchSemifinalizedCandidates(vacancyId, 5);
        if (!semifinalized.isEmpty()) {
            builder.append("Previously semifinalized candidates for this vacancy:\n");
            for (Map<String, Object> item : semifinalized) {
                builder.append("- ")
                    .append(item.getOrDefault("full_name", "Unknown"))
                    .append(" (candidate_id=")
                    .append(item.get("candidate_id"))
                    .append("): score=")
                    .append(item.get("score"))
                    .append(", status=")
                    .append(item.getOrDefault("status", "unknown"))
                    .append("\n");
            }
        }

        return builder.toString();
    }

    private String buildResumeText(Map<String, Object> row) {
        StringBuilder builder = new StringBuilder();
        append(builder, row.get("skills"));
        append(builder, row.get("qualifications"));
        append(builder, row.get("experience"));
        append(builder, row.get("raw_text"));
        return builder.toString();
    }

    private void append(StringBuilder builder, Object value) {
        if (value == null) {
            return;
        }
        String text = value.toString().trim();
        if (!text.isBlank()) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(text);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength - 3) + "...";
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
