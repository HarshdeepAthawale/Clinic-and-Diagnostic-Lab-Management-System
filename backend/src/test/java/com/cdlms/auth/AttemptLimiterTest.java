package com.cdlms.auth;

import com.cdlms.auth.AttemptLimiter.Kind;
import com.cdlms.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Guessing passwords and registration codes gets slower and then stops (Docs/Security.md §2, ADR-030). */
class AttemptLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    /** 3 failures per email+address, 9 per address (so 3 per address for claims), 15-minute window. */
    private final AttemptLimiter limiter = new AttemptLimiter(3, 9, Duration.ofMinutes(15), now::get);

    private void fail(Kind kind, String address, String email, int times) {
        for (int i = 0; i < times; i++) {
            limiter.failed(kind, address, email);
        }
    }

    private void passes(Kind kind, String address, String email) {
        assertThatCode(() -> limiter.check(kind, address, email)).doesNotThrowAnyException();
    }

    private void blocked(Kind kind, String address, String email) {
        assertThatThrownBy(() -> limiter.check(kind, address, email))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(e.getCode()).isEqualTo("TOO_MANY_ATTEMPTS");
                });
    }

    @Test
    void anEmailIsBlockedFromAnAddressAfterTooManyFailures() {
        fail(Kind.LOGIN, "10.0.0.1", "ana@example.com", 2);
        passes(Kind.LOGIN, "10.0.0.1", "ana@example.com");
        fail(Kind.LOGIN, "10.0.0.1", "ana@example.com", 1);
        blocked(Kind.LOGIN, "10.0.0.1", "ana@example.com");
    }

    @Test
    void aStrangerCannotLockSomeoneOutFromTheirOwnAddress() {
        fail(Kind.LOGIN, "10.0.0.66", "ana@example.com", 3);
        blocked(Kind.LOGIN, "10.0.0.66", "ana@example.com");
        // Ana herself, from her own address, is unaffected.
        passes(Kind.LOGIN, "10.0.0.1", "ana@example.com");
        // And the attacker's address can still try another account (until the address cap).
        passes(Kind.LOGIN, "10.0.0.66", "ben@example.com");
    }

    @Test
    void theEmailIsMatchedIgnoringCaseAndSpaces() {
        fail(Kind.LOGIN, "10.0.0.1", "Ana@Example.com", 2);
        fail(Kind.LOGIN, "10.0.0.1", "  ana@example.COM ", 1);
        blocked(Kind.LOGIN, "10.0.0.1", "ana@example.com");
    }

    @Test
    void oneAddressTryingManyEmailsIsStoppedToo() {
        for (int i = 0; i < 9; i++) {
            fail(Kind.LOGIN, "10.0.0.9", "user" + i + "@example.com", 1);
        }
        blocked(Kind.LOGIN, "10.0.0.9", "someone.new@example.com");
        passes(Kind.LOGIN, "10.0.0.10", "someone.new@example.com");
    }

    @Test
    void aCorrectSignInClearsThatEmailsCount() {
        fail(Kind.LOGIN, "10.0.0.1", "ana@example.com", 2);
        limiter.succeeded(Kind.LOGIN, "10.0.0.1", "ana@example.com");
        fail(Kind.LOGIN, "10.0.0.1", "ana@example.com", 2);
        passes(Kind.LOGIN, "10.0.0.1", "ana@example.com");
    }

    @Test
    void theBlockLiftsWhenTheWindowPasses() {
        fail(Kind.LOGIN, "10.0.0.1", "ana@example.com", 3);
        blocked(Kind.LOGIN, "10.0.0.1", "ana@example.com");
        now.addAndGet(Duration.ofMinutes(14).toMillis());
        blocked(Kind.LOGIN, "10.0.0.1", "ana@example.com");
        now.addAndGet(Duration.ofMinutes(1).toMillis());
        passes(Kind.LOGIN, "10.0.0.1", "ana@example.com");
    }

    @Test
    void oldFailuresAgeOutOneByOne() {
        fail(Kind.LOGIN, "10.0.0.1", "ana@example.com", 2);
        now.addAndGet(Duration.ofMinutes(10).toMillis());
        fail(Kind.LOGIN, "10.0.0.1", "ana@example.com", 1);
        blocked(Kind.LOGIN, "10.0.0.1", "ana@example.com");
        // The first two expire 5 minutes later, leaving one: allowed again.
        now.addAndGet(Duration.ofMinutes(5).toMillis());
        passes(Kind.LOGIN, "10.0.0.1", "ana@example.com");
    }

    @Test
    void theMessageSaysHowLongToWait() {
        fail(Kind.LOGIN, "10.0.0.1", "ana@example.com", 3);
        now.addAndGet(Duration.ofMinutes(5).toMillis());
        assertThatThrownBy(() -> limiter.check(Kind.LOGIN, "10.0.0.1", "ana@example.com"))
                .hasMessageContaining("10 minutes");
    }

    @Test
    void registrationCodeGuessesAreLimitedByAddressAlone() {
        // With a nine-failure address cap, claims are cut off at a third of that.
        fail(Kind.CLAIM, "10.0.0.1", "a@example.com", 1);
        fail(Kind.CLAIM, "10.0.0.1", "b@example.com", 1);
        passes(Kind.CLAIM, "10.0.0.1", "c@example.com");
        fail(Kind.CLAIM, "10.0.0.1", "c@example.com", 1);
        blocked(Kind.CLAIM, "10.0.0.1", "d@example.com");
        passes(Kind.CLAIM, "10.0.0.2", "d@example.com");
    }

    @Test
    void signInAndClaimCountSeparately() {
        fail(Kind.LOGIN, "10.0.0.1", "ana@example.com", 3);
        passes(Kind.CLAIM, "10.0.0.1", "ana@example.com");
        fail(Kind.CLAIM, "10.0.0.5", "x@example.com", 3);
        passes(Kind.LOGIN, "10.0.0.5", "x@example.com");
    }
}
