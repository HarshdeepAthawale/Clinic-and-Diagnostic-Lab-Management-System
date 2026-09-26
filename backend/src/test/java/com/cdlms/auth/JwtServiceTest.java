package com.cdlms.auth;

import com.cdlms.user.Role;
import com.cdlms.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-that-is-at-least-32-bytes-long";
    private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

    private static AuthProperties props(String secret) {
        return new AuthProperties(secret, Duration.ofHours(8), "cdlms_token", true);
    }

    private static JwtService at(Instant instant, String secret) {
        return new JwtService(props(secret), Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static User user(Role role) {
        User user = new User("someone@test.local", "hash", role);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    @Test
    void roundTripsUserIdAndRole() {
        User user = user(Role.PATHOLOGIST);
        String token = at(NOW, SECRET).issue(user);

        assertThat(at(NOW.plusSeconds(60), SECRET).verify(token))
                .hasValue(new JwtService.TokenClaims(user.getId(), Role.PATHOLOGIST));
    }

    @Test
    void rejectsExpiredToken() {
        String token = at(NOW, SECRET).issue(user(Role.DOCTOR));

        assertThat(at(NOW.plus(Duration.ofHours(8)).plusSeconds(1), SECRET).verify(token)).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String token = at(NOW, "a-completely-different-secret-of-32-bytes-plus").issue(user(Role.ADMIN));

        assertThat(at(NOW, SECRET).verify(token)).isEmpty();
    }

    @Test
    void rejectsGarbage() {
        assertThat(at(NOW, SECRET).verify("not.a.jwt")).isEmpty();
        assertThat(at(NOW, SECRET).verify("")).isEmpty();
    }

    @Test
    void refusesShortSecret() {
        assertThatThrownBy(() -> at(NOW, "too-short")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> at(NOW, null)).isInstanceOf(IllegalStateException.class);
    }
}
