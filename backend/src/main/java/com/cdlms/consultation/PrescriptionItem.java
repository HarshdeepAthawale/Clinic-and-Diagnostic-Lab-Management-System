package com.cdlms.consultation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/** One medicine line: what, how much, how often, for how long, and any instructions. */
@Entity
@Table(name = "prescription_items")
public class PrescriptionItem {

    /** The fields a doctor fills in for one line, e.g. Paracetamol · 500 mg · 1-0-1 · 5 days · after food. */
    public record Line(String medicine, String dosage, String frequency, String duration, String instructions) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescription_id", nullable = false, updatable = false)
    private Prescription prescription;

    @Column(nullable = false)
    private short position;

    @Column(nullable = false)
    private String medicine;

    private String dosage;

    @Column(nullable = false)
    private String frequency;

    @Column(nullable = false)
    private String duration;

    private String instructions;

    protected PrescriptionItem() {
    }

    PrescriptionItem(Prescription prescription, int position, Line line) {
        this.prescription = prescription;
        this.position = (short) position;
        this.medicine = line.medicine();
        this.dosage = line.dosage();
        this.frequency = line.frequency();
        this.duration = line.duration();
        this.instructions = line.instructions();
    }

    public Line line() {
        return new Line(medicine, dosage, frequency, duration, instructions);
    }

    public int getPosition() {
        return position;
    }
}
