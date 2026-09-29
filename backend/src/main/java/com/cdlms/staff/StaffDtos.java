package com.cdlms.staff;

import com.cdlms.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** Request/response bodies for staff accounts (Docs/API.md "Admin", ADR-032). */
public final class StaffDtos {

    private StaffDtos() {
    }

    /**
     * Create a staff account. Doctors need a specialization; pathologists need a qualification and a registration
     * number (both are printed on what they sign). {@code role} can be any role except PATIENT.
     */
    public record CreateStaffRequest(
            @NotNull Role role,
            @NotBlank @Size(max = 200) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @Size(max = 120) String specialization,
            @Size(max = 120) String qualification,
            @Size(max = 60) String registrationNumber) {
    }

    public record SetActiveRequest(@NotNull Boolean active) {
    }

    /** One staff account. {@code detail} is the specialization, or the qualification and registration number. */
    public record StaffView(UUID userId, String fullName, String email, Role role, boolean active, String detail,
                            Instant createdAt) {
    }

    /** The new account and its one-time temporary password, shown once and never stored in the clear. */
    public record CreatedStaff(StaffView staff, String temporaryPassword) {
    }
}
