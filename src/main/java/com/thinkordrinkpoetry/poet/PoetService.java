package com.thinkordrinkpoetry.poet;

import com.thinkordrinkpoetry.auth.AuthProperties;
import com.thinkordrinkpoetry.auth.AuthService;
import com.thinkordrinkpoetry.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** A signed-in person's own poet profile. */
@Service
public class PoetService {
    private final PoetRepository poets;
    private final AuthService authService;
    private final String adminEmail;

    PoetService(PoetRepository poets, AuthService authService, AuthProperties authProperties) {
        this.poets = poets;
        this.authService = authService;
        this.adminEmail = authProperties.adminEmail();
    }

    /** Creates the profile for a verified email address and attaches it to the current session. */
    @Transactional
    public Poet create(AuthenticatedUser user, PoetRequest request) {
        if (user.poetId() != null || poets.findByEmail(user.email()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A poet profile already exists.");
        }
        String penName = request.normalizedPenName();
        if (penName != null && poets.penNameTaken(penName)) {
            throw penNameTaken();
        }
        PoetRole role = user.email().equalsIgnoreCase(adminEmail) ? PoetRole.ADMIN : PoetRole.USER;
        Poet poet = poets.saveAndFlush(new Poet(
                user.email(),
                request.trimmedFirstName(),
                request.trimmedLastName(),
                penName,
                request.bio(),
                null,
                AccountStatus.ACTIVE,
                role));
        authService.attachPoet(user.sessionId(), poet.getId());
        return poet;
    }

    @Transactional(readOnly = true)
    public Poet current(AuthenticatedUser user) {
        if (user.poetId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Create a poet profile first.");
        }
        return poets.findById(user.poetId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional
    public Poet update(AuthenticatedUser user, PoetRequest request) {
        Poet poet = current(user);
        String penName = request.normalizedPenName();
        if (penName != null && poets.penNameTakenByAnotherPoet(penName, poet.getId())) {
            throw penNameTaken();
        }
        poet.updateProfile(request.trimmedFirstName(), request.trimmedLastName(), penName, request.bio());
        // Flush so the database-maintained updated_at and full_name are read back for the response.
        poets.flush();
        return poet;
    }

    @Transactional
    public void delete(AuthenticatedUser user) {
        poets.delete(current(user));
    }

    private static ResponseStatusException penNameTaken() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "That pen name is already taken.");
    }
}
