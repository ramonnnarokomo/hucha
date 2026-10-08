package com.ramonnnarokomo.hucha.admin;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ramonnnarokomo.hucha.account.AccountView;
import com.ramonnnarokomo.hucha.admin.AdminDtos.Overview;
import com.ramonnnarokomo.hucha.admin.AdminDtos.UserDetail;
import com.ramonnnarokomo.hucha.admin.AdminDtos.UserRow;
import com.ramonnnarokomo.hucha.audit.AuditAction;
import com.ramonnnarokomo.hucha.audit.AuditView;
import com.ramonnnarokomo.hucha.security.CurrentUser;
import com.ramonnnarokomo.hucha.shared.PageResponse;
import com.ramonnnarokomo.hucha.shared.RequestInfo;

/** Solo administradores: lo comprueba la cadena de seguridad por URL y, además, la anotación. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService admin;

    public AdminController(AdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/overview")
    public Overview overview() {
        return admin.overview();
    }

    @GetMapping("/users")
    public PageResponse<UserRow> users(@RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return admin.users(q, page, size);
    }

    @GetMapping("/users/{id}")
    public UserDetail user(@PathVariable UUID id) {
        return admin.user(id);
    }

    @PostMapping("/users/{id}/block")
    public UserRow block(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, HttpServletRequest http) {
        return admin.setBlocked(id, true, CurrentUser.actor(jwt), RequestInfo.from(http));
    }

    @PostMapping("/users/{id}/unblock")
    public UserRow unblock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, HttpServletRequest http) {
        return admin.setBlocked(id, false, CurrentUser.actor(jwt), RequestInfo.from(http));
    }

    @PostMapping("/accounts/{id}/freeze")
    public AccountView freeze(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, HttpServletRequest http) {
        return admin.setFrozen(id, true, CurrentUser.actor(jwt), RequestInfo.from(http));
    }

    @PostMapping("/accounts/{id}/unfreeze")
    public AccountView unfreeze(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, HttpServletRequest http) {
        return admin.setFrozen(id, false, CurrentUser.actor(jwt), RequestInfo.from(http));
    }

    @GetMapping("/audit")
    public PageResponse<AuditView> audit(@RequestParam(required = false) AuditAction action,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "30") int size) {
        return admin.audit(action, page, size);
    }
}
