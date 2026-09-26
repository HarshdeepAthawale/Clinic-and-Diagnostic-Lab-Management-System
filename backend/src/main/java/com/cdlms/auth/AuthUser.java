package com.cdlms.auth;

import com.cdlms.user.Role;

import java.util.UUID;

/** The authenticated caller, available in controllers via {@code @AuthenticationPrincipal}. */
public record AuthUser(UUID id, String email, Role role) {
}
