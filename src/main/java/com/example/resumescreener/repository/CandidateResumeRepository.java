package com.example.resumescreener.repository;

import com.example.resumescreener.entity.CandidateResume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CandidateResumeRepository extends JpaRepository<CandidateResume, Long> {
    List<CandidateResume> findAllByOrderByIdDesc();
    List<CandidateResume> findByUserIdOrderByIdDesc(Long userId);
    Optional<CandidateResume> findFirstByUserIdOrderByIdDesc(Long userId);
    boolean existsByUserId(Long userId);
}
