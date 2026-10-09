package com.example.resumescreener.repository;

import com.example.resumescreener.entity.UserCredentials;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserCredentialsRepository extends JpaRepository<UserCredentials, Long> {
    Optional<UserCredentials> findByUsername(String username);
    Optional<UserCredentials> findByEmail(String email);
    Optional<UserCredentials> findByCandidateCode(String candidateCode);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByCandidateCode(String candidateCode);
    List<UserCredentials> findAllByOrderByIdDesc();
}
