package com.cdlms.consultation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** One medicine line of a prescription. Immutable, like its prescription. */
@Entity
@Table(name = "prescription_items")
public class PrescriptionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private short position;

    @Column(nullable = false, updatable = false)
    private String medicine;

    @Column(updatable = false)
    private String dose;

    @Column(nullable = false, updatable = false)
    private String frequency;

    @Column(name = "duration_days", updatable = false)
    private Short durationDays;

    @Column(updatable = false)
    private String instructions;

    protected PrescriptionItem() {
    }

    public PrescriptionItem(int position, String medicine, String dose, String frequency, Integer durationDays,
                            String instructions) {
        this.position = (short) position;
        this.medicine = medicine;
        this.dose = dose;
        this.frequency = frequency;
        this.durationDays = durationDays == null ? null : durationDays.shortValue();
        this.instructions = instructions;
    }
}
