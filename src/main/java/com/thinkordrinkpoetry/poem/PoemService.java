package com.thinkordrinkpoetry.poem;

import com.thinkordrinkpoetry.auth.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** A poet's own poems. Every lookup is scoped to the signed-in poet, so poets can only touch their own. */
@Service
public class PoemService {
    private final PoemRepository poems;

    PoemService(PoemRepository poems) {
        this.poems = poems;
    }

    @Transactional
    public Poem create(AuthenticatedUser user, PoemRequest request) {
        // saveAndFlush so the database-assigned timestamps are read back for the response.
        return poems.saveAndFlush(new Poem(poetId(user), request.title().trim(), request.poem(), null));
    }

    @Transactional(readOnly = true)
    public List<Poem> list(AuthenticatedUser user) {
        return poems.findByPoetIdOrderByUpdatedAtDesc(poetId(user));
    }

    @Transactional(readOnly = true)
    public Poem get(AuthenticatedUser user, UUID poemId) {
        return owned(user, poemId);
    }

    @Transactional
    public Poem update(AuthenticatedUser user, UUID poemId, PoemRequest request) {
        Poem poem = owned(user, poemId);
        poem.update(request.title().trim(), request.poem());
        poems.flush();
        return poem;
    }

    @Transactional
    public void delete(AuthenticatedUser user, UUID poemId) {
        poems.delete(owned(user, poemId));
    }

    private Poem owned(AuthenticatedUser user, UUID poemId) {
        return poems.findByIdAndPoetId(poemId, poetId(user))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static UUID poetId(AuthenticatedUser user) {
        if (user.poetId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Create a poet profile first.");
        }
        return user.poetId();
    }
}
