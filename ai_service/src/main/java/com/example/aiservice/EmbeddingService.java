package com.example.aiservice;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class EmbeddingService {
    private static final int VECTOR_SIZE = 256;

    public float[] embedText(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        String[] tokens = normalized.replaceAll("[^a-z0-9\\s]", " ").split("\\s+");
        Map<Integer, Integer> counts = new HashMap<>();
        int maxCount = 1;

        for (String token : tokens) {
            if (token == null || token.isBlank() || token.length() < 2) {
                continue;
            }
            int bucket = Math.abs(token.hashCode()) % VECTOR_SIZE;
            int value = counts.getOrDefault(bucket, 0) + 1;
            counts.put(bucket, value);
            if (value > maxCount) {
                maxCount = value;
            }
        }

        float[] vector = new float[VECTOR_SIZE];
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            double ratio = entry.getValue() / (double) Math.max(1, maxCount);
            vector[entry.getKey()] = (float) ratio;
        }

        double norm = 0.0;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm > 0.0) {
            norm = Math.sqrt(norm);
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) (vector[i] / norm);
            }
        }

        return vector;
    }

    public double cosineSimilarity(float[] left, float[] right) {
        if (left == null || right == null || left.length != right.length || left.length == 0) {
            return 0.0;
        }

        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }

        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }

        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
