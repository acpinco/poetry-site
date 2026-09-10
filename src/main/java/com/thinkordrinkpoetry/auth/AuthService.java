package com.thinkordrinkpoetry.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class AuthService {
    private final AuthProperties properties;
    private final EmailAddressNormalizer emailAddressNormalizer;
    private final SecretTokenService secretTokenService;
    private final MagicLinkRequestRateLimiter rateLimiter;
    private final EmailLoginTokenRepository loginTokens;
    private final UserSessionRepository sessions;
    private final MagicLinkMailer magicLinkMailer;

    AuthService(AuthProperties properties, EmailAddressNormalizer emailAddressNormalizer,
            SecretTokenService secretTokenService, MagicLinkRequestRateLimiter rateLimiter,
            EmailLoginTokenRepository loginTokens,
            UserSessionRepository sessions, MagicLinkMailer magicLinkMailer) {
        this.properties = properties;
        this.emailAddressNormalizer = emailAddressNormalizer;
        this.secretTokenService = secretTokenService;
        this.rateLimiter = rateLimiter;
        this.loginTokens = loginTokens;
        this.sessions = sessions;
        this.magicLinkMailer = magicLinkMailer;
    }

    @Transactional
    void requestMagicLink(String submittedEmail, String clientIp) {
        String email = emailAddressNormalizer.normalize(submittedEmail);
        rateLimiter.check(email, clientIp);
        String token = secretTokenService.create();
        loginTokens.save(new EmailLoginToken(email, secretTokenService.hash(token),
                Instant.now().plus(properties.magicLinkTtl())));
        magicLinkMailer.send(email, magicLinkUrl(token));
    }

    @Transactional
    LoginResult consumeMagicLink(String token) {
        Instant now = Instant.now();
        EmailLoginToken loginToken = loginTokens.findByTokenHash(secretTokenService.hash(token))
                .orElseThrow(this::invalidMagicLink);
        if (loginToken.isExpiredAt(now)) {
            loginTokens.delete(loginToken);
            throw invalidMagicLink();
        }

        String sessionToken = secretTokenService.create();
        Instant sessionExpiry = now.plus(properties.sessionTtl());
        UserSession session = sessions.save(new UserSession(
                secretTokenService.hash(sessionToken), loginToken.getEmail(), sessionExpiry));
        loginTokens.delete(loginToken);
        return new LoginResult(sessionToken, session.getExpiresAt());
    }

    @Transactional
    Optional<AuthenticatedUser> authenticate(String sessionToken) {
        Instant now = Instant.now();
        return sessions.findActiveByTokenHash(secretTokenService.hash(sessionToken), now)
                .map(session -> {
                    session.markUsed(now);
                    return new AuthenticatedUser(session.getId(), session.getAuthenticatedEmail(),
                            session.getPoetId(), session.getExpiresAt());
                });
    }

    @Transactional
    void logout(String sessionToken) {
        sessions.findActiveByTokenHash(secretTokenService.hash(sessionToken), Instant.now())
                .ifPresent(session -> session.revoke(Instant.now()));
    }

    @Transactional
    void removeExpiredLoginTokens() {
        loginTokens.deleteExpiredBefore(Instant.now());
    }

    @Transactional
    void removeExpiredOrRevokedSessions() {
        sessions.deleteExpiredOrRevokedBefore(Instant.now());
    }

    private String magicLinkUrl(String token) {
        return properties.magicLinkBaseUrl().replaceAll("/+$", "") + "/api/auth/magic-links/" + token;
    }

    private ResponseStatusException invalidMagicLink() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "This sign-in link is invalid or has expired.");
    }

    record LoginResult(String sessionToken, Instant expiresAt) {
    }
}
