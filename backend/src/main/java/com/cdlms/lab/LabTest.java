package com.cdlms.lab;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A test the lab offers (Docs/Schema.md §3): its price, the tube it needs, how long it takes, what the
 * patient must do beforehand, and the parameters it reports with their reference ranges. Tests are
 * deactivated rather than deleted, since orders keep pointing at them.
 */
@Entity
@Table(name = "lab_tests")
public class LabTest {

    /** The editable fields of a test, applied as a whole. */
    public record Details(String name, String category, SampleType sampleType, TubeType requiredTubeType,
                          BigDecimal price, short turnaroundHours, String prepInstructions, boolean active) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "sample_type", nullable = false)
    private SampleType sampleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_tube_type", nullable = false)
    private TubeType requiredTubeType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "turnaround_hours", nullable = false)
    private short turnaroundHours;

    @Column(name = "prep_instructions")
    private String prepInstructions;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "labTest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<LabTestParameter> parameters = new ArrayList<>();

    protected LabTest() {
    }

    public LabTest(String code, Details details) {
        this.code = code;
        apply(details);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void apply(Details d) {
        name = d.name();
        category = d.category();
        sampleType = d.sampleType();
        requiredTubeType = d.requiredTubeType();
        price = d.price();
        turnaroundHours = d.turnaroundHours();
        prepInstructions = d.prepInstructions();
        active = d.active();
    }

    public void addParameter(LabTestParameter.Range range) {
        parameters.add(new LabTestParameter(this, parameters.size() + 1, range));
    }

    public void clearParameters() {
        parameters.clear();
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public SampleType getSampleType() {
        return sampleType;
    }

    public TubeType getRequiredTubeType() {
        return requiredTubeType;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public short getTurnaroundHours() {
        return turnaroundHours;
    }

    public String getPrepInstructions() {
        return prepInstructions;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<LabTestParameter> getParameters() {
        return parameters;
    }
}
