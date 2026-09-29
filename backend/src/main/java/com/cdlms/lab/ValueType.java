package com.cdlms.lab;

import java.math.BigDecimal;

/** How a test parameter's result is entered: a number (checked against its ranges) or free text (Positive, O+, 1:160). */
public enum ValueType {
    NUMERIC, TEXT;

    /**
     * The type for a parameter whose editor did not choose one: a number if it has a normal range or
     * critical limits to check against, otherwise text.
     */
    public static ValueType inferred(BigDecimal refLow, BigDecimal refHigh, BigDecimal criticalLow, BigDecimal criticalHigh) {
        return refLow != null || refHigh != null || criticalLow != null || criticalHigh != null ? NUMERIC : TEXT;
    }
}
