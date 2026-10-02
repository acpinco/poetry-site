package com.thinkordrinkpoetry.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.auth")
@Validated
public record AuthProperties(
        String magicLinkBaseUrl,
        String frontendBaseUrl,
        Duration magicLinkTtl,
        int magicLinkRequestLimit,
        Duration sessionTtl,
        String sessionCookieName,
        boolean secureSessionCookie,
        String adminEmail,
        // Shown to locked-out poets; validated so a missing setting stops startup instead of
        // mailing them a literal "${ADMIN_CONTACT_EMAIL}".
        @NotBlank @Email String adminContactEmail,
        int magicLinkIpLimit,
        int magicLinkGlobalLimit,
        String turnstileSiteKey,
        String turnstileSecretKey) {
}
