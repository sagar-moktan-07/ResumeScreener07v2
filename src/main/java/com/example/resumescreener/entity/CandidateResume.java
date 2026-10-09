package com.example.resumescreener.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "candidate_resume")
public class CandidateResume {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "full_name")
    private String fullName;

    private String email;

    private String phone;

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @Column(columnDefinition = "TEXT")
    private String skills;

    @Column(columnDefinition = "TEXT")
    private String qualifications;

    @Column(columnDefinition = "TEXT")
    private String experience;

    private String status = "uploaded";

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "candidate_code", unique = true)
    private String candidateCode;

    @PrePersist
    @PreUpdate
    private void ensureCode() {
        if (candidateCode == null || candidateCode.trim().isEmpty()) {
            candidateCode = "CAN-" + UserCredentials.generateRandomCode();
        }
    }

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @PrePersist
    @PreUpdate
    private void ensureCcode() {
        if (candidateCode == null || candidateCode.trim().isEmpty()) {
            Long sourceId = userId != null ? userId : id;
            if (sourceId != null) {
                candidateCode = String.format("CAN-%04d", Math.abs(sourceId));
            } else {
                candidateCode = "CAN-0000";
            }
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getSkills() {
        return skills;
    }

    public void setSkills(String skills) {
        this.skills = skills;
    }

    public String getQualifications() {
        return qualifications;
    }

    public void setQualifications(String qualifications) {
        this.qualifications = qualifications;
    }

    public String getExperience() {
        return experience;
    }

    public void setExperience(String experience) {
        this.experience = experience;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getCandidateCode() {
        return candidateCode;
    }

    public void setCandidateCode(String candidateCode) {
        this.candidateCode = candidateCode;
    }

    public String getDisplayCode() {
        if (candidateCode != null && !candidateCode.trim().isEmpty()) {
            return candidateCode;
        }
        Long sourceId = userId != null ? userId : id;
        if (sourceId != null) {
            return String.format("CAN-%04d", Math.abs(sourceId));
        }
        return "CAN-0000";
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
