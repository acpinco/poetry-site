package com.thinkordrinkpoetry.poem;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PoemRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 100_000) String poem) {}
