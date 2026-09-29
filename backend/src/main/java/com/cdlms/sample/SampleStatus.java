package com.cdlms.sample;

import java.util.EnumSet;
import java.util.Set;

/**
 * Where a sample is in its journey (Docs/Rules.md §2, Appflow.md §3). Moves only go forward, with
 * two exceptions: {@code REJECTED} (a new sample replaces this one) and return for retest
 * ({@code RESULT_ENTERED → IN_TESTING}, same sample). The database enforces the same table.
 */
public enum SampleStatus {
    ORDERED,
    COLLECTED,
    RECEIVED_AT_LAB,
    IN_TESTING,
    RESULT_ENTERED,
    VERIFIED,
    REPORT_GENERATED,
    DISPATCHED,
    REJECTED,
    /** Every test on the sample was taken off the order before anything was drawn. */
    CANCELLED;

    public Set<SampleStatus> next() {
        return switch (this) {
            case ORDERED -> EnumSet.of(COLLECTED, CANCELLED);
            case COLLECTED -> EnumSet.of(RECEIVED_AT_LAB, REJECTED);
            case RECEIVED_AT_LAB -> EnumSet.of(IN_TESTING);
            case IN_TESTING -> EnumSet.of(RESULT_ENTERED, REJECTED);
            case RESULT_ENTERED -> EnumSet.of(VERIFIED, IN_TESTING);
            case VERIFIED -> EnumSet.of(REPORT_GENERATED);
            case REPORT_GENERATED -> EnumSet.of(DISPATCHED);
            case REJECTED, CANCELLED, DISPATCHED -> EnumSet.noneOf(SampleStatus.class);
        };
    }

    public boolean canMoveTo(SampleStatus target) {
        return next().contains(target);
    }

    /** A sample that will still be processed: not rejected, not cancelled. */
    public boolean isLive() {
        return this != REJECTED && this != CANCELLED;
    }
}
