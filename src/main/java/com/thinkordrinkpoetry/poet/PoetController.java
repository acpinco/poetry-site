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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/poets")
public class PoetController {
    private final PoetRepository poets; private final AuthService authService; private final AuthPropertiesFacade properties;
    public PoetController(PoetRepository poets, AuthService authService, com.thinkordrinkpoetry.auth.AuthProperties authProperties) {
        this.poets = poets; this.authService = authService; this.properties = new AuthPropertiesFacade(authProperties.adminEmail());
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public PoetResponse create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody PoetRequest request) {
        if (user.poetId() != null || poets.findByEmail(user.email()).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "A poet profile already exists.");
        PoetRole role = user.email().equalsIgnoreCase(properties.adminEmail) ? PoetRole.ADMIN : PoetRole.USER;
        Poet poet = poets.save(new Poet(user.email(), request.firstName.trim(), request.lastName.trim(), optional(request.penName), request.bio, null, AccountStatus.ACTIVE, role));
        authService.attachPoet(user.sessionId(), poet.getId());
        return response(poet);
    }
    @GetMapping("/me") @Transactional(readOnly = true)
    public PoetResponse me(@AuthenticationPrincipal AuthenticatedUser user) { return response(current(user)); }
    @PutMapping("/me") @Transactional
    public PoetResponse update(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody PoetRequest request) {
        Poet poet=current(user); poet.updateProfile(request.firstName.trim(), request.lastName.trim(), optional(request.penName), request.bio); return response(poet);
    }
    @DeleteMapping("/me") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    public void delete(@AuthenticationPrincipal AuthenticatedUser user) { poets.delete(current(user)); }
    private Poet current(AuthenticatedUser user) { if (user.poetId()==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Create a poet profile first."); return poets.findById(user.poetId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    public static PoetResponse response(Poet p) { String full=p.getFullName()==null ? p.getFirstName()+" "+p.getLastName() : p.getFullName(); Instant effective = p.getLegacySubmittedOn()==null ? p.getCreatedAt() : p.getLegacySubmittedOn().atStartOfDay().toInstant(ZoneOffset.UTC); String pen=p.getPenName()==null?full:p.getPenName(); return new PoetResponse(p.getId(),p.getEmail(),p.getFirstName(),p.getLastName(),full,pen,p.getBio(),effective,p.getUpdatedAt(),p.getAccountStatus()); }
    public record PoetRequest(@NotBlank @Size(max=100) String firstName, @NotBlank @Size(max=100) String lastName, @Size(max=100) String penName, @Size(max=500) String bio) {}
    public record PoetResponse(java.util.UUID poetId,String email,String firstName,String lastName,String fullName,String penName,String bio,Instant createdAt,Instant updatedAt,AccountStatus accountStatus) {}
    private record AuthPropertiesFacade(String adminEmail) {}
}
