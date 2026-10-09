package com.example.resumescreener.repository;

import com.example.resumescreener.entity.SemifinalizedCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SemifinalizedCandidateRepository extends JpaRepository<SemifinalizedCandidate, Long> {
    List<SemifinalizedCandidate> findAllByVacancyIdOrderByScoreDesc(Long vacancyId);
}
