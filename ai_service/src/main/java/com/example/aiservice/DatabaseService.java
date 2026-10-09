package com.example.aiservice;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DatabaseService {
    private final JdbcTemplate jdbcTemplate;

    public DatabaseService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> fetchCandidates(int limit) {
        return jdbcTemplate.queryForList(
            "SELECT id, file_name, full_name, email, phone, raw_text, skills, qualifications, experience FROM candidate_resume ORDER BY id DESC LIMIT ?",
            limit
        );
    }

    public Map<String, Object> fetchVacancy(int vacancyId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, job_title, department, job_description, required_skills, preferred_skills, qualifications, responsibilities, education_requirements, experience_requirements, status FROM vacancy_post WHERE id = ?",
            vacancyId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> fetchSemifinalizedCandidates(Integer vacancyId, int limit) {
        if (vacancyId != null) {
            return jdbcTemplate.queryForList(
                "SELECT sc.id, sc.vacancy_id, sc.candidate_id, sc.score, sc.status, cr.full_name, cr.email FROM semifinalized_candidate sc LEFT JOIN candidate_resume cr ON cr.id = sc.candidate_id WHERE sc.vacancy_id = ? ORDER BY sc.score DESC NULLS LAST, sc.id DESC LIMIT ?",
                vacancyId,
                limit
            );
        }
        return jdbcTemplate.queryForList(
            "SELECT sc.id, sc.vacancy_id, sc.candidate_id, sc.score, sc.status, cr.full_name, cr.email FROM semifinalized_candidate sc LEFT JOIN candidate_resume cr ON cr.id = sc.candidate_id ORDER BY sc.score DESC NULLS LAST, sc.id DESC LIMIT ?",
            limit
        );
    }

    public List<Map<String, Object>> fetchTopRankedCandidates(Integer vacancyId, int limit) {
        if (vacancyId != null) {
            return jdbcTemplate.queryForList(
                "SELECT id, candidate_id, score, status FROM semifinalized_candidate WHERE vacancy_id = ? ORDER BY score DESC NULLS LAST, id DESC LIMIT ?",
                vacancyId,
                limit
            );
        }
        return jdbcTemplate.queryForList(
            "SELECT id, candidate_id, score, status FROM semifinalized_candidate ORDER BY score DESC NULLS LAST, id DESC LIMIT ?",
            limit
        );
    }

    public void createCandidateEmbedding(int candidateId, List<Double> embedding, String source) {
        String vector = vectorToString(embedding);
        jdbcTemplate.update(
            "INSERT INTO candidate_embedding (candidate_id, embedding, source) VALUES (?, CAST(? AS vector), ?)",
            candidateId,
            vector,
            source
        );
    }

    public void createVacancyEmbedding(int vacancyId, List<Double> embedding, String source) {
        String vector = vectorToString(embedding);
        jdbcTemplate.update(
            "INSERT INTO vacancy_embedding (vacancy_id, embedding, source) VALUES (?, CAST(? AS vector), ?)",
            vacancyId,
            vector,
            source
        );
    }

    public List<Map<String, Object>> fetchCandidateEmbeddings() {
        return jdbcTemplate.queryForList(
            "SELECT id, candidate_id, source, embedding::text AS embedding_text FROM candidate_embedding ORDER BY id DESC"
        );
    }

    public List<Map<String, Object>> fetchVacancyEmbeddings() {
        return jdbcTemplate.queryForList(
            "SELECT id, vacancy_id, source, embedding::text AS embedding_text FROM vacancy_embedding ORDER BY id DESC"
        );
    }

    private String vectorToString(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        List<String> text = new ArrayList<>();
        for (Double value : values) {
            text.add(String.valueOf(value));
        }
        return "[" + String.join(",", text) + "]";
    }
}
