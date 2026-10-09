package com.example.aiservice;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Service
public class RerankerService {

    public double rerankPair(String vacancyText, String resumeText) {
        if (vacancyText == null || vacancyText.isBlank() || resumeText == null || resumeText.isBlank()) {
            return 0.0;
        }

        Set<String> vacancyTerms = tokenize(vacancyText);
        Set<String> resumeTerms = tokenize(resumeText);
        if (vacancyTerms.isEmpty() || resumeTerms.isEmpty()) {
            return 0.0;
        }

        Set<String> overlap = new HashSet<>(vacancyTerms);
        overlap.retainAll(resumeTerms);

        double overlapRatio = overlap.size() / (double) Math.max(1, Math.max(vacancyTerms.size(), resumeTerms.size()));
        double tokenScore = Math.min(1.0, overlapRatio * 1.8);

        double specialtyBoost = 0.0;
        String[] roleSignals = {"java", "spring", "sql", "postgres", "api", "microservices", "cloud", "azure", "aws", "kubernetes", "docker", "python", "ai", "ml"};
        for (String signal : roleSignals) {
            if (vacancyText.toLowerCase(Locale.ROOT).contains(signal) && resumeText.toLowerCase(Locale.ROOT).contains(signal)) {
                specialtyBoost += 0.08;
            }
        }

        double score = Math.min(1.0, tokenScore + specialtyBoost);
        return Math.max(0.0, Math.min(1.0, score));
    }

    private Set<String> tokenize(String text) {
        String clean = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ");
        String[] parts = clean.split("\\s+");
        Set<String> tokens = new HashSet<>();
        Arrays.stream(parts)
            .filter(part -> part != null && !part.isBlank() && part.length() > 2)
            .forEach(tokens::add);
        return tokens;
    }
}
