package com.example.resumescreener.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class FastApiClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${fastapi.base-url:http://localhost:8002}")
    private String baseUrl;

    public boolean notifyResumeUploaded(String batchId, List<Long> candidateIds, Long vacancyId, int count) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "resume_uploaded");
        payload.put("batch_id", batchId);
        payload.put("candidate_ids", candidateIds);
        payload.put("vacancy_id", vacancyId);
        payload.put("count", count);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl + "/data-ready", payload, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception ex) {
            return false;
        }
    }

    public Map<String, Object> runScreening(Long vacancyId, Long candidateId, String vacancyText, String resumeText) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("vacancy_id", vacancyId);
        payload.put("candidate_id", candidateId);
        payload.put("vacancy_text", vacancyText);
        payload.put("resume_text", resumeText);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(baseUrl + "/screening/run", payload, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception ignored) {
        }

        Map<String, Object> fallback = new HashMap<>();
        fallback.put("status", "error");
        fallback.put("score", 0.0);
        fallback.put("match_percentage", 0);
        return fallback;
    }
}
