package com.thinkordrinkpoetry.auth;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class EmailAddressNormalizer {

    String normalize(String email) {
        if (email == null) {
            throw invalidEmail();
        }

        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < 3 || normalized.length() > 320) {
            throw invalidEmail();
        }

        try {
            InternetAddress address = new InternetAddress(normalized, true);
            address.validate();
            if (address.isGroup() || !normalized.equals(address.getAddress())) {
                throw invalidEmail();
            }
            return normalized;
        } catch (AddressException exception) {
            throw invalidEmail();
        }
    }

    private ResponseStatusException invalidEmail() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address.");
    }
}
