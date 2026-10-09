package com.example.resumescreener.controller;

import com.example.resumescreener.entity.CandidateResume;
import com.example.resumescreener.entity.UserCredentials;
import com.example.resumescreener.entity.VacancyApplication;
import com.example.resumescreener.entity.VacancyPost;
import com.example.resumescreener.repository.CandidateResumeRepository;
import com.example.resumescreener.repository.UserCredentialsRepository;
import com.example.resumescreener.repository.VacancyApplicationRepository;
import com.example.resumescreener.repository.VacancyPostRepository;
import com.example.resumescreener.service.ResumeUploadService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ResumeApiController {

    private final ResumeUploadService resumeUploadService;
    private final CandidateResumeRepository candidateResumeRepository;
    private final VacancyPostRepository vacancyPostRepository;
    private final VacancyApplicationRepository vacancyApplicationRepository;
    private final UserCredentialsRepository userCredentialsRepository;

    public ResumeApiController(ResumeUploadService resumeUploadService,
                             CandidateResumeRepository candidateResumeRepository,
                             VacancyPostRepository vacancyPostRepository,
                             VacancyApplicationRepository vacancyApplicationRepository,
                             UserCredentialsRepository userCredentialsRepository) {
        this.resumeUploadService = resumeUploadService;
        this.candidateResumeRepository = candidateResumeRepository;
        this.vacancyPostRepository = vacancyPostRepository;
        this.vacancyApplicationRepository = vacancyApplicationRepository;
        this.userCredentialsRepository = userCredentialsRepository;
    }

    @PostMapping(value = "/resumes/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('USER')")
    public Map<String, Object> upload(@RequestParam(value = "files", required = false) List<MultipartFile> files,
                                    Authentication authentication) {
        if (files == null || files.isEmpty()) {
            return Map.of(
                "stored", 0,
                "failed", 0,
                "fastApiNotified", false,
                "savedFiles", List.of(),
                "uploaded", List.of(),
                "message", "No PDF files were provided."
            );
        }

        UserCredentials user = userCredentialsRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            return Map.of(
                "stored", 0,
                "failed", files.size(),
                "fastApiNotified", false,
                "savedFiles", List.of(),
                "uploaded", List.of(),
                "message", "User account could not be found."
            );
        }

        return resumeUploadService.processBatch(files, user.getId());
    }

    @GetMapping("/candidates")
    public List<CandidateResume> candidates(Authentication authentication) {
        if (authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"))) {
            return candidateResumeRepository.findAllByOrderByIdDesc();
        }

        UserCredentials user = userCredentialsRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            return List.of();
        }
        return candidateResumeRepository.findByUserIdOrderByIdDesc(user.getId());
    }

    @GetMapping("/vacancies")
    public List<VacancyPost> vacancies() {
        return vacancyPostRepository.findAllByOrderByIdDesc();
    }

    @PostMapping("/vacancy-apply")
    @PreAuthorize("hasRole('USER')")
    public Map<String, Object> applyToVacancy(@RequestParam Long vacancyId,
                                            Authentication authentication) {
        UserCredentials user = userCredentialsRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            return Map.of("status", "error", "message", "User not found");
        }

        CandidateResume candidate = candidateResumeRepository.findFirstByUserIdOrderByIdDesc(user.getId()).orElse(null);
        if (candidate == null) {
            return Map.of("status", "error", "message", "Upload a resume before applying to a vacancy.");
        }

        if (vacancyApplicationRepository.existsByUserIdAndVacancyId(user.getId(), vacancyId)) {
            return Map.of("status", "exists", "message", "You already applied to this vacancy.");
        }

        long applicationCount = vacancyApplicationRepository.countByUserId(user.getId());
        if (applicationCount >= 2) {
            return Map.of("status", "limit", "message", "You can apply to a maximum of 2 vacancies.");
        }

        VacancyPost vacancy = vacancyPostRepository.findById(vacancyId).orElse(null);
        if (vacancy == null) {
            return Map.of("status", "error", "message", "Selected vacancy was not found.");
        }

        VacancyApplication application = new VacancyApplication();
        application.setUserId(user.getId());
        application.setVacancyId(vacancyId);
        application.setVacancyCode(vacancy.getDisplayCode());
        application.setCandidateResumeId(candidate.getId());
        application.setCandidateCode(candidate.getDisplayCode());
        application.setStatus("applied");
        vacancyApplicationRepository.save(application);

        return Map.of(
            "status", "ok",
            "message", "Application submitted successfully.",
            "vacancyId", vacancyId,
            "vacancyCode", vacancy.getDisplayCode(),
            "candidateResumeId", candidate.getId()
        );
    }

    @GetMapping("/my-applications")
    @PreAuthorize("hasRole('USER')")
    public List<Map<String, Object>> myApplications(Authentication authentication) {
        UserCredentials user = userCredentialsRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            return List.of();
        }

        List<Map<String, Object>> applications = new ArrayList<>();
        for (var application : vacancyApplicationRepository.findByUserIdOrderByAppliedAtDesc(user.getId())) {
            var vacancy = vacancyPostRepository.findById(application.getVacancyId()).orElse(null);
            Map<String, Object> item = new HashMap<>();
            item.put("applicationId", application.getId());
            item.put("vacancyId", application.getVacancyId());
            item.put("vacancyCode", application.getVacancyCodeOrDefault());
            item.put("vacancyTitle", vacancy != null ? vacancy.getJobTitle() : "Unknown vacancy");
            item.put("candidateCode", application.getCandidateCodeOrDefault());
            item.put("status", application.getStatus());
            item.put("appliedAt", application.getAppliedAt());
            applications.add(item);
        }
        return applications;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "service", "resume-screener-java");
    }
}
