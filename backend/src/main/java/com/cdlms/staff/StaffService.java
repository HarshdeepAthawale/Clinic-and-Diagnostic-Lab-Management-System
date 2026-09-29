package com.cdlms.staff;

import com.cdlms.auth.AuthUser;
import com.cdlms.common.ApiException;
import com.cdlms.staff.StaffDtos.CreateStaffRequest;
import com.cdlms.staff.StaffDtos.CreatedStaff;
import com.cdlms.staff.StaffDtos.StaffView;
import com.cdlms.user.Doctor;
import com.cdlms.user.DoctorRepository;
import com.cdlms.user.Pathologist;
import com.cdlms.user.PathologistRepository;
import com.cdlms.user.Role;
import com.cdlms.user.Staff;
import com.cdlms.user.StaffRepository;
import com.cdlms.user.User;
import com.cdlms.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

/**
 * Staff accounts (ADR-032): the admin creates them and switches them off. Patients are never touched here.
 *
 * <p>A new account gets a random temporary password, shown to the admin once and stored only as a hash; the
 * person changes it at first sign-in. Deactivating takes effect on the person's very next request (the sign-in
 * cookie is checked against the account every time), and accounts are never deleted, because everything they
 * did stays attributed to them.
 */
@Service
public class StaffService {

    /** No 0/O/1/l/I: it is read out or typed from a note. */
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int PASSWORD_LENGTH = 12;

    private final UserRepository users;
    private final DoctorRepository doctors;
    private final PathologistRepository pathologists;
    private final StaffRepository staff;
    private final PasswordEncoder passwordEncoder;
    private final NamedParameterJdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public StaffService(UserRepository users, DoctorRepository doctors, PathologistRepository pathologists,
                        StaffRepository staff, PasswordEncoder passwordEncoder, NamedParameterJdbcTemplate jdbc) {
        this.users = users;
        this.doctors = doctors;
        this.pathologists = pathologists;
        this.staff = staff;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    /** Everyone who works here, active first, then by role and name. Patients are not listed. */
    @Transactional(readOnly = true)
    public List<StaffView> list(String query, boolean includeInactive) {
        String q = query == null ? "" : query.trim().toLowerCase();
        return jdbc.query("""
                SELECT u.id, u.email, u.role, u.is_active, u.created_at,
                       COALESCE(d.full_name, pa.full_name, s.full_name, u.email) AS name,
                       COALESCE(d.specialization,
                                CASE WHEN pa.id IS NOT NULL THEN pa.qualification || ' · Reg. ' || pa.registration_number END) AS detail
                FROM users u
                LEFT JOIN doctors d ON d.user_id = u.id
                LEFT JOIN pathologists pa ON pa.user_id = u.id
                LEFT JOIN staff s ON s.user_id = u.id
                WHERE u.role <> 'PATIENT' AND (:all OR u.is_active)
                  AND (:q = '' OR lower(COALESCE(d.full_name, pa.full_name, s.full_name, '')) LIKE '%' || :q || '%'
                       OR lower(u.email) LIKE '%' || :q || '%')
                ORDER BY u.is_active DESC, u.role, name
                """, new MapSqlParameterSource("all", includeInactive).addValue("q", q),
                (rs, i) -> new StaffView(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("email"),
                        Role.valueOf(rs.getString("role")), rs.getBoolean("is_active"), rs.getString("detail"),
                        rs.getTimestamp("created_at").toInstant()));
    }

    @Transactional
    public CreatedStaff create(CreateStaffRequest request) {
        Role role = request.role();
        if (role == Role.PATIENT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Patients register themselves or at the front desk");
        }
        String name = request.fullName().trim();
        switch (role) {
            case DOCTOR -> require(request.specialization(), "A doctor needs a specialization");
            case PATHOLOGIST -> {
                require(request.qualification(), "A pathologist needs a qualification");
                require(request.registrationNumber(), "A pathologist needs a registration number");
            }
            default -> {
            }
        }
        String email = User.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "An account with this email already exists");
        }
        String registration = role == Role.PATHOLOGIST ? request.registrationNumber().trim() : null;
        if (registration != null && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pathologists WHERE registration_number = :r)",
                new MapSqlParameterSource("r", registration), Boolean.class))) {
            throw new ApiException(HttpStatus.CONFLICT, "REGISTRATION_TAKEN", "A pathologist with that registration number already exists");
        }

        String password = temporaryPassword();
        User user = users.saveAndFlush(new User(email, passwordEncoder.encode(password), role));
        switch (role) {
            case DOCTOR -> doctors.saveAndFlush(new Doctor(user.getId(), name, request.specialization().trim()));
            case PATHOLOGIST -> pathologists.saveAndFlush(new Pathologist(user.getId(), name, request.qualification().trim(), registration));
            default -> staff.saveAndFlush(new Staff(user.getId(), name, role));
        }
        return new CreatedStaff(viewOf(user), password);
    }

    /** Switches an account on or off. Nobody can switch themselves off, so the clinic is never left without an admin. */
    @Transactional
    public StaffView setActive(AuthUser admin, UUID userId, boolean active) {
        User user = users.findById(userId).filter(u -> u.getRole() != Role.PATIENT)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Staff account not found"));
        if (!active && user.getId().equals(admin.id())) {
            throw new ApiException(HttpStatus.CONFLICT, "CANNOT_DEACTIVATE_SELF", "You can't deactivate your own account");
        }
        user.setActive(active);
        users.saveAndFlush(user);
        return viewOf(user);
    }

    private StaffView viewOf(User user) {
        return list(user.getEmail(), true).stream().filter(s -> s.userId().equals(user.getId())).findFirst().orElseThrow();
    }

    private String temporaryPassword() {
        StringBuilder out = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            out.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return out.toString();
    }

    private static void require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
        }
    }
}
