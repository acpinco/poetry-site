package com.thinkordrinkpoetry.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RequiredContactSettingsTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AuthConfiguration.class)
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
