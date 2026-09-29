package com.cdlms.auth;

import com.cdlms.auth.AuthDtos.ClaimAccountRequest;
import com.cdlms.auth.AuthDtos.LoginRequest;
import com.cdlms.auth.AuthDtos.MeResponse;
import com.cdlms.auth.AuthDtos.RegisterRequest;
import com.cdlms.common.ApiException;
import com.cdlms.patient.ClaimCodes;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PatientRepository patients;
    private final DoctorRepository doctors;
    private final PathologistRepository pathologists;
    private final StaffRepository staff;
    private final PasswordEncoder passwordEncoder;
    private final ClaimCodes claimCodes;
    /** Compared against when the email is unknown, so both failure paths take the same time. */
    private final String dummyHash;

    public AuthService(UserRepository users, PatientRepository patients, DoctorRepository doctors,
                       PathologistRepository pathologists, StaffRepository staff, PasswordEncoder passwordEncoder,
                       ClaimCodes claimCodes) {
        this.users = users;
        this.patients = patients;
        this.doctors = doctors;
        this.pathologists = pathologists;
        this.staff = staff;
        this.passwordEncoder = passwordEncoder;
        this.claimCodes = claimCodes;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
    }

    @Transactional
    public User registerPatient(RegisterRequest request) {
        String email = User.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "An account with this email already exists");
        }
        User user = users.save(new User(email, passwordEncoder.encode(request.password()), Role.PATIENT));
        patients.save(new Patient(user.getId(), request.fullName().trim(), request.dob(), request.gender(),
                request.phone().trim()));
        return user;
    }

    /**
     * Links a new login to a record the front desk registered (ADR-018). Unknown, expired and
     * already-used codes all get the same error, so codes can't be probed.
     */
    @Transactional
    public User claimAccount(ClaimAccountRequest request) {
        ApiException invalid = new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REGISTRATION_CODE",
                "That registration code isn't valid. Ask the front desk for a new one.");
        Patient patient = patients.findByClaimCodeHash(claimCodes.hash(request.registrationCode()))
                .filter(p -> p.getUserId() == null)
                .filter(p -> p.getClaimCodeExpiresAt() != null && p.getClaimCodeExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> invalid);

        String email = User.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "An account with this email already exists");
        }
        User user = users.save(new User(email, passwordEncoder.encode(request.password()), Role.PATIENT));
        patient.linkToUser(user.getId());
        patients.save(patient);
        return user;
    }

    /** Changes the signed-in user's own password. The current one must be right, and the new one must differ. */
    @Transactional
    public void changePassword(AuthUser caller, AuthDtos.ChangePasswordRequest request) {
        User user = users.findById(caller.id()).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Please sign in again"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Your current password isn't right");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Choose a password you haven't been using");
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        users.save(user);
    }

    @Transactional(readOnly = true)
    public User login(LoginRequest request) {
        Optional<User> found = users.findByEmail(User.normalizeEmail(request.email()));
        String hash = found.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (found.isEmpty() || !matches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect");
        }
        User user = found.get();
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "This account has been deactivated");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public MeResponse me(AuthUser authUser) {
        User user = users.findById(authUser.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required"));
        return toMe(user);
    }

    @Transactional(readOnly = true)
    public MeResponse toMe(User user) {
        return new MeResponse(user.getId(), user.getEmail(), user.getRole(), displayName(user));
    }

    private String displayName(User user) {
        Optional<String> name = switch (user.getRole()) {
            case PATIENT -> patients.findByUserId(user.getId()).map(Patient::getFullName);
            case DOCTOR -> doctors.findByUserId(user.getId()).map(Doctor::getFullName);
            case PATHOLOGIST -> pathologists.findByUserId(user.getId()).map(Pathologist::getFullName);
            case RECEPTIONIST, LAB_TECHNICIAN, ADMIN -> staff.findByUserId(user.getId()).map(Staff::getFullName);
        };
        return name.orElse(user.getEmail());
    }
}
