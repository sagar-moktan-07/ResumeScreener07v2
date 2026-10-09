package com.example.resumescreener.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "vacancy_application",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "vacancy_id"})
    })
public class VacancyApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "vacancy_id", nullable = false)
    private Long vacancyId;

    @Column(name = "vacancy_code")
    private String vacancyCode;

    @Column(name = "candidate_resume_id", nullable = false)
    private Long candidateResumeId;

    @Column(name = "candidate_code")
    private String candidateCode;

    @Column(name = "status", nullable = false)
    private String status = "applied";

    @Column(name = "applied_at")
    private LocalDateTime appliedAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getVacancyId() {
        return vacancyId;
    }

    public void setVacancyId(Long vacancyId) {
        this.vacancyId = vacancyId;
    }

    public String getVacancyCode() {
        return vacancyCode;
    }

    public void setVacancyCode(String vacancyCode) {
        this.vacancyCode = vacancyCode;
    }

    public String getVacancyCodeOrDefault() {
        if (vacancyCode != null && !vacancyCode.trim().isEmpty()) {
            return vacancyCode;
        }
        if (vacancyId != null) {
            return String.format("VAC-%03d", vacancyId);
        }
        return "VAC-000";
    }

    public Long getCandidateResumeId() {
        return candidateResumeId;
    }

    public void setCandidateResumeId(Long candidateResumeId) {
        this.candidateResumeId = candidateResumeId;
    }

    public String getCandidateCode() {
        return candidateCode;
    }

    public void setCandidateCode(String candidateCode) {
        this.candidateCode = candidateCode;
    }

    public String getCandidateCodeOrDefault() {
        if (candidateCode != null && !candidateCode.trim().isEmpty()) {
            return candidateCode;
        }
        if (candidateResumeId != null) {
            return String.format("CAN-%04d", candidateResumeId);
        }
        return "CAN-0000";
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }
}
