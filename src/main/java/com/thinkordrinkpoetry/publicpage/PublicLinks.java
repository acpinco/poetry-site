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

    /** Opens the create-account form, or the new-poem editor for someone already signed in. */
    public String join() {
        return siteUrl + "/sign-in?join=1";
    }

    public String poemOfTheDay() {
        return siteUrl + "/poem-of-the-day";
    }

    public String poem(UUID poemId, String title) {
        return siteUrl + "/poems/" + poemId + "/" + slugify(title);
    }

    /** Redirects to a random poem other than the one being read. */
    public String anotherPoem(UUID currentPoemId) {
        return siteUrl + "/poems/random?from=" + currentPoemId;
    }

    public String siteImage() {
        return siteUrl + "/share.png";
    }

    public String poemImage(UUID poemId) {
        return siteUrl + "/poems/" + poemId + "/share.png";
    }

    public String poetImage(UUID poetId) {
        return siteUrl + "/poets/" + poetId + "/share.png";
    }

    public String poet(UUID poetId, String name) {
        return siteUrl + "/poets/" + poetId + "/" + slugify(name);
    }

    public String poetBio(UUID poetId, String name) {
        return poet(poetId, name) + "/bio";
    }

    static String slugify(String value) {
        String slug =
                value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "poem" : slug;
    }
}
