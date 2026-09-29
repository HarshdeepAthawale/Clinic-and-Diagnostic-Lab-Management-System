package com.cdlms.auth;

import com.cdlms.auth.AuthDtos.ClaimAccountRequest;
import com.cdlms.auth.AuthDtos.LoginRequest;
import com.cdlms.auth.AuthDtos.MeResponse;
import com.cdlms.auth.AuthDtos.RegisterRequest;
import com.cdlms.user.User;
import com.cdlms.auth.AttemptLimiter.Kind;
import com.cdlms.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final AuthCookies cookies;
    private final AttemptLimiter limiter;

    public AuthController(AuthService authService, JwtService jwtService, AuthCookies cookies, AttemptLimiter limiter) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.cookies = cookies;
        this.limiter = limiter;
    }

    @PostMapping("/register")
    public ResponseEntity<MeResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.registerPatient(request);
        return withLoginCookie(ResponseEntity.status(HttpStatus.CREATED), user);
    }

    /** Patient signup that links to an existing front-desk record via its registration code. */
    @PostMapping("/register/claim")
    public ResponseEntity<MeResponse> claim(@Valid @RequestBody ClaimAccountRequest request, HttpServletRequest http) {
        String address = http.getRemoteAddr();
        limiter.check(Kind.CLAIM, address, request.email());
        User user;
        try {
            user = authService.claimAccount(request);
        } catch (ApiException e) {
            limiter.failed(Kind.CLAIM, address, request.email());
            throw e;
        }
        return withLoginCookie(ResponseEntity.status(HttpStatus.CREATED), user);
    }

    @PostMapping("/login")
    public ResponseEntity<MeResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String address = http.getRemoteAddr();
        limiter.check(Kind.LOGIN, address, request.email());
        User user;
        try {
            user = authService.login(request);
        } catch (ApiException e) {
            limiter.failed(Kind.LOGIN, address, request.email());
            throw e;
        }
        limiter.succeeded(Kind.LOGIN, address, request.email());
        return withLoginCookie(ResponseEntity.ok(), user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthUser authUser) {
        return authService.me(authUser);
    }

    private ResponseEntity<MeResponse> withLoginCookie(ResponseEntity.BodyBuilder builder, User user) {
        return builder
                .header(HttpHeaders.SET_COOKIE, cookies.create(jwtService.issue(user)).toString())
                .body(authService.toMe(user));
    }
}
