package com.thinkordrinkpoetry.admin;

import com.thinkordrinkpoetry.auth.AuthenticatedUser;
import com.thinkordrinkpoetry.poem.PoemResponse;
import com.thinkordrinkpoetry.poet.PoetResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private static final int MAX_PAGE_SIZE = 200;

    private final AdminService adminService;

    AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /** Poets one page at a time; sort is name, email, status, joined, or lastSeen. */
    @GetMapping("/poets")
    public PoetsPageResponse poets(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "lastSeen") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        AdminService.PoetPage result =
                adminService.poets(user, poetSort(sort), "desc".equalsIgnoreCase(direction), page, size);
        return new PoetsPageResponse(
                result.poets().stream().map(PoetResponse::from).toList(),
                page,
                size,
                result.totalPoets(),
                result.seenLastWeek());
    }

    @GetMapping("/poems")
    public List<PoemResponse> poems(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return adminService.poems(user, page, size).stream()
                .map(PoemResponse::from)
                .toList();
    }

    @GetMapping("/poets/{poetId}/poems")
    public List<PoemResponse> poemsByPoet(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID poetId) {
        return adminService.poemsByPoet(user, poetId).stream()
                .map(PoemResponse::from)
                .toList();
    }

    @PostMapping("/poets/{poetId}/lock")
    public void lock(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID poetId,
            @Valid @RequestBody(required = false) Reason request) {
        adminService.lock(user, poetId, request == null ? null : request.reason());
    }

    @PostMapping("/poets/{poetId}/unlock")
    public void unlock(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID poetId) {
        adminService.restoreAccess(user, poetId, "UNLOCK_POET");
    }

    @PostMapping("/poets/{poetId}/activate")
    public void activate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID poetId) {
        adminService.restoreAccess(user, poetId, "ACTIVATE_LEGACY_POET");
    }

    @DeleteMapping("/poets/{poetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePoet(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID poetId) {
        adminService.deletePoet(user, poetId);
    }

    @DeleteMapping("/poems/{poemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePoem(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID poemId) {
        adminService.deletePoem(user, poemId);
    }

    private static AdminService.PoetSort poetSort(String sort) {
        return switch (sort) {
            case "name" -> AdminService.PoetSort.NAME;
            case "email" -> AdminService.PoetSort.EMAIL;
            case "status" -> AdminService.PoetSort.STATUS;
            case "joined" -> AdminService.PoetSort.JOINED;
            case "lastSeen" -> AdminService.PoetSort.LAST_SEEN;
            default ->
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Sort by name, email, status, joined, or lastSeen.");
        };
    }

    public record Reason(@Size(max = 1000) String reason) {}

    public record PoetsPageResponse(List<PoetResponse> poets, int page, int size, long totalPoets, long seenLastWeek) {}
}
