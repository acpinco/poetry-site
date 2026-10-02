package com.thinkordrinkpoetry.admin;

import com.thinkordrinkpoetry.auth.AuthenticatedUser;
import com.thinkordrinkpoetry.auth.UserSessionRepository;
import com.thinkordrinkpoetry.poem.Poem;
import com.thinkordrinkpoetry.poem.PoemRepository;
import com.thinkordrinkpoetry.poet.Poet;
import com.thinkordrinkpoetry.poet.PoetRepository;
import com.thinkordrinkpoetry.poet.PoetRole;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Site administration. Spring Security admits only active admins to /api/admin (see
 * SecurityConfiguration); every change is recorded in the audit log.
 */
@Service
class AdminService {
    private final PoetRepository poets;
    private final PoemRepository poems;
    private final UserSessionRepository sessions;
    private final AdminAuditEventRepository audit;

    private final EntityManager entityManager;

    AdminService(
            PoetRepository poets,
            PoemRepository poems,
            UserSessionRepository sessions,
            AdminAuditEventRepository audit,
            EntityManager entityManager) {
        this.poets = poets;
        this.poems = poems;
        this.sessions = sessions;
        this.audit = audit;
        this.entityManager = entityManager;
    }

    /** One page of poets in the requested order, plus totals for the whole site. */
    @Transactional(readOnly = true)
    PoetPage poets(AuthenticatedUser user, PoetSort sort, boolean descending, int page, int size) {
        requireAdmin(user);
        // The ORDER BY comes from a fixed enum, never from request text. Never-seen poets
        // sort last in both directions; id keeps pages stable when values tie.
        String sql = "select * from poet order by " + sort.column + (descending ? " desc" : " asc")
                + " nulls last, id limit :size offset :offset";
        @SuppressWarnings("unchecked")
        List<Poet> rows = entityManager
                .createNativeQuery(sql, Poet.class)
                .setParameter("size", size)
                .setParameter("offset", (long) page * size)
                .getResultList();
        Instant weekAgo = Instant.now().minus(Duration.ofDays(7));
        return new PoetPage(rows, poets.count(), poets.countByLastSeenAtAfter(weekAgo));
    }

    @Transactional(readOnly = true)
    List<Poem> poems(AuthenticatedUser user, int page, int size) {
        requireAdmin(user);
        return poems.findAllByOrderByUpdatedAtDescIdDesc(PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    List<Poem> poemsByPoet(AuthenticatedUser user, UUID poetId) {
        requireAdmin(user);
        if (!poets.existsById(poetId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return poems.findByPoetIdOrderByUpdatedAtDesc(poetId);
    }

    /** Locks the poet out and ends their sessions immediately. */
    @Transactional
    void lock(AuthenticatedUser user, UUID poetId, String reason) {
        Poet actor = requireAdmin(user);
        otherPoet(actor, poetId).lock(reason);
        sessions.deleteByPoetId(poetId);
        record(actor, "LOCK_POET", "POET", poetId, reason);
    }

    /**
     * Lets the poet sign in again. Used both to unlock a locked poet and to activate an unclaimed
     * 1999 account; the audit action records which one the admin meant.
     */
    @Transactional
    void restoreAccess(AuthenticatedUser user, UUID poetId, String auditAction) {
        Poet actor = requireAdmin(user);
        otherPoet(actor, poetId).unlock();
        record(actor, auditAction, "POET", poetId, null);
    }

    @Transactional
    void deletePoet(AuthenticatedUser user, UUID poetId) {
        Poet actor = requireAdmin(user);
        Poet target = otherPoet(actor, poetId);
        if (target.getRole() == PoetRole.ADMIN && poets.countByRole(PoetRole.ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete the last admin.");
        }
        record(actor, "DELETE_POET", "POET", poetId, null);
        poets.delete(target);
    }

    @Transactional
    void deletePoem(AuthenticatedUser user, UUID poemId) {
        Poet actor = requireAdmin(user);
        Poem poem = poems.findById(poemId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        record(actor, "DELETE_POEM", "POEM", poemId, null);
        poems.delete(poem);
    }

    /** The admin performing an action, for the audit log and the self-change check. */
    private Poet requireAdmin(AuthenticatedUser user) {
        return poets.findById(user.poetId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    }

    /** Admins manage other poets; changing their own account here could lock them out. */
    private Poet otherPoet(Poet actor, UUID poetId) {
        if (actor.getId().equals(poetId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Admins cannot change their own account here.");
        }
        return poets.findById(poetId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void record(Poet actor, String action, String targetType, UUID targetId, String reason) {
        audit.save(new AdminAuditEvent(actor.getId(), action, targetType, targetId, reason));
    }

    /** Columns the admin poet list can sort by, matching what the page displays. */
    enum PoetSort {
        NAME("lower(coalesce(pen_name, full_name))"),
        EMAIL("email"),
        STATUS("account_status"),
        // 1999 poets "joined" on their original submission date.
        JOINED("coalesce(legacy_submitted_on::timestamptz, created_at)"),
        LAST_SEEN("last_seen_at");

        private final String column;

        PoetSort(String column) {
            this.column = column;
        }
    }

    record PoetPage(List<Poet> poets, long totalPoets, long seenLastWeek) {}
}
