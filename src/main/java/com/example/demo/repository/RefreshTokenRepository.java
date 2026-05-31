package com.example.demo.repository;

import com.example.demo.entity.Profile;
import com.example.demo.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    java.util.Optional<RefreshToken> findByToken(String token);

    java.util.Optional<RefreshToken> findByProfile(Profile profile);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    void deleteByProfile(Profile profile);
}
