package com.cdlms.auth;

import com.cdlms.patient.Gender;
import com.cdlms.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/** Request/response bodies for {@code /api/auth}. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    /** Patient self-registration. Staff accounts are created by Admin, never self-registered. */
    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            // BCrypt only uses the first 72 bytes, so longer passwords would be silently truncated.
            @NotBlank @Size(min = 8, max = 72, message = "must be 8 to 72 characters") String password,
            @NotBlank @Size(max = 200) String fullName,
            @NotNull @Past LocalDate dob,
            @NotNull Gender gender,
            @NotBlank @Pattern(regexp = "^\\+?[0-9 ()-]{7,20}$", message = "must be a valid phone number") String phone) {
    }

    /** The logged-in user, as returned by login, register and {@code GET /api/auth/me}. */
    public record MeResponse(UUID id, String email, Role role, String name) {
    }
}
