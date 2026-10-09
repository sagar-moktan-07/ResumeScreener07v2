package com.example.resumescreener.controller;

import com.example.resumescreener.entity.UserCredentials;
import com.example.resumescreener.entity.VacancyPost;
import com.example.resumescreener.entity.FinalizedCandidate;
import com.example.resumescreener.entity.VacancyApplication;
import com.example.resumescreener.repository.CandidateResumeRepository;
import com.example.resumescreener.repository.FinalizedCandidateRepository;
import com.example.resumescreener.repository.UserCredentialsRepository;
import com.example.resumescreener.repository.VacancyApplicationRepository;
import com.example.resumescreener.repository.VacancyPostRepository;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;
import java.util.regex.Pattern;

@Controller
public class SessionController {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private String generateUniqueCandidateCode() {
        String candidateCode;
        do {
            candidateCode = "CAN-" + UserCredentials.generateRandomCode();
        } while (userCredentialsRepository.existsByCandidateCode(candidateCode));
        return candidateCode;
    }

    private final VacancyPostRepository vacancyPostRepository;
    private final CandidateResumeRepository candidateResumeRepository;
    private final UserCredentialsRepository userCredentialsRepository;
    private final VacancyApplicationRepository vacancyApplicationRepository;
    private final FinalizedCandidateRepository finalizedCandidateRepository;
    private final PasswordEncoder passwordEncoder;

    public SessionController(VacancyPostRepository vacancyPostRepository,
                            CandidateResumeRepository candidateResumeRepository,
                            UserCredentialsRepository userCredentialsRepository,
                            VacancyApplicationRepository vacancyApplicationRepository,
                            FinalizedCandidateRepository finalizedCandidateRepository,
                            PasswordEncoder passwordEncoder) {
        this.vacancyPostRepository = vacancyPostRepository;
        this.candidateResumeRepository = candidateResumeRepository;
        this.userCredentialsRepository = userCredentialsRepository;
        this.vacancyApplicationRepository = vacancyApplicationRepository;
        this.finalizedCandidateRepository = finalizedCandidateRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping({"/", "/login"})
    public String login(@RequestParam(value = "error", required = false) String error,
                        Model model) {
        if (error != null) {
            model.addAttribute("loginError", "Invalid username or password.");
        }
        return "login";
    }

    @GetMapping("/signup")
    public String signup(Model model) {
        model.addAttribute("user", new UserCredentials());
        return "signup";
    }

    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute("user") UserCredentials userCredentials,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "signup";
        }

        String name = userCredentials.getName() == null ? "" : userCredentials.getName().trim();
        String username = userCredentials.getUsername() == null ? "" : userCredentials.getUsername().trim();
        String email = userCredentials.getEmail() == null ? "" : userCredentials.getEmail().trim();
        String password = userCredentials.getPassword() == null ? "" : userCredentials.getPassword();

        if (name.isEmpty() || username.isEmpty() || email.isEmpty() || password.isBlank()) {
            redirectAttributes.addFlashAttribute("signupError", "Name, username, email, and password are required.");
            return "redirect:/signup";
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            redirectAttributes.addFlashAttribute("signupError", "Please enter a valid email address.");
            return "redirect:/signup";
        }

        String normalizedUsername = username.trim();
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        if (userCredentialsRepository.existsByUsername(normalizedUsername)) {
            redirectAttributes.addFlashAttribute("signupError", "This username already exists. Please choose another one.");
            return "redirect:/signup";
        }

        if (userCredentialsRepository.existsByEmail(normalizedEmail)) {
            redirectAttributes.addFlashAttribute("signupError", "This email address is already registered.");
            return "redirect:/signup";
        }

        userCredentials.setName(name);
        userCredentials.setUsername(normalizedUsername);
        userCredentials.setEmail(normalizedEmail);
        userCredentials.setPassword(passwordEncoder.encode(password));
        userCredentials.setCandidateCode(generateUniqueCandidateCode());
        userCredentialsRepository.save(userCredentials);

        redirectAttributes.addFlashAttribute("signupSuccess", "Account created successfully. Please sign in.");
        return "redirect:/login";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model,
                           Authentication authentication,
                           @RequestParam(value = "selectedVacancyId", required = false) Long selectedVacancyId) {
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            return "redirect:/normal-user-dashboard";
        }

        model.addAttribute("isAdmin", true);
        model.addAttribute("vacancies", vacancyPostRepository.findAllByOrderByIdDesc());
        model.addAttribute("selectedVacancyId", selectedVacancyId);
        model.addAttribute("candidates", buildApplicantList(selectedVacancyId));
        model.addAttribute("vacancy", new VacancyPost());
        return "dashboard";
    }

    @GetMapping("/normal-user-dashboard")
    @PreAuthorize("hasRole('USER')")
    public String normalUserDashboard(Model model, Authentication authentication) {
        UserCredentials user = userCredentialsRepository.findByUsername(authentication.getName()).orElse(null);
        var candidate = user == null ? null : candidateResumeRepository.findFirstByUserIdOrderByIdDesc(user.getId()).orElse(null);
        String candidateStatus = "uploaded";

        if (candidate != null) {
            candidateStatus = candidate.getStatus() == null ? "uploaded" : candidate.getStatus();
            for (var finalized : finalizedCandidateRepository.findAllByOrderByIdDesc()) {
                if (finalized.getCandidateId() != null && finalized.getCandidateId().equals(candidate.getId())) {
                    if (finalized.getDecision() != null && !finalized.getDecision().isBlank()) {
                        candidateStatus = finalized.getDecision();
                        break;
                    }
                }
            }
        }

        model.addAttribute("user", user);
        model.addAttribute("vacancies", vacancyPostRepository.findAllByOrderByIdDesc());
        model.addAttribute("candidate", candidate);
        model.addAttribute("candidateStatus", candidateStatus);
        model.addAttribute("hasUploadedResume", user != null && candidateResumeRepository.existsByUserId(user.getId()));
        return "normal-user-dashboard";
    }

    private java.util.List<java.util.Map<String, Object>> buildApplicantList(Long selectedVacancyId) {
        java.util.List<VacancyApplication> applications = selectedVacancyId == null
            ? vacancyApplicationRepository.findAllByOrderByAppliedAtDesc()
            : vacancyApplicationRepository.findByVacancyIdOrderByAppliedAtDesc(selectedVacancyId);

        java.util.Map<Long, java.util.Map<String, Object>> deduped = new java.util.LinkedHashMap<>();
        for (VacancyApplication application : applications) {
            var candidate = candidateResumeRepository.findById(application.getCandidateResumeId()).orElse(null);
            if (candidate == null) {
                continue;
            }

            UserCredentials user = candidate.getUserId() == null ? null : userCredentialsRepository.findById(candidate.getUserId()).orElse(null);

            Long candidateKey = candidate.getId();
            java.util.Map<String, Object> item = deduped.get(candidateKey);
            if (item == null) {
                item = new java.util.HashMap<>();
                item.put("applicationId", application.getId());
                item.put("candidateResumeId", candidate.getId());
                item.put("id", candidate.getId());
                item.put("candidateCode", candidate.getDisplayCode());
                item.put("fullName", user != null && user.getName() != null && !user.getName().isBlank() ? user.getName() : candidate.getFullName());
                item.put("email", user != null && user.getEmail() != null && !user.getEmail().isBlank() ? user.getEmail() : candidate.getEmail());
                item.put("phone", candidate.getPhone());
                item.put("skills", candidate.getSkills());
                item.put("experience", candidate.getExperience());
                item.put("fileName", candidate.getFileName());
                item.put("vacancyCodes", new java.util.ArrayList<String>());
                item.put("vacancyIds", new java.util.ArrayList<Long>());
                item.put("vacancyCode", application.getVacancyCodeOrDefault());
                item.put("vacancyTitle", "");
                deduped.put(candidateKey, item);
            }

            @SuppressWarnings("unchecked")
            java.util.List<String> vacancyCodes = (java.util.List<String>) item.get("vacancyCodes");
            java.util.List<Long> vacancyIds = (java.util.List<Long>) item.get("vacancyIds");
            String vacancyCode = application.getVacancyCodeOrDefault();
            if (vacancyCode != null && !vacancyCode.isBlank() && !vacancyCodes.contains(vacancyCode)) {
                vacancyCodes.add(vacancyCode);
            }
            if (!vacancyIds.contains(application.getVacancyId())) {
                vacancyIds.add(application.getVacancyId());
            }

            item.put("vacancyCodesText", String.join(", ", vacancyCodes));
            item.put("vacancyId", vacancyIds.isEmpty() ? null : vacancyIds.get(0));
            item.put("vacancyTitle", vacancyIds.isEmpty() ? "" : String.join(", ", vacancyCodes));
        }
        return new java.util.ArrayList<>(deduped.values());
    }

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminUsers(Model model) {
        model.addAttribute("users", userCredentialsRepository.findAllByOrderByIdDesc());
        return "admin-users";
    }

    @GetMapping("/admin/candidates")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminCandidates(Model model,
                                 @RequestParam(value = "selectedVacancyId", required = false) Long selectedVacancyId) {
        model.addAttribute("vacancies", vacancyPostRepository.findAllByOrderByIdDesc());
        model.addAttribute("selectedVacancyId", selectedVacancyId);
        model.addAttribute("candidates", buildApplicantList(selectedVacancyId));
        return "admin-candidates";
    }

    @GetMapping("/admin/recent-applicants")
    @PreAuthorize("hasRole('ADMIN')")
    public String recentApplicants(Model model,
                                  @RequestParam(value = "selectedVacancyId", required = false) Long selectedVacancyId) {
        model.addAttribute("vacancies", vacancyPostRepository.findAllByOrderByIdDesc());
        model.addAttribute("selectedVacancyId", selectedVacancyId);
        model.addAttribute("candidates", buildApplicantList(selectedVacancyId));
        return "recent-applicants";
    }

    @GetMapping("/admin/finalized")
    @PreAuthorize("hasRole('ADMIN')")
    public String finalizedCandidates(Model model) {
        var finalized = finalizedCandidateRepository.findAllByOrderByIdDesc();
        var vacancyMap = new java.util.HashMap<Long, String>();
        var candidateNameMap = new java.util.HashMap<Long, String>();
        var candidateEmailMap = new java.util.HashMap<Long, String>();
        var candidateCodeMap = new java.util.HashMap<Long, String>();

        for (var vacancy : vacancyPostRepository.findAllByOrderByIdDesc()) {
            vacancyMap.put(vacancy.getId(), vacancy.getJobTitle());
        }
        for (var candidate : candidateResumeRepository.findAllByOrderByIdDesc()) {
            UserCredentials user = candidate.getUserId() == null ? null : userCredentialsRepository.findById(candidate.getUserId()).orElse(null);
            candidateNameMap.put(candidate.getId(), user != null && user.getName() != null && !user.getName().isBlank() ? user.getName() : candidate.getFullName());
            candidateEmailMap.put(candidate.getId(), user != null && user.getEmail() != null && !user.getEmail().isBlank() ? user.getEmail() : candidate.getEmail());
            candidateCodeMap.put(candidate.getId(), candidate.getDisplayCode());
        }

        model.addAttribute("finalizedCandidates", finalized);
        model.addAttribute("vacancyMap", vacancyMap);
        model.addAttribute("candidateNameMap", candidateNameMap);
        model.addAttribute("candidateEmailMap", candidateEmailMap);
        model.addAttribute("candidateCodeMap", candidateCodeMap);
        return "finalized-candidates";
    }

    @PostMapping("/admin/finalized/{id}/decision")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateDecision(@PathVariable Long id,
                                @RequestParam String decision,
                                RedirectAttributes redirectAttributes) {
        FinalizedCandidate candidate = finalizedCandidateRepository.findById(id).orElse(null);
        if (candidate != null) {
            String normalized = decision == null ? "" : decision.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("approved") || normalized.equals("rejected")) {
                candidate.setDecision(normalized);
                finalizedCandidateRepository.save(candidate);

                var resume = candidateResumeRepository.findById(candidate.getCandidateId()).orElse(null);
                if (resume != null) {
                    resume.setStatus(normalized);
                    candidateResumeRepository.save(resume);
                }

                for (var application : vacancyApplicationRepository.findAll()) {
                    if (application.getCandidateResumeId() != null && application.getCandidateResumeId().equals(candidate.getCandidateId())) {
                        application.setStatus(normalized);
                        vacancyApplicationRepository.save(application);
                    }
                }

                redirectAttributes.addFlashAttribute("message", "Decision updated to '" + normalized + "'.");
            }
        }
        return "redirect:/admin/finalized";
    }

    @PostMapping("/vacancies")
    public String createVacancy(@ModelAttribute("vacancy") VacancyPost vacancyPost) {
        vacancyPost.setUpdatedAt(java.time.LocalDateTime.now());
        VacancyPost saved = vacancyPostRepository.save(vacancyPost);
        saved.setVacancyCode(saved.getDisplayCode());
        vacancyPostRepository.save(saved);
        return "redirect:/dashboard";
    }

    @PostMapping("/admin/users/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        if (!userCredentialsRepository.existsById(id)) {
            return "redirect:/dashboard";
        }

        java.util.List<Long> candidateIds = candidateResumeRepository.findByUserIdOrderByIdDesc(id).stream()
            .map(com.example.resumescreener.entity.CandidateResume::getId)
            .toList();

        for (Long candidateId : candidateIds) {
            for (var application : vacancyApplicationRepository.findAll()) {
                if (application.getCandidateResumeId() != null && application.getCandidateResumeId().equals(candidateId)) {
                    vacancyApplicationRepository.delete(application);
                }
            }
            finalizedCandidateRepository.findAll().stream()
                .filter(item -> item.getCandidateId() != null && item.getCandidateId().equals(candidateId))
                .forEach(finalizedCandidateRepository::delete);
        }

        candidateResumeRepository.findByUserIdOrderByIdDesc(id).forEach(candidateResumeRepository::delete);
        vacancyApplicationRepository.findByUserIdOrderByAppliedAtDesc(id).forEach(vacancyApplicationRepository::delete);
        userCredentialsRepository.deleteById(id);

        redirectAttributes.addFlashAttribute("message", "User and all related data were removed successfully.");
        return "redirect:/dashboard";
    }

    @PostMapping("/admin/applications/{applicationId}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteApplicant(@PathVariable Long applicationId,
                                 @RequestParam(value = "selectedVacancyId", required = false) Long selectedVacancyId,
                                 RedirectAttributes redirectAttributes) {
        var application = vacancyApplicationRepository.findById(applicationId).orElse(null);
        if (application != null) {
            vacancyApplicationRepository.delete(application);
            redirectAttributes.addFlashAttribute("message", "Applicant removed from this vacancy.");
        }

        if (selectedVacancyId != null) {
            return "redirect:/dashboard?selectedVacancyId=" + selectedVacancyId;
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/admin/candidates/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteCandidate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        if (candidateResumeRepository.existsById(id)) {
            candidateResumeRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("message", "Candidate record removed successfully.");
        }
        return "redirect:/dashboard";
    }
}
