package com.thinkordrinkpoetry.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
    ResponseEntity<Void> followMagicLink(@PathVariable String token) {
        AuthService.LoginResult result = authService.consumeMagicLink(token);
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.SET_COOKIE, sessionCookie(result.sessionToken(), properties.sessionTtl()).toString())
                .header(HttpHeaders.LOCATION, destination(result))
                .build();
    }

    @GetMapping(value = "/branding/raven-logo.png", produces = MediaType.IMAGE_PNG_VALUE)
    Resource ravenLogo() {
        return new ClassPathResource("email/raven-logo.png");
    }

    @PostMapping("/magic-links/{token}/consume")
    ResponseEntity<?> consumeMagicLink(@PathVariable String token, HttpServletRequest request) {
        AuthService.LoginResult result = authService.consumeMagicLink(token);
        String sessionCookie = sessionCookie(result.sessionToken(), properties.sessionTtl()).toString();
        if (request.getHeader(HttpHeaders.ACCEPT) != null && request.getHeader(HttpHeaders.ACCEPT).contains(MediaType.TEXT_HTML_VALUE)) {
            return ResponseEntity.status(HttpStatus.SEE_OTHER)
                    .header(HttpHeaders.SET_COOKIE, sessionCookie)
                    .header(HttpHeaders.LOCATION, destination(result))
                    .build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookie)
                .body(new LoginResponse(result.expiresAt(), result.profileExists()));
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

    private String destination(AuthService.LoginResult result) {
        return properties.frontendBaseUrl().replaceAll("/+$", "")
                + (result.profileExists() ? "/home" : "/account/setup");
    }

    record MagicLinkRequest(@NotBlank String email) {
    }

    record LoginResponse(java.time.Instant expiresAt, boolean profileExists) {
    }

    record MeResponse(String email, java.util.UUID poetId, java.time.Instant expiresAt) {
    }
}
