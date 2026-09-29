package com.cdlms.lab;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Numeric parameters are checked against their ranges when a result is entered; text parameters
 * (Positive / Negative, blood group) are recorded as typed.
 */
@Entity
@Table(name = "lab_test_parameters")
public class LabTestParameter {

    public record Range(String name, String unit, BigDecimal refLow, BigDecimal refHigh,
                        BigDecimal criticalLow, BigDecimal criticalHigh, ValueType valueType) {
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

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false)
    private ValueType valueType = ValueType.TEXT;

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
        this.valueType = range.valueType() != null ? range.valueType()
                : ValueType.inferred(range.refLow(), range.refHigh(), range.criticalLow(), range.criticalHigh());
    }

    public Range range() {
        return new Range(name, unit, refLow, refHigh, criticalLow, criticalHigh, valueType);
    }

    public UUID getId() {
        return id;
    }

    public UUID getLabTestId() {
        return labTest.getId();
    }

    public int getPosition() {
        return position;
    }

    public String getName() {
        return name;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getRefLow() {
        return refLow;
    }

    public BigDecimal getRefHigh() {
        return refHigh;
    }

    public BigDecimal getCriticalLow() {
        return criticalLow;
    }

    public BigDecimal getCriticalHigh() {
        return criticalHigh;
    }

    public ValueType getValueType() {
        return valueType;
    }
}
