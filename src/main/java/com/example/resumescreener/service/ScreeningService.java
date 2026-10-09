package com.example.resumescreener.service;

import com.example.resumescreener.client.FastApiClient;
import com.example.resumescreener.entity.CandidateResume;
import com.example.resumescreener.entity.FinalizedCandidate;
import com.example.resumescreener.entity.ScreeningResult;
import com.example.resumescreener.entity.ScreeningRun;
import com.example.resumescreener.entity.SemifinalizedCandidate;
import com.example.resumescreener.entity.UserCredentials;
import com.example.resumescreener.entity.VacancyApplication;
import com.example.resumescreener.entity.VacancyPost;
import com.example.resumescreener.repository.CandidateResumeRepository;
import com.example.resumescreener.repository.FinalizedCandidateRepository;
import com.example.resumescreener.repository.ScreeningResultRepository;
import com.example.resumescreener.repository.ScreeningRunRepository;
import com.example.resumescreener.repository.SemifinalizedCandidateRepository;
import com.example.resumescreener.repository.UserCredentialsRepository;
import com.example.resumescreener.repository.VacancyApplicationRepository;
import com.example.resumescreener.repository.VacancyPostRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ScreeningService {

    private final VacancyPostRepository vacancyPostRepository;
    private final CandidateResumeRepository candidateResumeRepository;
    private final ScreeningRunRepository screeningRunRepository;
    private final ScreeningResultRepository screeningResultRepository;
    private final SemifinalizedCandidateRepository semifinalizedCandidateRepository;
    private final FinalizedCandidateRepository finalizedCandidateRepository;
    private final VacancyApplicationRepository vacancyApplicationRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final FastApiClient fastApiClient;

    public ScreeningService(VacancyPostRepository vacancyPostRepository,
                           CandidateResumeRepository candidateResumeRepository,
                           ScreeningRunRepository screeningRunRepository,
                           ScreeningResultRepository screeningResultRepository,
                           SemifinalizedCandidateRepository semifinalizedCandidateRepository,
                           FinalizedCandidateRepository finalizedCandidateRepository,
                           VacancyApplicationRepository vacancyApplicationRepository,
                           UserCredentialsRepository userCredentialsRepository,
                           FastApiClient fastApiClient) {
        this.vacancyPostRepository = vacancyPostRepository;
        this.candidateResumeRepository = candidateResumeRepository;
        this.screeningRunRepository = screeningRunRepository;
        this.screeningResultRepository = screeningResultRepository;
        this.semifinalizedCandidateRepository = semifinalizedCandidateRepository;
        this.finalizedCandidateRepository = finalizedCandidateRepository;
        this.vacancyApplicationRepository = vacancyApplicationRepository;
        this.userCredentialsRepository = userCredentialsRepository;
        this.fastApiClient = fastApiClient;
    }

    public Map<String, Object> runForVacancy(Long vacancyId) {
        VacancyPost vacancy = vacancyPostRepository.findById(vacancyId)
            .orElseThrow(() -> new IllegalArgumentException("Vacancy not found: " + vacancyId));

        ScreeningRun screeningRun = new ScreeningRun();
        screeningRun.setVacancyId(vacancyId);
        screeningRun.setRunName("Vacancy screening - " + vacancy.getJobTitle());
        screeningRun.setStatus("running");
        screeningRun = screeningRunRepository.save(screeningRun);

        List<VacancyApplication> applications = vacancyApplicationRepository.findByVacancyIdOrderByAppliedAtDesc(vacancyId);
        List<Map<String, Object>> results = new ArrayList<>();

        List<CandidateResume> screenedCandidates = new ArrayList<>();
        for (VacancyApplication application : applications) {
            candidateResumeRepository.findById(application.getCandidateResumeId()).ifPresent(screenedCandidates::add);
        }

        if (screenedCandidates.isEmpty()) {
            screeningRun.setStatus("completed");
            screeningRun.setCompletedAt(java.time.LocalDateTime.now());
            screeningRun.setSummary("No applicants found for vacancy " + vacancyId + ".");
            screeningRunRepository.save(screeningRun);
            Map<String, Object> response = new HashMap<>();
            response.put("vacancy_id", vacancyId);
            response.put("vacancy_title", vacancy.getJobTitle());
            response.put("evaluated_candidates", 0);
            response.put("screening_run_id", screeningRun.getId());
            response.put("results", List.of());
            response.put("top_candidates", List.of());
            response.put("semifinalized_candidates", List.of());
            response.put("finalized_candidates", List.of());
            return response;
        }

        for (CandidateResume candidate : screenedCandidates) {
            String vacancyText = String.join(" ",
                safe(vacancy.getJobTitle()),
                safe(vacancy.getDepartment()),
                safe(vacancy.getJobDescription()),
                safe(vacancy.getRequiredSkills()),
                safe(vacancy.getPreferredSkills()),
                safe(vacancy.getQualifications()),
                safe(vacancy.getResponsibilities())
            );

            String resumeText = candidate.getRawText() == null ? "" : candidate.getRawText();
            Map<String, Object> response = fastApiClient.runScreening(vacancyId, candidate.getId(), vacancyText, resumeText);

            Double score = parseDouble(response.get("score"));
            Integer matchPercentage = parseInteger(response.get("match_percentage"));
            String reasoning = response.get("reasoning") == null ? "" : response.get("reasoning").toString();

            ScreeningResult screeningResult = new ScreeningResult();
            screeningResult.setScreeningRunId(screeningRun.getId());
            screeningResult.setCandidateId(candidate.getId());
            screeningResult.setVacancyId(vacancyId);
            screeningResult.setOverallScore(score);
            screeningResult.setReasoning(reasoning);
            screeningResult.setStatus("scored");
            screeningResultRepository.save(screeningResult);

            UserCredentials user = candidate.getUserId() == null ? null : userCredentialsRepository.findById(candidate.getUserId()).orElse(null);
            String candidateName = user != null && user.getName() != null && !user.getName().isBlank()
                ? user.getName()
                : candidate.getFullName();
            String candidateEmail = user != null && user.getEmail() != null && !user.getEmail().isBlank()
                ? user.getEmail()
                : candidate.getEmail();

            Map<String, Object> item = new HashMap<>();
            item.put("candidate_id", candidate.getId());
            item.put("candidate_name", candidateName);
            item.put("email", candidateEmail);
            item.put("score", score);
            item.put("match_percentage", matchPercentage);
            item.put("status", response.getOrDefault("status", "ok"));
            item.put("reasoning", reasoning);
            results.add(item);
        }

        results.sort(Comparator.comparing((Map<String, Object> item) -> parseDouble(item.get("score"))).reversed());

        List<Map<String, Object>> topCandidates = results.stream().limit(5).toList();
        List<Map<String, Object>> semifinalizedCandidates = new ArrayList<>();
        List<Map<String, Object>> finalizedCandidates = new ArrayList<>();

        int semifinalLimit = Math.min(5, results.size());
        for (int i = 0; i < semifinalLimit; i++) {
            Map<String, Object> item = results.get(i);
            Double score = parseDouble(item.get("score"));
            Long candidateId = parseLong(item.get("candidate_id"));

            SemifinalizedCandidate semifinalizedCandidate = new SemifinalizedCandidate();
            semifinalizedCandidate.setVacancyId(vacancyId);
            semifinalizedCandidate.setCandidateId(candidateId);
            semifinalizedCandidate.setScore(score);
            semifinalizedCandidate.setStatus("semifinalized");
            semifinalizedCandidateRepository.save(semifinalizedCandidate);

            semifinalizedCandidates.add(Map.of(
                "candidate_id", candidateId,
                "score", score,
                "status", "semifinalized"
            ));

            if (score >= 0.6 || i < 2) {
                FinalizedCandidate finalizedCandidate = new FinalizedCandidate();
                finalizedCandidate.setVacancyId(vacancyId);
                finalizedCandidate.setCandidateId(candidateId);
                finalizedCandidate.setFinalScore(score);
                finalizedCandidate.setDecision(i == 0 ? "recommended" : "shortlist");
                finalizedCandidate.setConclusion(
                    i == 0
                        ? "This candidate is a strong fit for the role and is recommended for the next stage."
                        : "This candidate is a good fit for the role and should be considered for further review."
                );
                finalizedCandidateRepository.save(finalizedCandidate);

                finalizedCandidates.add(Map.of(
                    "candidate_id", candidateId,
                    "final_score", score,
                    "decision", finalizedCandidate.getDecision(),
                    "conclusion", finalizedCandidate.getConclusion()
                ));
            }
        }

        screeningRun.setStatus("completed");
        screeningRun.setCompletedAt(java.time.LocalDateTime.now());
        screeningRun.setSummary("Evaluated " + results.size() + " candidates. " + semifinalizedCandidates.size() + " semifinalized and " + finalizedCandidates.size() + " finalized.");
        screeningRunRepository.save(screeningRun);

        Map<String, Object> response = new HashMap<>();
        response.put("vacancy_id", vacancyId);
        response.put("vacancy_title", vacancy.getJobTitle());
        response.put("evaluated_candidates", results.size());
        response.put("screening_run_id", screeningRun.getId());
        response.put("results", results);
        response.put("top_candidates", topCandidates);
        response.put("semifinalized_candidates", semifinalizedCandidates);
        response.put("finalized_candidates", finalizedCandidates);
        return response;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private Double parseDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
