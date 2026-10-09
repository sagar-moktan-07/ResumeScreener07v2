package com.example.resumescreener.repository;

import com.example.resumescreener.entity.VacancyPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VacancyPostRepository extends JpaRepository<VacancyPost, Long> {
    List<VacancyPost> findAllByOrderByIdDesc();
}
