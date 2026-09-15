package com.thinkordrinkpoetry.admin;

import com.thinkordrinkpoetry.auth.AuthenticatedUser;
import com.thinkordrinkpoetry.auth.UserSessionRepository;
import com.thinkordrinkpoetry.poem.Poem;
import com.thinkordrinkpoetry.poem.PoemController;
import com.thinkordrinkpoetry.poem.PoemRepository;
import com.thinkordrinkpoetry.poet.*;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/admin")
public class AdminController {
    private final PoetRepository poets; private final PoemRepository poems; private final UserSessionRepository sessions; private final AdminAuditEventRepository audit;
    public AdminController(PoetRepository poets,PoemRepository poems,UserSessionRepository sessions,AdminAuditEventRepository audit) { this.poets=poets;this.poems=poems;this.sessions=sessions;this.audit=audit; }
    @GetMapping("/poets") @Transactional(readOnly=true)
    public java.util.List<com.thinkordrinkpoetry.poet.PoetController.PoetResponse> poets(@AuthenticationPrincipal AuthenticatedUser user) { require(user); return poets.findAll().stream().map(com.thinkordrinkpoetry.poet.PoetController::response).toList(); }
    @GetMapping("/poems") @Transactional(readOnly=true)
    public java.util.List<PoemController.PoemResponse> poems(@AuthenticationPrincipal AuthenticatedUser user) { require(user); return poems.findAllByOrderByUpdatedAtDesc().stream().map(PoemController::response).toList(); }
    @GetMapping("/poets/{poetId}/poems") @Transactional(readOnly=true)
    public java.util.List<PoemController.PoemResponse> poemsByPoet(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID poetId) { require(user); if(!poets.existsById(poetId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND); return poems.findByPoetIdOrderByUpdatedAtDesc(poetId).stream().map(PoemController::response).toList(); }
    @PostMapping("/poets/{poetId}/lock") @Transactional
    public void lock(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID poetId,@RequestBody(required=false) Reason request) { Poet target=target(user,poetId); target.lock(request==null?null:request.reason()); sessions.deleteByPoetId(poetId); audit.save(new AdminAuditEvent(user.poetId(),"LOCK_POET","POET",poetId,request==null?null:request.reason())); }
    @PostMapping("/poets/{poetId}/unlock") @Transactional
    public void unlock(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID poetId) { Poet target=target(user,poetId); target.unlock(); audit.save(new AdminAuditEvent(user.poetId(),"UNLOCK_POET","POET",poetId,null)); }
    @PostMapping("/poets/{poetId}/activate") @Transactional
    public void activate(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID poetId) { Poet target=target(user,poetId); target.unlock(); audit.save(new AdminAuditEvent(user.poetId(),"ACTIVATE_LEGACY_POET","POET",poetId,null)); }
    @DeleteMapping("/poets/{poetId}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    public void deletePoet(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID poetId) { Poet target=target(user,poetId); if(target.getRole()==PoetRole.ADMIN && poets.countByRole(PoetRole.ADMIN)<=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"Cannot delete the last admin."); audit.save(new AdminAuditEvent(user.poetId(),"DELETE_POET","POET",poetId,null)); poets.delete(target); }
    @DeleteMapping("/poems/{poemId}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    public void deletePoem(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable UUID poemId) { require(user); Poem poem=poems.findById(poemId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); audit.save(new AdminAuditEvent(user.poetId(),"DELETE_POEM","POEM",poemId,null)); poems.delete(poem); }
    private Poet target(AuthenticatedUser user,UUID id) { require(user); if(user.poetId().equals(id)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Admins cannot change their own account here."); return poets.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }
    private void require(AuthenticatedUser user) { if(user==null||user.poetId()==null||poets.findById(user.poetId()).filter(p->p.getRole()==PoetRole.ADMIN&&p.getAccountStatus()==AccountStatus.ACTIVE).isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN); }
    public record Reason(@Size(max=1000) String reason) {}
}
