package com.thinkordrinkpoetry.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SecretTokenServiceTest {
    private final SecretTokenService tokenService = new SecretTokenService();

    @Test
    void createsOpaqueUrlSafeTokensAndStableSha256Hashes() {
        String token = tokenService.create();

        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(tokenService.hash(token)).hasSize(64).isEqualTo(tokenService.hash(token));
        assertThat(tokenService.hash(UUID.randomUUID().toString())).isNotEqualTo(tokenService.hash(token));
    }
}
