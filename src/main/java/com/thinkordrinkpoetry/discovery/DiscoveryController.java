package com.thinkordrinkpoetry.discovery;

import com.thinkordrinkpoetry.discovery.PoemCatalog.PoemDetail;
import com.thinkordrinkpoetry.discovery.PoemCatalog.PoemSearchResult;
import com.thinkordrinkpoetry.discovery.PoemCatalog.PoemSummary;
import com.thinkordrinkpoetry.discovery.PoemCatalog.PoetSummary;
import com.thinkordrinkpoetry.discovery.PoemCatalog.RecentPoemSummary;
import com.thinkordrinkpoetry.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/discovery")
public class DiscoveryController {
    private final PoemCatalog catalog;
    private final PublicDiscoveryRateLimiter limiter;
    private final PoemOfTheDayService poemOfTheDay;
    private final ClientIpResolver clientIps;

    public DiscoveryController(
            PoemCatalog catalog,
            PublicDiscoveryRateLimiter limiter,
            PoemOfTheDayService poemOfTheDay,
            ClientIpResolver clientIps) {
        this.catalog = catalog;
        this.limiter = limiter;
        this.poemOfTheDay = poemOfTheDay;
        this.clientIps = clientIps;
    }

    /** Fallback landing data for when no Poem of the Day can be assigned yet. */
    @GetMapping("/home")
    public HomeResponse home(HttpServletRequest request) {
        limiter.check(clientIps.resolve(request), false);
        PoetSummary poet = catalog.randomPublishedPoet().orElseThrow(DiscoveryController::noPoemsYet);
        List<PoemSummary> poems = catalog.poemsByPoet(poet.poetId());
        // The random selection belongs exclusively to the persistent Poem of the Day.
        // The center reader starts with this poet's newest poem instead.
        return new HomeResponse(poet, poems, poem(poems.getFirst().poemId()));
    }

    @GetMapping("/poem-of-the-day")
    public PoemDetail poemOfTheDay(HttpServletRequest request) {
        limiter.check(clientIps.resolve(request), false);
        UUID poemId = poemOfTheDay.poemIdForToday();
        if (poemId == null) {
            throw noPoemsYet();
        }
        return poem(poemId);
    }

    @GetMapping("/poems/recent")
    public RecentPoemsResponse recentPoems(
            @RequestParam(defaultValue = "60") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            HttpServletRequest request) {
        limiter.check(clientIps.resolve(request), false);
        // Fetch one extra row to learn whether another page exists.
        List<RecentPoemSummary> poems = catalog.recentlyAdded(limit + 1, offset);
        boolean hasMore = poems.size() > limit;
        return new RecentPoemsResponse(hasMore ? poems.subList(0, limit) : poems, hasMore);
    }

    @GetMapping("/poets/{poetId}/poems")
    public PoetPoemsResponse poemsByPoet(@PathVariable UUID poetId, HttpServletRequest request) {
        limiter.check(clientIps.resolve(request), false);
        PoetSummary poet = catalog.poet(poetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Poet not found."));
        return new PoetPoemsResponse(poet, catalog.poemsByPoet(poetId));
    }

    @GetMapping("/poets")
    public List<PoetSummary> poets(HttpServletRequest request) {
        limiter.check(clientIps.resolve(request), false);
        return catalog.publishedPoets();
    }

    @GetMapping("/poems/{poemId}")
    public PoemDetail poem(@PathVariable UUID poemId, HttpServletRequest request) {
        limiter.check(clientIps.resolve(request), false);
        return poem(poemId);
    }

    @GetMapping("/search")
    public SearchResponse search(@RequestParam @Size(min = 2, max = 100) String q, HttpServletRequest request) {
        limiter.check(clientIps.resolve(request), true);
        return new SearchResponse(catalog.searchPoets(q), catalog.searchPoems(q));
    }

    private PoemDetail poem(UUID poemId) {
        return catalog.poem(poemId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static ResponseStatusException noPoemsYet() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No poems are available yet.");
    }

    public record RecentPoemsResponse(List<RecentPoemSummary> poems, boolean hasMore) {}

    public record HomeResponse(PoetSummary poet, List<PoemSummary> poems, PoemDetail selectedPoem) {}

    public record PoetPoemsResponse(PoetSummary poet, List<PoemSummary> poems) {}

    public record SearchResponse(List<PoetSummary> poets, List<PoemSearchResult> poems) {}
}
