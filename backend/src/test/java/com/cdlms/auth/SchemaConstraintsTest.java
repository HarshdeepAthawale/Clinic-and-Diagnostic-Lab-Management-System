package com.cdlms.auth;

import com.cdlms.support.IntegrationTest;
import com.cdlms.user.Role;
import com.cdlms.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The V1 migration's database-level guarantees hold even if application code gets them wrong. */
class SchemaConstraintsTest extends IntegrationTest {

    @Test
    void profileCannotPointAtAccountOfAnotherRole() {
        User patient = testUsers.create(Role.PATIENT);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO doctors (user_id, full_name, specialization) VALUES (?, 'X', 'Y')", patient.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO staff (user_id, full_name, staff_type) VALUES (?, 'X', 'ADMIN')", patient.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void emailIsUniqueIgnoringCase() {
        testUsers.create(Role.PATIENT, "same@test.local");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO users (email, password_hash, role) VALUES ('SAME@test.local', 'x', 'PATIENT')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknownRoleIsRejected() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO users (email, password_hash, role) VALUES ('x@test.local', 'x', 'SUPERUSER')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
