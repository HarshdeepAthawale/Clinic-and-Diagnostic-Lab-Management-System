package com.cdlms.lab;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One thing a test measures, e.g. Haemoglobin in g/dL, normal 12.0–15.5, critical below 7.0.
 * Qualitative parameters (Positive / Negative) have no numeric ranges.
 */
@Entity
@Table(name = "lab_test_parameters")
public class LabTestParameter {

    public record Range(String name, String unit, BigDecimal refLow, BigDecimal refHigh,
                        BigDecimal criticalLow, BigDecimal criticalHigh) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lab_test_id", nullable = false, updatable = false)
    private LabTest labTest;

    @Column(nullable = false)
    private short position;

    @Column(nullable = false)
    private String name;

    private String unit;

    @Column(name = "ref_low", precision = 12, scale = 3)
    private BigDecimal refLow;

    @Column(name = "ref_high", precision = 12, scale = 3)
    private BigDecimal refHigh;

    @Column(name = "critical_low", precision = 12, scale = 3)
    private BigDecimal criticalLow;

    @Column(name = "critical_high", precision = 12, scale = 3)
    private BigDecimal criticalHigh;

    protected LabTestParameter() {
    }

    LabTestParameter(LabTest labTest, int position, Range range) {
        this.labTest = labTest;
        this.position = (short) position;
        this.name = range.name();
        this.unit = range.unit();
        this.refLow = range.refLow();
        this.refHigh = range.refHigh();
        this.criticalLow = range.criticalLow();
        this.criticalHigh = range.criticalHigh();
    }

    public Range range() {
        return new Range(name, unit, refLow, refHigh, criticalLow, criticalHigh);
    }

    public int getPosition() {
        return position;
    }
}
