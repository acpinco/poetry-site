package com.thinkordrinkpoetry.poet;

import com.thinkordrinkpoetry.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/poets")
public class PoetController {
    private final PoetService poetService;

    PoetController(PoetService poetService) {
        this.poetService = poetService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PoetResponse create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody PoetRequest request) {
        return PoetResponse.from(poetService.create(user, request));
    }

    @GetMapping("/me")
    public PoetResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return PoetResponse.from(poetService.current(user));
    }

    @PutMapping("/me")
    public PoetResponse update(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody PoetRequest request) {
        return PoetResponse.from(poetService.update(user, request));
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user) {
        poetService.delete(user);
    }
}
