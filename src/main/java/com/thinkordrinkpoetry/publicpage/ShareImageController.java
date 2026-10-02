package com.thinkordrinkpoetry.publicpage;

import com.thinkordrinkpoetry.discovery.PoemCatalog;
import com.thinkordrinkpoetry.discovery.PoemCatalog.PoemDetail;
import com.thinkordrinkpoetry.discovery.PoemCatalog.PoetSummary;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/**
 * Preview images for shared links. Public and cacheable for a day, so Cloudflare serves repeat
 * requests and a shared link's picture catches up with an edited poem within a day.
 */
@Controller
class ShareImageController {
    private static final CacheControl CACHE =
            CacheControl.maxAge(Duration.ofDays(1)).cachePublic();

    private final PoemCatalog catalog;
    private final ShareImageRenderer renderer;

    ShareImageController(PoemCatalog catalog, ShareImageRenderer renderer) {
        this.catalog = catalog;
        this.renderer = renderer;
    }

    @GetMapping("/share.png")
    ResponseEntity<byte[]> site() {
        return png(renderer.site());
    }

    @GetMapping("/poems/{poemId}/share.png")
    ResponseEntity<byte[]> poem(@PathVariable UUID poemId) {
        PoemDetail poem = catalog.poem(poemId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return png(renderer.poem(poem.title(), poem.poetDisplayName(), poem.poem()));
    }

    @GetMapping("/poets/{poetId}/share.png")
    ResponseEntity<byte[]> poet(@PathVariable UUID poetId) {
        PoetSummary poet =
                catalog.publishedPoet(poetId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return png(renderer.poet(poet.displayName(), poet.poemCount(), poet.bio()));
    }

    private static ResponseEntity<byte[]> png(byte[] image) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CACHE)
                .body(image);
    }
}
