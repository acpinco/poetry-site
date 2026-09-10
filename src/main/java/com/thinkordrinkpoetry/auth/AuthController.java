package com.thinkordrinkpoetry.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {
    private final AuthService authService;
    private final AuthProperties properties;

    AuthController(AuthService authService, AuthProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @PostMapping("/magic-links")
    ResponseEntity<Void> requestMagicLink(@Valid @RequestBody MagicLinkRequest request, HttpServletRequest servletRequest) {
        authService.requestMagicLink(request.email(), servletRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/magic-links/{token}", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> showSignInConfirmation(@PathVariable String token) {
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String page = """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8"><title>Confirm sign in</title></head>
                <body><h1>Sign in to Think or Drink Poetry</h1>
                <p>Confirm that you want to use this sign-in link.</p>
                <form method="post" action="/api/auth/magic-links/%s/consume">
                <button type="submit">Sign in</button></form></body></html>
                """.formatted(encodedToken);
        return ResponseEntity.ok().body(page);
    }

    @PostMapping("/magic-links/{token}/consume")
    ResponseEntity<LoginResponse> consumeMagicLink(@PathVariable String token) {
        AuthService.LoginResult result = authService.consumeMagicLink(token);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookie(result.sessionToken(), properties.sessionTtl()).toString())
                .body(new LoginResponse(result.expiresAt()));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        String sessionToken = sessionCookieValue(request);
        if (user != null && sessionToken != null) {
            authService.logout(sessionToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO).toString())
                .build();
    }

    private String sessionCookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (properties.sessionCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    @GetMapping("/me")
    MeResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return new MeResponse(user.email(), user.poetId(), user.expiresAt());
    }

    private ResponseCookie sessionCookie(String value, Duration maxAge) {
        return ResponseCookie.from(properties.sessionCookieName(), value)
                .httpOnly(true)
                .secure(properties.secureSessionCookie())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    record MagicLinkRequest(@NotBlank String email) {
    }

    record LoginResponse(java.time.Instant expiresAt) {
    }

    record MeResponse(String email, java.util.UUID poetId, java.time.Instant expiresAt) {
    }
}
