package com.thinkordrinkpoetry.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class RequiredContactSettingsTest {

    @Configuration
    @EnableConfigurationProperties(AuthProperties.class)
    static class AuthPropertiesOnly {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AuthPropertiesOnly.class)
            .withPropertyValues("app.auth.admin-contact-email=${ADMIN_CONTACT_EMAIL}");

    @Test
    void startupFailsWhenTheAdminContactEmailIsNotConfigured() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void startupSucceedsWithAnAdminContactEmail() {
        runner.withPropertyValues("ADMIN_CONTACT_EMAIL=support@example.test")
                .run(context -> assertThat(context.getBean(AuthProperties.class).adminContactEmail())
                        .isEqualTo("support@example.test"));
    }
}
