package com.cdlms.auth;

import com.cdlms.common.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Slows down password and registration-code guessing (Docs/Security.md §2, ADR-030).
 *
 * <p>Only <em>failed</em> attempts count, inside a sliding window:
 * <ul>
 *   <li>a sign-in is refused after {@code accountFailures} failures for the same email <em>from the same address</em>
 *       (so a stranger can't lock a real user out from elsewhere);</li>
 *   <li>and after {@code addressFailures} failures of any kind from one address (guessing across many emails);</li>
 *   <li>a registration-code claim is refused after {@code addressFailures / 3} failures from one address.</li>
 * </ul>
 * A successful sign-in clears that email's count. Counts live in memory: they reset when the server restarts
 * and are per server — fine for a single instance; a shared store would be needed to scale out.
 */
@Component
public class AttemptLimiter {

    /** What is being attempted; each has its own counters. */
    public enum Kind { LOGIN, CLAIM }

    private final int accountFailures;
    private final int addressFailures;
    private final long windowMillis;
    private final LongSupplier clock;
    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    @Autowired
    public AttemptLimiter(@Value("${app.auth.max-failures-per-account:5}") int accountFailures,
                          @Value("${app.auth.max-failures-per-address:30}") int addressFailures,
                          @Value("${app.auth.failure-window:PT15M}") Duration window) {
        this(accountFailures, addressFailures, window, System::currentTimeMillis);
    }

    AttemptLimiter(int accountFailures, int addressFailures, Duration window, LongSupplier clock) {
        this.accountFailures = accountFailures;
        this.addressFailures = addressFailures;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    /** Throws {@code 429} if this caller has used up their attempts. Call before checking the password or code. */
    public void check(Kind kind, String address, String account) {
        long retry = Math.max(
                retryAfter(key(kind, "addr", address), kind == Kind.CLAIM ? Math.max(1, addressFailures / 3) : addressFailures),
                kind == Kind.LOGIN ? retryAfter(key(kind, "acct", address + "|" + normalise(account)), accountFailures) : 0);
        if (retry > 0) {
            long minutes = Math.max(1, (retry + 59_999) / 60_000);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many attempts. Please wait " + minutes + " minute" + (minutes == 1 ? "" : "s") + " and try again.");
        }
    }

    public void failed(Kind kind, String address, String account) {
        record(key(kind, "addr", address));
        if (kind == Kind.LOGIN) {
            record(key(kind, "acct", address + "|" + normalise(account)));
        }
        if (failures.size() > 20_000) {
            prune();
        }
    }

    /** A correct sign-in clears the count for that email from that address (the address count keeps running). */
    public void succeeded(Kind kind, String address, String account) {
        if (kind == Kind.LOGIN) {
            failures.remove(key(kind, "acct", address + "|" + normalise(account)));
        }
    }

    // ---------------------------------------------------------------- internals

    private long retryAfter(String key, int allowed) {
        Deque<Long> attempts = failures.get(key);
        if (attempts == null) {
            return 0;
        }
        long now = clock.getAsLong();
        synchronized (attempts) {
            drop(attempts, now);
            return attempts.size() >= allowed ? attempts.peekFirst() + windowMillis - now : 0;
        }
    }

    private void record(String key) {
        long now = clock.getAsLong();
        Deque<Long> attempts = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (attempts) {
            drop(attempts, now);
            attempts.addLast(now);
        }
    }

    private void drop(Deque<Long> attempts, long now) {
        while (!attempts.isEmpty() && now - attempts.peekFirst() >= windowMillis) {
            attempts.pollFirst();
        }
    }

    /** Forgets callers whose failures have all aged out, so the map can't grow without bound. */
    private void prune() {
        long now = clock.getAsLong();
        failures.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                drop(e.getValue(), now);
                return e.getValue().isEmpty();
            }
        });
    }

    private static String key(Kind kind, String scope, String value) {
        return kind + ":" + scope + ":" + value;
    }

    private static String normalise(String account) {
        return account == null ? "" : account.trim().toLowerCase(Locale.ROOT);
    }
}
