package com.cdlms.result;

import java.math.BigDecimal;

/**
 * The automatic reference-range check for a numeric result (Docs/Rules.md §2). Critical limits win over
 * the normal range; a value sitting exactly on a critical limit is critical. Missing limits are simply
 * not checked, so a parameter with only an upper limit never flags LOW.
 */
public final class RangeCheck {

    public enum Flag { NORMAL, LOW, HIGH, CRITICAL_LOW, CRITICAL_HIGH }

    private RangeCheck() {
    }

    public static Flag flag(BigDecimal value, BigDecimal refLow, BigDecimal refHigh, BigDecimal criticalLow,
                            BigDecimal criticalHigh) {
        if (criticalLow != null && value.compareTo(criticalLow) <= 0) {
            return Flag.CRITICAL_LOW;
        }
        if (criticalHigh != null && value.compareTo(criticalHigh) >= 0) {
            return Flag.CRITICAL_HIGH;
        }
        if (refLow != null && value.compareTo(refLow) < 0) {
            return Flag.LOW;
        }
        if (refHigh != null && value.compareTo(refHigh) > 0) {
            return Flag.HIGH;
        }
        return Flag.NORMAL;
    }

    public static boolean isCritical(Flag flag) {
        return flag == Flag.CRITICAL_LOW || flag == Flag.CRITICAL_HIGH;
    }

    public static boolean isAbnormal(Flag flag) {
        return flag != null && flag != Flag.NORMAL;
    }
}
