package com.cdlms.result;

import com.cdlms.lab.ValueType;
import com.cdlms.result.RangeCheck.Flag;
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
 * One value of one attempt — a parameter of a test on the sample. The name, unit and ranges are copied
 * when it is entered so the flag always makes sense later, and the row can never be changed (V9 trigger).
 */
@Entity
@Table(name = "result_values")
public class ResultValue {

    /** What the technician typed for one parameter, with the parameter's ranges at that moment. */
    public record Entry(UUID labOrderItemId, UUID parameterId, int position, String name, String unit, ValueType type,
                        BigDecimal numeric, String text, BigDecimal refLow, BigDecimal refHigh,
                        BigDecimal criticalLow, BigDecimal criticalHigh, Flag flag) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sample_result_id", nullable = false, updatable = false)
    private SampleResult sampleResult;

    @Column(name = "lab_order_item_id", nullable = false, updatable = false)
    private UUID labOrderItemId;

    @Column(name = "parameter_id", nullable = false, updatable = false)
    private UUID parameterId;

    @Column(nullable = false, updatable = false)
    private short position;

    @Column(name = "parameter_name", nullable = false, updatable = false)
    private String parameterName;

    @Column(updatable = false)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, updatable = false)
    private ValueType valueType;

    @Column(name = "numeric_value", updatable = false, precision = 14, scale = 4)
    private BigDecimal numericValue;

    @Column(name = "text_value", updatable = false)
    private String textValue;

    @Column(name = "ref_low", updatable = false, precision = 12, scale = 3)
    private BigDecimal refLow;

    @Column(name = "ref_high", updatable = false, precision = 12, scale = 3)
    private BigDecimal refHigh;

    @Column(name = "critical_low", updatable = false, precision = 12, scale = 3)
    private BigDecimal criticalLow;

    @Column(name = "critical_high", updatable = false, precision = 12, scale = 3)
    private BigDecimal criticalHigh;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private Flag flag;

    protected ResultValue() {
    }

    ResultValue(SampleResult sampleResult, Entry e) {
        this.sampleResult = sampleResult;
        this.labOrderItemId = e.labOrderItemId();
        this.parameterId = e.parameterId();
        this.position = (short) e.position();
        this.parameterName = e.name();
        this.unit = e.unit();
        this.valueType = e.type();
        this.numericValue = e.numeric();
        this.textValue = e.text();
        this.refLow = e.refLow();
        this.refHigh = e.refHigh();
        this.criticalLow = e.criticalLow();
        this.criticalHigh = e.criticalHigh();
        this.flag = e.flag();
    }

    public UUID getLabOrderItemId() {
        return labOrderItemId;
    }

    public UUID getParameterId() {
        return parameterId;
    }

    public int getPosition() {
        return position;
    }

    public String getParameterName() {
        return parameterName;
    }

    public String getUnit() {
        return unit;
    }

    public ValueType getValueType() {
        return valueType;
    }

    public BigDecimal getNumericValue() {
        return numericValue;
    }

    public String getTextValue() {
        return textValue;
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

    public Flag getFlag() {
        return flag;
    }
}
