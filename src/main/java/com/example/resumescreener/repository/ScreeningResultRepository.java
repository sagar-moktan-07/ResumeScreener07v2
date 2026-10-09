package com.example.resumescreener.repository;

import com.example.resumescreener.entity.ScreeningResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScreeningResultRepository extends JpaRepository<ScreeningResult, Long> {
    List<ScreeningResult> findAllByVacancyIdOrderByOverallScoreDesc(Long vacancyId);
}
