package com.thinkordrinkpoetry.poem;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

public record PoemResponse(UUID poemId, UUID poetId, String title, String poem, Instant createdAt, Instant updatedAt) {

    public static PoemResponse from(Poem poem) {
        // 1999 poems are dated by their original submission, not by when they were imported.
        Instant createdAt = poem.getLegacySubmittedOn() == null
                ? poem.getCreatedAt()
                : poem.getLegacySubmittedOn().atStartOfDay().toInstant(ZoneOffset.UTC);
        return new PoemResponse(
                poem.getId(), poem.getPoetId(), poem.getTitle(), poem.getBody(), createdAt, poem.getUpdatedAt());
    }
}
