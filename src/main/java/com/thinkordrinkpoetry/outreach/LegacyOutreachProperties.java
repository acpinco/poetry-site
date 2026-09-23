package com.thinkordrinkpoetry.outreach;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.legacy-outreach")
record LegacyOutreachProperties(
        boolean enabled,
        String mode,
        String campaignId,
        String confirmation,
        String testRecipients,
        int limit,
        boolean resend,
        String replyTo,
        String publicBaseUrl) {
}
