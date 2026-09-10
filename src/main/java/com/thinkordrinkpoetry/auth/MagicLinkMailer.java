package com.thinkordrinkpoetry.auth;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
class MagicLinkMailer {
    private final JavaMailSender mailSender;

    MagicLinkMailer(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    void send(String email, String url) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(email);
            helper.setSubject("Your Think or Drink Poetry sign-in link");
            helper.setText("Use this link to sign in. It expires in 15 minutes:\n\n" + url, false);
            mailSender.send(message);
        } catch (MessagingException | MailException exception) {
            throw new IllegalStateException("Unable to send the sign-in email.", exception);
        }
    }
}
