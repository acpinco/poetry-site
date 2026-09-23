package com.thinkordrinkpoetry.contact;

import com.thinkordrinkpoetry.auth.AuthenticatedUser;
import com.thinkordrinkpoetry.poet.Poet;
import com.thinkordrinkpoetry.poet.PoetRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/contact")
public class ContactController {
    private final PoetRepository poets;
    private final ContactMailer mailer;

    ContactController(PoetRepository poets, ContactMailer mailer) {
        this.poets = poets;
        this.mailer = mailer;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void send(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody ContactRequest request) {
        if (user == null || user.poetId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Create a poet profile before sending a message.");
        }
        Poet poet = poets.findById(user.poetId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        mailer.send(poet, request.message());
    }

    public record ContactRequest(@NotBlank @Size(max = 10_000) String message) {}
}
