package com.example.demo.entity;

import com.example.demo.common.BaseEntity;
import com.example.demo.enums.ScannerTranslationSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "scan_detections")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ScanDetection extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_session_id", nullable = false)
    private ScanSession scanSession;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private double confidence;

    @Column(name = "native_word", nullable = false)
    private String nativeWord;

    @Column(name = "learning_word", nullable = false)
    private String learningWord;

    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "translation_source")
    private ScannerTranslationSource translationSource;

    @Column(name = "box_x")
    private Double boxX;

    @Column(name = "box_y")
    private Double boxY;

    @Column(name = "box_width")
    private Double boxWidth;

    @Column(name = "box_height")
    private Double boxHeight;
}
