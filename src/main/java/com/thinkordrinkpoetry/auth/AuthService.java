package com.thinkordrinkpoetry.auth;

import com.thinkordrinkpoetry.poet.AccountStatus;
import com.thinkordrinkpoetry.poet.Poet;
import com.thinkordrinkpoetry.poet.PoetRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final AuthProperties properties;
    private final EmailAddressNormalizer emailAddressNormalizer;
    private final SecretTokenService secretTokenService;
    private final MagicLinkRequestRateLimiter rateLimiter;
    private final EmailLoginTokenRepository loginTokens;
    private final UserSessionRepository sessions;
    private final MagicLinkMailer magicLinkMailer;
    private final PoetRepository poets;
    private final TurnstileVerifier turnstileVerifier;

    AuthService(AuthProperties properties, EmailAddressNormalizer emailAddressNormalizer,
            SecretTokenService secretTokenService, MagicLinkRequestRateLimiter rateLimiter,
            EmailLoginTokenRepository loginTokens,
            UserSessionRepository sessions, MagicLinkMailer magicLinkMailer, PoetRepository poets,
            TurnstileVerifier turnstileVerifier) {
        this.properties = properties;
        this.emailAddressNormalizer = emailAddressNormalizer;
        this.secretTokenService = secretTokenService;
        this.rateLimiter = rateLimiter;
        this.loginTokens = loginTokens;
        this.sessions = sessions;
        this.magicLinkMailer = magicLinkMailer;
        this.poets = poets;
        this.turnstileVerifier = turnstileVerifier;
    }

    /**
     * Sign-in: only addresses that already belong to a poet receive email. Unknown addresses get the
     * same response with nothing sent, so the form neither spams strangers nor reveals who has an
     * account.
     */
    @Transactional
    void requestMagicLink(String submittedEmail, String clientIp) {
        String email = emailAddressNormalizer.normalize(submittedEmail);
        rateLimiter.checkClient(clientIp);
        Optional<Poet> poet = poets.findByEmail(email);
        if (poet.isEmpty()) {
            return;
        }
        sendMagicLink(email, poet);
    }

    /**
     * Sign-up: any address may receive a link, but only after a Turnstile challenge proves a person
     * submitted the form. An address that already has an account simply receives a sign-in link.
     */
    @Transactional
    void requestSignUp(String submittedEmail, String turnstileToken, String clientIp) {
        String email = emailAddressNormalizer.normalize(submittedEmail);
        rateLimiter.checkClient(clientIp);
        if (!turnstileVerifier.verify(turnstileToken, clientIp)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "We could not verify that you are human. Please try again.");
        }
        sendMagicLink(email, poets.findByEmail(email));
    }

    private void sendMagicLink(String email, Optional<Poet> poet) {
        if (!rateLimiter.allowEmail(email)) {
            return;
        }
        rateLimiter.checkGlobal();
        if (poet.isPresent() && poet.get().getAccountStatus() == AccountStatus.LOCKED) {
            magicLinkMailer.sendAccountNotice(email, "Your Think or Drink Poetry account is locked",
                    "Your account has been locked. Contact " + properties.adminContactEmail() + " for help.");
            return;
        }
        if (poet.isPresent() && poet.get().getAccountStatus() == AccountStatus.LEGACY_UNCLAIMED) {
            magicLinkMailer.sendAccountNotice(email, "Activate your Think or Drink Poetry account",
                    "This historical account must be activated before sign-in. Contact "
                            + properties.adminContactEmail() + " for help.");
            return;
        }
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
        UUID poetId = poets.findByEmail(loginToken.getEmail()).map(Poet::getId).orElse(null);
        UserSession session = sessions.save(new UserSession(
                secretTokenService.hash(sessionToken), loginToken.getEmail(), poetId, sessionExpiry));
        return new LoginResult(sessionToken, session.getExpiresAt(), poetId != null);
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
    public void attachPoet(UUID sessionId, UUID poetId) {
        sessions.findById(sessionId).ifPresent(session -> session.attachPoet(poetId));
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

    record LoginResult(String sessionToken, Instant expiresAt, boolean profileExists) {
    }
}
