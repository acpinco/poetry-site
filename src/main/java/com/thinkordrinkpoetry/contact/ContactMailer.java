package com.thinkordrinkpoetry.contact;

import com.thinkordrinkpoetry.poet.Poet;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
class ContactMailer {
    private final JavaMailSender mailSender;
    private final String from;
    private final String recipient;

    ContactMailer(JavaMailSender mailSender, @Value("${app.mail.from}") String from,
            @Value("${app.contact.to}") String recipient) {
        this.mailSender = mailSender;
        this.from = from;
        this.recipient = recipient;
    }

    void send(Poet poet, String message) {
        String fullName = poet.getFullName() == null ? poet.getFirstName() + " " + poet.getLastName() : poet.getFullName();
        String penName = poet.getPenName() == null || poet.getPenName().isBlank() ? fullName : poet.getPenName();
        String text = "A signed-in poet sent a message to The Raven's Nest.\n\n"
                + "Full name: " + fullName + "\n"
                + "Pen name: " + penName + "\n"
                + "Account email: " + poet.getEmail() + "\n\n"
                + "Message:\n" + message.trim();
        try {
            MimeMessage email = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(email, "UTF-8");
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setSubject("[The Raven's Nest] Message from " + fullName + " (" + penName + ")");
            helper.setText(text, false);
            mailSender.send(email);
        } catch (MessagingException | MailException exception) {
            throw new IllegalStateException("Unable to send the contact message.", exception);
        }
    }
}
