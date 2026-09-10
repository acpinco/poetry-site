package com.thinkordrinkpoetry.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class EmailAddressNormalizerTest {
    private final EmailAddressNormalizer normalizer = new EmailAddressNormalizer();

    @Test
    void normalizesAValidEmail() {
        assertThat(normalizer.normalize("  MAYA@example.com ")).isEqualTo("maya@example.com");
    }

    @Test
    void rejectsAnInvalidEmail() {
        assertThatThrownBy(() -> normalizer.normalize("not-an-email"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
