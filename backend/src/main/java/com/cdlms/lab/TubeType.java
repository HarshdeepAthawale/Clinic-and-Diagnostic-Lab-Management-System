package com.cdlms.lab;

/**
 * The container a sample must be collected in (Docs/Rules.md §2). Blood tubes are named after their
 * additive / cap colour: EDTA lavender, PLAIN red, SST gold, CITRATE light blue, FLUORIDE grey,
 * HEPARIN green.
 */
public enum TubeType {
    EDTA, PLAIN, SST, CITRATE, FLUORIDE, HEPARIN, URINE_CUP, STOOL_CUP, SWAB_TUBE;

    /** Blood tubes are filled from a vein, so collection records the body site. */
    public boolean isBlood() {
        return this != URINE_CUP && this != STOOL_CUP && this != SWAB_TUBE;
    }
}
