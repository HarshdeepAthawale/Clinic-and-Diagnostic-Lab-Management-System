package com.cdlms.user;

/** System roles. One per account (ADR-011). Stored as the enum name in {@code users.role}. */
public enum Role {
    PATIENT,
    DOCTOR,
    PATHOLOGIST,
    RECEPTIONIST,
    LAB_TECHNICIAN,
    ADMIN;

    /** Spring Security authority name, e.g. {@code ROLE_DOCTOR}. */
    public String authority() {
        return "ROLE_" + name();
    }

    public boolean isStaff() {
        return this == RECEPTIONIST || this == LAB_TECHNICIAN || this == ADMIN;
    }
}
