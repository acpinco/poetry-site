package com.thinkordrinkpoetry.admin;

import com.thinkordrinkpoetry.auth.AuthenticatedUser;
import com.thinkordrinkpoetry.auth.UserSessionRepository;
import com.thinkordrinkpoetry.poem.Poem;
import com.thinkordrinkpoetry.poem.PoemController;
import com.thinkordrinkpoetry.poem.PoemRepository;
import com.thinkordrinkpoetry.poet.AccountStatus;
import com.thinkordrinkpoetry.poet.Poet;
import com.thinkordrinkpoetry.poet.PoetController;
import com.thinkordrinkpoetry.poet.PoetRepository;
import com.thinkordrinkpoetry.poet.PoetRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final PoetRepository poets;
    private final PoemRepository poems;
    private final UserSessionRepository sessions;
    private final AdminAuditEventRepository audit;

    public AdminController(
            PoetRepository poets,
            PoemRepository poems,
            UserSessionRepository sessions,
            AdminAuditEventRepository audit) {
        this.poets = poets;
        this.poems = poems;
        this.sessions = sessions;
        this.audit = audit;
    }

    @GetMapping("/poets")
    @Transactional(readOnly = true)
    public List<PoetController.PoetResponse> poets(@AuthenticationPrincipal AuthenticatedUser user) {
        requireAdmin(user);
        return poets.findAll().stream().map(PoetController::response).toList();
    }

    @GetMapping("/poems")
    @Transactional(readOnly = true)
    public List<PoemController.PoemResponse> poems(@AuthenticationPrincipal AuthenticatedUser user) {
        requireAdmin(user);
        return poems.findAllByOrderByUpdatedAtDesc().stream().map(PoemController::response).toList();
    }

    @GetMapping("/poets/{poetId}/poems")
    @Transactional(readOnly = true)
    public List<PoemController.PoemResponse> poemsByPoet(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID poetId) {
        requireAdmin(user);
        if (!poets.existsById(poetId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return poems.findByPoetIdOrderByUpdatedAtDesc(poetId).stream()
                .map(PoemController::response)
                .toList();
    }

    @PostMapping("/poets/{poetId}/lock")
    @Transactional
    public void lock(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID poetId,
            @Valid @RequestBody(required = false) Reason request) {
        Poet actor = requireAdmin(user);
        Poet target = targetPoet(actor, poetId);
        String reason = request == null ? null : request.reason();
        target.lock(reason);
        sessions.deleteByPoetId(poetId);
        audit.save(new AdminAuditEvent(actor.getId(), "LOCK_POET", "POET", poetId, reason));
    }

    @PostMapping("/poets/{poetId}/unlock")
    @Transactional
    public void unlock(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID poetId) {
        Poet actor = requireAdmin(user);
        targetPoet(actor, poetId).unlock();
        audit.save(new AdminAuditEvent(actor.getId(), "UNLOCK_POET", "POET", poetId, null));
    }

    @PostMapping("/poets/{poetId}/activate")
    @Transactional
    public void activate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID poetId) {
        Poet actor = requireAdmin(user);
        targetPoet(actor, poetId).unlock();
        audit.save(new AdminAuditEvent(actor.getId(), "ACTIVATE_LEGACY_POET", "POET", poetId, null));
    }

    @DeleteMapping("/poets/{poetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deletePoet(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID poetId) {
        Poet actor = requireAdmin(user);
        Poet target = targetPoet(actor, poetId);
        if (target.getRole() == PoetRole.ADMIN && poets.countByRole(PoetRole.ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete the last admin.");
        }
        audit.save(new AdminAuditEvent(actor.getId(), "DELETE_POET", "POET", poetId, null));
        poets.delete(target);
    }

    @DeleteMapping("/poems/{poemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deletePoem(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID poemId) {
        Poet actor = requireAdmin(user);
        Poem poem = poems.findById(poemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        audit.save(new AdminAuditEvent(actor.getId(), "DELETE_POEM", "POEM", poemId, null));
        poems.delete(poem);
    }

    private Poet targetPoet(Poet actor, UUID targetId) {
        if (actor.getId().equals(targetId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Admins cannot change their own account here.");
        }
        return poets.findById(targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private Poet requireAdmin(AuthenticatedUser user) {
        if (user == null || user.poetId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return poets.findById(user.poetId())
                .filter(poet -> poet.getRole() == PoetRole.ADMIN)
                .filter(poet -> poet.getAccountStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    }

    public record Reason(@Size(max = 1000) String reason) {}
}
