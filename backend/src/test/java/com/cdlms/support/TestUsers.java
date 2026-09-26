package com.cdlms.support;

import com.cdlms.auth.AuthCookies;
import com.cdlms.auth.JwtService;
import com.cdlms.patient.Gender;
import com.cdlms.patient.Patient;
import com.cdlms.patient.PatientRepository;
import com.cdlms.user.Doctor;
import com.cdlms.user.DoctorRepository;
import com.cdlms.user.Pathologist;
import com.cdlms.user.PathologistRepository;
import com.cdlms.user.Role;
import com.cdlms.user.Staff;
import com.cdlms.user.StaffRepository;
import com.cdlms.user.User;
import com.cdlms.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

/** Creates accounts with their profile row, and login cookies for them, directly (no HTTP). */
@TestComponent
public class TestUsers {

    public static final String PASSWORD = "correct-horse-battery";

    private final UserRepository users;
    private final PatientRepository patients;
    private final DoctorRepository doctors;
    private final PathologistRepository pathologists;
    private final StaffRepository staff;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthCookies cookies;

    public TestUsers(UserRepository users, PatientRepository patients, DoctorRepository doctors,
                     PathologistRepository pathologists, StaffRepository staff, PasswordEncoder passwordEncoder,
                     JwtService jwtService, AuthCookies cookies) {
        this.users = users;
        this.patients = patients;
        this.doctors = doctors;
        this.pathologists = pathologists;
        this.staff = staff;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.cookies = cookies;
    }

    public User create(Role role) {
        return create(role, role.name().toLowerCase() + "@test.local");
    }

    public User create(Role role, String email) {
        User user = users.save(new User(email, passwordEncoder.encode(PASSWORD), role));
        String name = "Test " + role.name();
        switch (role) {
            case PATIENT -> patients.save(new Patient(user.getId(), name, LocalDate.of(1990, 1, 1), Gender.OTHER, "+911234567890"));
            case DOCTOR -> doctors.save(new Doctor(user.getId(), name, "General Medicine"));
            case PATHOLOGIST -> pathologists.save(new Pathologist(user.getId(), name, "MD Pathology", "REG-" + user.getId()));
            case RECEPTIONIST, LAB_TECHNICIAN, ADMIN -> staff.save(new Staff(user.getId(), name, role));
        }
        return user;
    }

    public Cookie loginCookie(User user) {
        return new Cookie(cookies.name(), jwtService.issue(user));
    }

    public User deactivate(User user) {
        user.setActive(false);
        return users.save(user);
    }
}
