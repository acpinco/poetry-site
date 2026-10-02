package com.thinkordrinkpoetry.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class MagicLinkMailerTest {

    private final MagicLinkMailer mailer = new MagicLinkMailer(
            mock(JavaMailSender.class), templateEngine(), properties(Duration.ofMinutes(20)), "noreply@example.test");

    @Test
    void signInEmailLinksToTheTokenAndStatesTheConfiguredLifetime() {
        String url = "https://poetry.example.test/api/auth/magic-links/abc?x=1&y=2";

        String html = mailer.signInHtml(url);

        assertThat(html)
                .contains("Your sign-in link is ready")
                .contains("It expires in 20 minutes.")
                .contains("href=\"https://poetry.example.test/api/auth/magic-links/abc?x=1&amp;y=2\"")
                .contains("cid:raven-logo");
        assertThat(mailer.signInText(url)).isEqualTo("Use this link to sign in. It expires in 20 minutes:\n\n" + url);
    }

    @Test
    void accountNoticesEscapeTheirText() {
        String html = mailer.accountNoticeHtml("Account <locked>", "Contact a&b@example.test");

        assertThat(html)
                .contains("Account &lt;locked&gt;")
                .contains("Contact a&amp;b@example.test")
                .doesNotContain("<locked>");
    }

    private static SpringTemplateEngine templateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private static AuthProperties properties(Duration magicLinkTtl) {
        return new AuthProperties(
                "http://localhost:8080",
                "http://localhost:5173",
                magicLinkTtl,
                5,
                Duration.ofDays(7),
                "poetry_session",
                false,
                "",
                "support@example.test",
                10,
                200,
                "",
                "");
    }
}
