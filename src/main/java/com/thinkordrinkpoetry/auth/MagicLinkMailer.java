package com.thinkordrinkpoetry.auth;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Sends sign-in email in the background so a slow or unavailable SMTP server never delays or fails
 * the sign-in request. Delivery failures are logged; the visitor can simply request another link.
 */
@Component
class MagicLinkMailer {
    private static final Logger log = LoggerFactory.getLogger(MagicLinkMailer.class);

    private final JavaMailSender mailSender;
    private final ITemplateEngine templates;
    private final long linkLifetimeMinutes;
    private final String from;

    MagicLinkMailer(JavaMailSender mailSender, ITemplateEngine templates, AuthProperties properties,
            @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.templates = templates;
        this.linkLifetimeMinutes = properties.magicLinkTtl().toMinutes();
        this.from = from;
    }

    @Async
    void send(String email, String url) {
        send(email, "Your Think or Drink Poetry sign-in link", signInText(url), signInHtml(url));
    }

    @Async
    void sendAccountNotice(String email, String subject, String text) {
        send(email, subject, text, accountNoticeHtml(subject, text));
    }

    String signInText(String url) {
        return "Use this link to sign in. It expires in " + linkLifetimeMinutes + " minutes:\n\n" + url;
    }

    String signInHtml(String url) {
        Context context = new Context();
        context.setVariable("url", url);
        context.setVariable("minutes", linkLifetimeMinutes);
        return templates.process("email/sign-in-link", context);
    }

    String accountNoticeHtml(String subject, String text) {
        Context context = new Context();
        context.setVariable("subject", subject);
        context.setVariable("text", text);
        return templates.process("email/account-notice", context);
    }

    private void send(String email, String subject, String plainText, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(plainText, html);
            helper.addInline("raven-logo", new ClassPathResource("email/raven-logo.png"), "image/png");
            mailSender.send(message);
        } catch (MessagingException | MailException exception) {
            log.error("Unable to send \"{}\" email.", subject, exception);
        }
    }
}
