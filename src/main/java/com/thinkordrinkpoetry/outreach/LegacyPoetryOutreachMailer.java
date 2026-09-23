package com.thinkordrinkpoetry.outreach;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
class LegacyPoetryOutreachMailer {
    private final JavaMailSender mailSender;
    private final String from;

    LegacyPoetryOutreachMailer(JavaMailSender mailSender, @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    void send(String recipient, String replyTo, String displayName, String publicUrl) {
        String plainText = "Hello " + displayName + ",\n\n"
                + "Your poetry from 1999 is live and ready.\n\n"
                + "Yes, you heard that right. Back in the late '90s you wrote some poetry, and I have been "
                + "toting around the database for close to 30 years. It now has a new home.\n\n"
                + "See your poetry: " + publicUrl + "\n\n"
                + "If this message reached the wrong person, or you would prefer not to receive future messages, "
                + "reply to this email.\n";
        String html = page(displayName)
                + "<p style=\"margin:28px 0;text-align:center\"><a href=\"" + escapeHtml(publicUrl)
                + "\" style=\"display:inline-block;padding:14px 24px;background:linear-gradient(135deg,#c9a84c,#a8872d);color:#080a0f;text-decoration:none;font-family:Georgia,serif;font-weight:bold;letter-spacing:.08em\">SEE YOUR POETRY</a></p>"
                + "<p style=\"margin:0;color:#8b8992;font:13px Georgia,serif;line-height:1.5\">If the button does not work, copy this link into your browser:<br><a href=\""
                + escapeHtml(publicUrl) + "\" style=\"color:#e8c97a;word-break:break-all\">"
                + escapeHtml(publicUrl) + "</a></p>"
                + "<p style=\"margin:26px 0 0;color:#8b8992;font:13px Georgia,serif;line-height:1.5\">If this message reached the wrong person, or you would prefer not to receive future messages, reply to this email.</p>"
                + footer();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            helper.setFrom(from);
            helper.setReplyTo(replyTo);
            helper.setTo(recipient);
            helper.setSubject("Your poetry from 1999 is live");
            helper.setText(plainText, html);
            helper.addInline("raven-logo", new ClassPathResource("email/raven-logo.png"), "image/png");
            mailSender.send(message);
        } catch (MessagingException | MailException exception) {
            throw new IllegalStateException("Unable to send the legacy poetry outreach email.", exception);
        }
    }

    private static String page(String displayName) {
        return "<div style=\"margin:0;padding:32px 16px;background:#080a0f;color:#e4ddd0\"><div style=\"max-width:560px;margin:auto;padding:36px;background:linear-gradient(160deg,#161a27,#0e1018);border:1px solid #2a2840\"><div style=\"height:1px;background:linear-gradient(90deg,transparent,#c9a84c,transparent);margin-bottom:28px\"></div><p style=\"text-align:center;margin:0 0 20px\"><img src=\"cid:raven-logo\" alt=\"Think or Drink Poetry\" style=\"width:260px;max-width:100%;height:auto\"></p><p style=\"margin:0 0 26px;text-align:center;color:#8b8992;font:italic 14px Georgia,serif\">Where poets find their voice in shadow and light.</p><h1 style=\"margin:0 0 16px;color:#e4ddd0;text-align:center;font:600 24px Georgia,serif\">Your poetry is live</h1><p style=\"margin:0;color:#e4ddd0;font:16px Georgia,serif;line-height:1.6\">Hello "
                + escapeHtml(displayName) + ",</p><p style=\"margin:16px 0 0;color:#e4ddd0;font:16px Georgia,serif;line-height:1.6\">Your poetry from 1999 is live and ready.</p><p style=\"margin:16px 0 0;color:#e4ddd0;font:16px Georgia,serif;line-height:1.6\">Yes, you heard that right. Back in the late ’90s you wrote some poetry, and I have been toting around the database for close to 30 years. It now has a new home.</p><p style=\"margin:16px 0 0;color:#e4ddd0;font:16px Georgia,serif;line-height:1.6\">Come take a look if you would like to revisit what you wrote.</p>";
    }

    private static String footer() {
        return "<div style=\"height:1px;background:linear-gradient(90deg,transparent,#c9a84c,transparent);margin-top:30px\"></div></div></div>";
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
