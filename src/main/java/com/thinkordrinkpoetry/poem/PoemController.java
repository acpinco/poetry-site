package com.thinkordrinkpoetry.poem;

import com.thinkordrinkpoetry.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/poems")
public class PoemController {
    private final PoemRepository poems;
    public PoemController(PoemRepository poems) { this.poems=poems; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
    public PoemResponse create(@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody PoemRequest request) { UUID poetId=poetId(user); return response(poems.save(new Poem(poetId, request.title().trim(), request.poem(), null))); }
    @GetMapping @Transactional(readOnly=true)
    public List<PoemResponse> list(@AuthenticationPrincipal AuthenticatedUser user) { return poems.findByPoetIdOrderByUpdatedAtDesc(poetId(user)).stream().map(PoemController::response).toList(); }
    @GetMapping("/{poemId}") @Transactional(readOnly=true)
    public PoemResponse get(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID poemId) { return response(owned(poetId(user),poemId)); }
    @PutMapping("/{poemId}") @Transactional
    public PoemResponse update(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID poemId,@Valid @RequestBody PoemRequest request) { Poem poem=owned(poetId(user),poemId); poem.update(request.title().trim(),request.poem()); return response(poem); }
    @DeleteMapping("/{poemId}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    public void delete(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID poemId) { poems.delete(owned(poetId(user),poemId)); }
    private Poem owned(UUID poetId,UUID poemId) { return poems.findByIdAndPoetId(poemId,poetId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }
    private UUID poetId(AuthenticatedUser user) { if (user.poetId()==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"Create a poet profile first."); return user.poetId(); }
    public static PoemResponse response(Poem p) { Instant created=p.getLegacySubmittedOn()==null?p.getCreatedAt():p.getLegacySubmittedOn().atStartOfDay().toInstant(ZoneOffset.UTC); return new PoemResponse(p.getId(),p.getPoetId(),p.getTitle(),p.getBody(),created,p.getUpdatedAt()); }
    public record PoemRequest(@NotBlank @Size(max=200) String title,@NotBlank @Size(max=100000) String poem) {}
    public record PoemResponse(UUID poemId,UUID poetId,String title,String poem,Instant createdAt,Instant updatedAt) {}
}
