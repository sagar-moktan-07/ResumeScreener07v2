package com.example.resumescreener.repository;

import com.example.resumescreener.entity.FinalizedCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FinalizedCandidateRepository extends JpaRepository<FinalizedCandidate, Long> {
    List<FinalizedCandidate> findAllByOrderByIdDesc();
    List<FinalizedCandidate> findAllByVacancyIdOrderByFinalScoreDesc(Long vacancyId);
}
