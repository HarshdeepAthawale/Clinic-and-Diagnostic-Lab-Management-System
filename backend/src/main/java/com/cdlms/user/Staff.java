package com.cdlms.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Receptionist, Lab Technician or Admin profile. {@code staffType} always equals the account's role. */
@Entity
@Table(name = "staff")
public class Staff {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "staff_type", nullable = false)
    private Role staffType;

    protected Staff() {
    }

    public Staff(UUID userId, String fullName, Role staffType) {
        if (!staffType.isStaff()) {
            throw new IllegalArgumentException("Not a staff role: " + staffType);
        }
        this.userId = userId;
        this.fullName = fullName;
        this.staffType = staffType;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getStaffType() {
        return staffType;
    }
}
