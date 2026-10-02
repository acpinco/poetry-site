package com.thinkordrinkpoetry.auth;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Verifies Cloudflare Turnstile tokens from the sign-up form. Fails closed: without a configured
 * secret, or when Cloudflare cannot be reached, new-account sign-up is refused.
 */
@Component
class TurnstileVerifier {
    private static final Logger log = LoggerFactory.getLogger(TurnstileVerifier.class);
    private static final String SITEVERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    private static final int MAX_TOKEN_LENGTH = 2048;

    private final String secretKey;
    private final RestClient restClient;

    TurnstileVerifier(AuthProperties properties) {
        secretKey = properties.turnstileSecretKey() == null ? "" : properties.turnstileSecretKey().trim();
        if (secretKey.isEmpty()) {
            log.warn("TURNSTILE_SECRET_KEY is not set; new-account sign-up is disabled.");
        }
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    boolean verify(String token, String clientIp) {
        if (secretKey.isEmpty() || token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            return false;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secretKey);
        form.add("response", token);
        form.add("remoteip", clientIp);
        try {
            Map<String, Object> result = restClient.post()
                    .uri(SITEVERIFY_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            return result != null && Boolean.TRUE.equals(result.get("success"));
        } catch (RestClientException exception) {
            log.warn("Turnstile verification failed: {}", exception.getMessage());
            return false;
        }
    }
}
