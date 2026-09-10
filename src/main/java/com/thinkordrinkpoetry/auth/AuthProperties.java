package com.thinkordrinkpoetry.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String magicLinkBaseUrl,
        Duration magicLinkTtl,
        int magicLinkRequestLimit,
        Duration sessionTtl,
        String sessionCookieName,
        boolean secureSessionCookie) {
}
