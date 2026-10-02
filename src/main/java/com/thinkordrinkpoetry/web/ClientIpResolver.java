package com.thinkordrinkpoetry.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the visitor's IP address for rate limiting.
 *
 * <p>In production every request arrives through Cloudflare Tunnel and Caddy, so the TCP peer is
 * always the proxy. Cloudflare overwrites {@code CF-Connecting-IP} with the real visitor address,
 * which makes it the only trustworthy source. The header is trusted only because the application
 * port is not published outside the Docker network; never expose the app directly.
 */
@Component
public class ClientIpResolver {
    private final String header;

    public ClientIpResolver(@Value("${app.client-ip-header:CF-Connecting-IP}") String header) {
        this.header = header;
    }

    public String resolve(HttpServletRequest request) {
        String value = header.isBlank() ? null : request.getHeader(header);
        return value == null || value.isBlank() ? request.getRemoteAddr() : value.trim();
    }
}
