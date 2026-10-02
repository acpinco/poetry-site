package com.thinkordrinkpoetry.admin;

import com.thinkordrinkpoetry.auth.AuthenticatedUser;
import com.thinkordrinkpoetry.poem.PoemResponse;
import com.thinkordrinkpoetry.poet.PoetResponse;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService adminService;

    AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/poets")
    public List<PoetResponse> poets(@AuthenticationPrincipal AuthenticatedUser user) {
        return adminService.poets(user).stream().map(PoetResponse::from).toList();
    }

    @GetMapping("/poems")
    public List<PoemResponse> poems(@AuthenticationPrincipal AuthenticatedUser user) {
        return adminService.poems(user).stream().map(PoemResponse::from).toList();
    }

    @GetMapping("/poets/{poetId}/poems")
    public List<PoemResponse> poemsByPoet(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID poetId) {
        return adminService.poemsByPoet(user, poetId).stream().map(PoemResponse::from).toList();
    }

    @PostMapping("/poets/{poetId}/lock")
    public void lock(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID poetId,
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

    public record Reason(@Size(max = 1000) String reason) {}
}
