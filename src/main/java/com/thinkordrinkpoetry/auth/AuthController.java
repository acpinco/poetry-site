package com.thinkordrinkpoetry.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    ResponseEntity<String> showSignInConfirmation(@PathVariable String token) {
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String page = """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Sign in | Think or Drink Poetry</title></head>
                <body style="margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px;background:radial-gradient(ellipse at 30%% 20%%,#12102a 0%%,#080a0f 60%%);color:#e4ddd0;font-family:Georgia,serif">
                <main style="width:100%%;max-width:480px;background:linear-gradient(160deg,#161a27,#0e1018);border:1px solid #2a2840;box-shadow:0 0 28px rgba(201,168,76,.12)">
                <div style="height:1px;margin:0 32px;background:linear-gradient(90deg,transparent,#c9a84c,transparent)"></div>
                <div style="padding:48px 40px;text-align:center"><img src="/api/auth/branding/raven-logo.png" alt="Think or Drink Poetry" style="width:288px;max-width:100%%;height:auto">
                <p style="margin:22px 0 36px;color:#6a6580;font-size:14px;font-style:italic">Where poets find their voice in shadow and light.</p>
                <h1 style="margin:0 0 14px;font-size:26px;font-weight:600">Welcome back</h1>
                <p style="margin:0 0 28px;color:#8b8992;line-height:1.55">Confirm that you want to use this sign-in link.</p>
                <form method="post" action="/api/auth/magic-links/%s/consume"><button type="submit" style="width:100%%;padding:14px;border:0;border-radius:2px;background:linear-gradient(135deg,#c9a84c,#a8872d);color:#080a0f;cursor:pointer;font-family:Georgia,serif;font-weight:bold;letter-spacing:.16em;text-transform:uppercase">Take Flight</button></form>
                </div><div style="height:1px;margin:0 32px;background:linear-gradient(90deg,transparent,#c9a84c,transparent)"></div></main></body></html>
                """.formatted(encodedToken);
        return ResponseEntity.ok().body(page);
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
            String destination = properties.frontendBaseUrl().replaceAll("/+$", "")
                    + (result.profileExists() ? "/swagger-ui.html" : "/");
            return ResponseEntity.status(HttpStatus.SEE_OTHER)
                    .header(HttpHeaders.SET_COOKIE, sessionCookie)
                    .header(HttpHeaders.LOCATION, destination)
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

    record MagicLinkRequest(@NotBlank String email) {
    }

    record LoginResponse(java.time.Instant expiresAt, boolean profileExists) {
    }

    record MeResponse(String email, java.util.UUID poetId, java.time.Instant expiresAt) {
    }
}
