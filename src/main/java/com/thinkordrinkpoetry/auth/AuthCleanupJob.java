package com.thinkordrinkpoetry.auth;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class AuthCleanupJob {
    private final AuthService authService;

    AuthCleanupJob(AuthService authService) {
        this.authService = authService;
    }

    @Scheduled(cron = "0 0 * * * *")
    void removeExpiredLoginTokens() {
        authService.removeExpiredLoginTokens();
    }

    @Scheduled(cron = "0 30 3 * * *")
    void removeExpiredOrRevokedSessions() {
        authService.removeExpiredOrRevokedSessions();
    }
}
