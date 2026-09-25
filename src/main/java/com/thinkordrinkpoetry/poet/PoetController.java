package com.thinkordrinkpoetry.poet;

import com.thinkordrinkpoetry.auth.AuthService;
import com.thinkordrinkpoetry.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/poets")
public class PoetController {
    private final PoetRepository poets;
    private final AuthService authService;
    private final AuthPropertiesFacade properties;

    public PoetController(
            PoetRepository poets,
            AuthService authService,
            com.thinkordrinkpoetry.auth.AuthProperties authProperties) {
        this.poets = poets;
        this.authService = authService;
        this.properties = new AuthPropertiesFacade(authProperties.adminEmail());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public PoetResponse create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody PoetRequest request) {
        AuthenticatedUser authenticatedUser = authenticatedUser(user);
        if (authenticatedUser.poetId() != null || poets.findByEmail(authenticatedUser.email()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A poet profile already exists.");
        }

        PoetRole role = authenticatedUser.email().equalsIgnoreCase(properties.adminEmail())
                ? PoetRole.ADMIN
                : PoetRole.USER;
        Poet poet = new Poet(
                authenticatedUser.email(),
                request.firstName.trim(),
                request.lastName.trim(),
                optional(request.penName),
                request.bio,
                null,
                AccountStatus.ACTIVE,
                role);
        poet = poets.save(poet);
        authService.attachPoet(authenticatedUser.sessionId(), poet.getId());
        return response(poet);
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public PoetResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return response(current(user));
    }

    @PutMapping("/me")
    @Transactional
    public PoetResponse update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody PoetRequest request) {
        Poet poet = current(user);
        poet.updateProfile(
                request.firstName.trim(),
                request.lastName.trim(),
                optional(request.penName),
                request.bio);
        return response(poet);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@AuthenticationPrincipal AuthenticatedUser user) {
        poets.delete(current(user));
    }

    private Poet current(AuthenticatedUser user) {
        AuthenticatedUser authenticatedUser = authenticatedUser(user);
        if (authenticatedUser.poetId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Create a poet profile first.");
        }
        return poets.findById(authenticatedUser.poetId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static AuthenticatedUser authenticatedUser(AuthenticatedUser user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return user;
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static PoetResponse response(Poet poet) {
        String fullName = poet.getFullName() == null
                ? poet.getFirstName() + " " + poet.getLastName()
                : poet.getFullName();
        Instant createdAt = poet.getLegacySubmittedOn() == null
                ? poet.getCreatedAt()
                : poet.getLegacySubmittedOn().atStartOfDay().toInstant(ZoneOffset.UTC);
        String penName = poet.getPenName() == null ? fullName : poet.getPenName();
        return new PoetResponse(
                poet.getId(),
                poet.getEmail(),
                poet.getFirstName(),
                poet.getLastName(),
                fullName,
                penName,
                poet.getBio(),
                createdAt,
                poet.getUpdatedAt(),
                poet.getAccountStatus());
    }

    public record PoetRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Size(max = 100) String penName,
            @Size(max = 500) String bio) {}

    public record PoetResponse(
            java.util.UUID poetId,
            String email,
            String firstName,
            String lastName,
            String fullName,
            String penName,
            String bio,
            Instant createdAt,
            Instant updatedAt,
            AccountStatus accountStatus) {}

    private record AuthPropertiesFacade(String adminEmail) {}
}
