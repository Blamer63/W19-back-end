package com.example.demo.entity;

import com.example.demo.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "scanner_translation_cache", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "label", "language_code" })
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ScannerTranslationCache extends BaseEntity {

    @Column(nullable = false)
    private String label;

    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;

    @Column(name = "translated_text", nullable = false)
    private String translatedText;
}
