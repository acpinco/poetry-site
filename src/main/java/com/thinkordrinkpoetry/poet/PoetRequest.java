package com.thinkordrinkpoetry.poet;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Profile fields a poet can set. Blank optional fields are stored as null. */
public record PoetRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 100) String penName,
        @Size(max = 500) String bio) {

    String trimmedFirstName() {
        return firstName.trim();
    }

    String trimmedLastName() {
        return lastName.trim();
    }

    String normalizedPenName() {
        return penName == null || penName.isBlank() ? null : penName.trim();
    }
}
