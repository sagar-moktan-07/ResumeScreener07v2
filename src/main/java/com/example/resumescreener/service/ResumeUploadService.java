package com.example.resumescreener.service;

import com.example.resumescreener.client.FastApiClient;
import com.example.resumescreener.entity.CandidateResume;
import com.example.resumescreener.entity.UserCredentials;
import com.example.resumescreener.repository.CandidateResumeRepository;
import com.example.resumescreener.repository.UserCredentialsRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ResumeUploadService {

    private final CandidateResumeRepository candidateResumeRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final PdfTextExtractor pdfTextExtractor;
    private final FastApiClient fastApiClient;

    public ResumeUploadService(CandidateResumeRepository candidateResumeRepository,
                              UserCredentialsRepository userCredentialsRepository,
                              PdfTextExtractor pdfTextExtractor,
                              FastApiClient fastApiClient) {
        this.candidateResumeRepository = candidateResumeRepository;
        this.userCredentialsRepository = userCredentialsRepository;
        this.pdfTextExtractor = pdfTextExtractor;
        this.fastApiClient = fastApiClient;
    }

    public Map<String, Object> processBatch(List<MultipartFile> files, Long userId) {
        if (userId == null) {
            return Map.of(
                "stored", 0,
                "failed", files == null ? 0 : files.size(),
                "fastApiNotified", false,
                "savedFiles", List.of(),
                "uploaded", List.of(),
                "message", "User session is missing. Please sign in again."
            );
        }

        if (candidateResumeRepository.existsByUserId(userId)) {
            return Map.of(
                "stored", 0,
                "failed", 0,
                "fastApiNotified", false,
                "savedFiles", List.of(),
                "uploaded", List.of(),
                "message", "You already uploaded a resume. Only one PDF per user is allowed."
            );
        }

        List<String> storedFiles = new ArrayList<>();
        List<Long> candidateIds = new ArrayList<>();
        int failed = 0;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                failed++;
                continue;
            }

            String originalName = file.getOriginalFilename() == null ? "resume.pdf" : file.getOriginalFilename();
            if (!originalName.toLowerCase().endsWith(".pdf")) {
                failed++;
                continue;
            }

            try {
                String extractedText = pdfTextExtractor.extractText(file);
                UserCredentials user = userCredentialsRepository.findById(userId).orElse(null);
                if (user != null && (user.getCandidateCode() == null || user.getCandidateCode().isBlank())) {
                    String candidateCode = "CAN-" + UserCredentials.generateRandomCode();
                    while (userCredentialsRepository.existsByCandidateCode(candidateCode)) {
                        candidateCode = "CAN-" + UserCredentials.generateRandomCode();
                    }
                    user.setCandidateCode(candidateCode);
                    userCredentialsRepository.save(user);
                }
                CandidateResume candidate = new CandidateResume();
                candidate.setUserId(userId);
                candidate.setCandidateCode(user != null ? user.getCandidateCode() : null);
                candidate.setFileName(originalName);
                candidate.setFullName(user != null && user.getName() != null && !user.getName().isBlank() ? user.getName() : pdfTextExtractor.extractName(extractedText));
                candidate.setEmail(user != null && user.getEmail() != null && !user.getEmail().isBlank() ? user.getEmail() : pdfTextExtractor.extractEmail(extractedText));
                candidate.setPhone(pdfTextExtractor.extractPhone(extractedText));
                candidate.setRawText(extractedText);
                candidate.setSkills(extractSkills(extractedText));
                candidate.setQualifications(extractQualifications(extractedText));
                candidate.setExperience(extractExperience(extractedText));
                CandidateResume saved = candidateResumeRepository.save(candidate);
                saved.setCandidateCode(saved.getDisplayCode());
                saved = candidateResumeRepository.save(saved);
                candidateIds.add(saved.getId());
                storedFiles.add(originalName);
            } catch (IOException e) {
                failed++;
            }
        }

        boolean notified = fastApiClient.notifyResumeUploaded("batch-java-" + System.currentTimeMillis(), candidateIds, null, storedFiles.size());

        Map<String, Object> result = new HashMap<>();
        result.put("stored", storedFiles.size());
        result.put("failed", failed);
        result.put("fastApiNotified", notified);
        result.put("savedFiles", storedFiles);
        result.put("uploaded", storedFiles);
        result.put("message", "Upload complete: " + storedFiles.size() + " file(s) stored, " + failed + " failed.");
        return result;
    }

    private String extractSkills(String text) {
        if (text == null || text.isBlank()) {
            return "Not specified";
        }
        String[] keywords = {"Java", "Spring", "SQL", "PostgreSQL", "Python", "FastAPI", "React", "AWS", "Docker", "Microservices", "Agile", "Leadership"};
        List<String> found = new ArrayList<>();
        for (String keyword : keywords) {
            if (text.toLowerCase().contains(keyword.toLowerCase())) {
                found.add(keyword);
            }
        }
        return found.isEmpty() ? "General software skills" : String.join(", ", found);
    }

    private String extractQualifications(String text) {
        if (text == null || text.isBlank()) {
            return "Not specified";
        }
        String[] keywords = {"Bachelor", "Master", "B.Tech", "M.Tech", "BSc", "MBA", "Computer Science", "Engineering"};
        List<String> found = new ArrayList<>();
        for (String keyword : keywords) {
            if (text.toLowerCase().contains(keyword.toLowerCase())) {
                found.add(keyword);
            }
        }
        return found.isEmpty() ? "Not specified" : String.join(", ", found);
    }

    private String extractExperience(String text) {
        if (text == null || text.isBlank()) {
            return "Not specified";
        }
        String[] keywords = {"experience", "years", "worked", "developer", "engineer", "analyst"};
        for (String keyword : keywords) {
            if (text.toLowerCase().contains(keyword.toLowerCase())) {
                return "Experience section detected";
            }
        }
        return "Not specified";
    }
}
