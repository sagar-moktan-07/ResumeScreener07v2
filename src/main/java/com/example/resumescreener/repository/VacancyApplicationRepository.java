package com.example.resumescreener.repository;

import com.example.resumescreener.entity.VacancyApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VacancyApplicationRepository extends JpaRepository<VacancyApplication, Long> {
    List<VacancyApplication> findByVacancyIdOrderByAppliedAtDesc(Long vacancyId);
    List<VacancyApplication> findByUserIdOrderByAppliedAtDesc(Long userId);
    List<VacancyApplication> findAllByOrderByAppliedAtDesc();
    Optional<VacancyApplication> findByUserIdAndVacancyId(Long userId, Long vacancyId);
    boolean existsByUserIdAndVacancyId(Long userId, Long vacancyId);
    long countByUserId(Long userId);
    List<VacancyApplication> findAllByVacancyIdOrderByAppliedAtDesc(Long vacancyId);
}
