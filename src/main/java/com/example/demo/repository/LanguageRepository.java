package com.example.demo.repository;

import com.example.demo.entity.Language;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LanguageRepository extends JpaRepository<Language, String> {
    java.util.Optional<Language> findByCode(String code);
}
