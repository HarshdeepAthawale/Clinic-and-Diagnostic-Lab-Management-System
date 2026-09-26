package com.cdlms.config;

import com.cdlms.auth.AuthProperties;
import com.cdlms.auth.CsrfHeaderFilter;
import com.cdlms.auth.JwtCookieAuthenticationFilter;
import com.cdlms.auth.JwtService;
import com.cdlms.common.ErrorResponses;
import com.cdlms.user.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT-cookie security (ADR-005, ADR-009, ADR-014).
 *
 * <ul>
 *   <li>No server sessions, no form login, no HTTP basic.</li>
 *   <li>Spring's token CSRF is off; {@link CsrfHeaderFilter} requires a custom header instead.</li>
 *   <li>CORS is deliberately not configured: the browser only talks to the Next.js origin, which
 *       proxies {@code /api}. Opening CORS would break the CSRF protection — see ADR-014.</li>
 *   <li>Role checks live on the endpoints ({@code @PreAuthorize}); everything under {@code /api}
 *       except the public auth endpoints needs a login.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, UserRepository users,
                                            AuthProperties authProperties) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register", "/api/auth/register/claim",
                                "/api/auth/logout").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) -> ErrorResponses.write(response,
                                401, "Authentication required", "UNAUTHENTICATED"))
                        .accessDeniedHandler((request, response, e) -> ErrorResponses.write(response,
                                403, "You do not have access to this resource", "FORBIDDEN")))
                .addFilterBefore(new CsrfHeaderFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtCookieAuthenticationFilter(jwtService, users, authProperties.cookieName()),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
