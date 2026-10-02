package com.thinkordrinkpoetry.poem;

import com.thinkordrinkpoetry.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/poems")
public class PoemController {
    private final PoemService poemService;

    PoemController(PoemService poemService) {
        this.poemService = poemService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PoemResponse create(
            @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody PoemRequest request) {
        return PoemResponse.from(poemService.create(user, request));
    }

    @GetMapping
    public List<PoemResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return poemService.list(user).stream().map(PoemResponse::from).toList();
    }

    @GetMapping("/{poemId}")
    public PoemResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID poemId) {
        return PoemResponse.from(poemService.get(user, poemId));
    }

    @PutMapping("/{poemId}")
    public PoemResponse update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID poemId,
            @Valid @RequestBody PoemRequest request) {
        return PoemResponse.from(poemService.update(user, poemId, request));
    }

    @DeleteMapping("/{poemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID poemId) {
        poemService.delete(user, poemId);
    }
}
