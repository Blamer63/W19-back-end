package com.example.demo.entity;

import com.example.demo.common.BaseEntity;
import com.example.demo.enums.ScanSessionStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "scan_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ScanSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Profile user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ScanSessionStatus status = ScanSessionStatus.COMPLETED;

    @OneToMany(mappedBy = "scanSession", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("confidence DESC")
    @Builder.Default
    private List<ScanDetection> detections = new ArrayList<>();

    public void addDetection(ScanDetection detection) {
        detections.add(detection);
        detection.setScanSession(this);
    }
}
