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

/**
 * Sends sign-in email in the background so a slow or unavailable SMTP server never delays or fails
 * the sign-in request. Delivery failures are logged; the visitor can simply request another link.
 */
@Component
class MagicLinkMailer {
    private static final Logger log = LoggerFactory.getLogger(MagicLinkMailer.class);

    private final JavaMailSender mailSender;
    private final String from;

    MagicLinkMailer(JavaMailSender mailSender, @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Async
    void send(String email, String url) {
        String plainText = "Use this link to sign in. It expires in 15 minutes:\n\n" + url;
        String html = emailPage("Your sign-in link is ready", "Use the button below to enter Think or Drink Poetry. It expires in 15 minutes.")
                + "<p style=\"margin:28px 0;text-align:center\"><a href=\"" + escapeHtml(url) + "\" style=\"display:inline-block;padding:14px 24px;background:linear-gradient(135deg,#c9a84c,#a8872d);color:#080a0f;text-decoration:none;font-family:Georgia,serif;font-weight:bold;letter-spacing:.08em\">TAKE FLIGHT</a></p>"
                + "<p style=\"margin:0;color:#8b8992;font:13px Georgia,serif;line-height:1.5\">If the button does not work, copy this link into your browser:<br><a href=\"" + escapeHtml(url) + "\" style=\"color:#e8c97a;word-break:break-all\">" + escapeHtml(url) + "</a></p>"
                + emailFooter();
        send(email, "Your Think or Drink Poetry sign-in link", plainText, html);
    }

    @Async
    void sendAccountNotice(String email, String subject, String text) {
        send(email, subject, text, emailPage(subject, text) + emailFooter());
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

    private static String emailPage(String heading, String text) {
        return "<div style=\"margin:0;padding:32px 16px;background:#080a0f;color:#e4ddd0\"><div style=\"max-width:560px;margin:auto;padding:36px;background:linear-gradient(160deg,#161a27,#0e1018);border:1px solid #2a2840\"><div style=\"height:1px;background:linear-gradient(90deg,transparent,#c9a84c,transparent);margin-bottom:28px\"></div><p style=\"text-align:center;margin:0 0 20px\"><img src=\"cid:raven-logo\" alt=\"Think or Drink Poetry\" style=\"width:260px;max-width:100%;height:auto\"></p><p style=\"margin:0 0 26px;text-align:center;color:#8b8992;font:italic 14px Georgia,serif\">Where poets find their voice in shadow and light.</p><h1 style=\"margin:0 0 16px;color:#e4ddd0;text-align:center;font:600 24px Georgia,serif\">" + escapeHtml(heading) + "</h1><p style=\"margin:0;color:#e4ddd0;font:16px Georgia,serif;line-height:1.6\">" + escapeHtml(text) + "</p>";
    }

    private static String emailFooter() {
        return "<div style=\"height:1px;background:linear-gradient(90deg,transparent,#c9a84c,transparent);margin-top:30px\"></div></div></div>";
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
