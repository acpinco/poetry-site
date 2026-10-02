package com.thinkordrinkpoetry.publicpage;

import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Absolute URLs for public pages. Slugs are decorative: pages are looked up by id, so a renamed
 * poem keeps working at its old URL while the canonical link points search engines at the new one.
 * The frontend mirrors this slug rule in {@code poetry.ts}.
 */
@Component
public class PublicLinks {
    private final String siteUrl;

    PublicLinks(@Value("${app.auth.frontend-base-url}") String siteUrl) {
        this.siteUrl = siteUrl.replaceAll("/+$", "");
    }

    public String site() {
        return siteUrl;
    }

    public String home() {
        return siteUrl + "/home";
    }

    public String homeAt(UUID poemId) {
        return poemId == null ? home() : home() + "?poem=" + poemId;
    }

    public String signIn() {
        return siteUrl + "/sign-in";
    }

    public String poemOfTheDay() {
        return siteUrl + "/poem-of-the-day";
    }

    public String poem(UUID poemId, String title) {
        return siteUrl + "/poems/" + poemId + "/" + slugify(title);
    }

    public String poet(UUID poetId, String name) {
        return siteUrl + "/poets/" + poetId + "/" + slugify(name);
    }

    public String poetBio(UUID poetId, String name) {
        return poet(poetId, name) + "/bio";
    }

    static String slugify(String value) {
        String slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "poem" : slug;
    }
}
