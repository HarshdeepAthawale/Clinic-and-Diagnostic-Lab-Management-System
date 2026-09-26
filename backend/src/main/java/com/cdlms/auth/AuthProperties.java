package com.cdlms.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code app.auth.*} settings (see application.yml).
 *
 * @param jwtSecret    HMAC signing key, at least 32 bytes. Comes from the {@code JWT_SECRET} env var.
 * @param tokenTtl     how long a login lasts; also the cookie's Max-Age
 * @param cookieName   name of the httpOnly cookie that carries the JWT (ADR-009)
 * @param cookieSecure whether the cookie is marked Secure; true everywhere except local dev
 */
@ConfigurationProperties("app.auth")
public record AuthProperties(String jwtSecret, Duration tokenTtl, String cookieName, boolean cookieSecure) {
}
