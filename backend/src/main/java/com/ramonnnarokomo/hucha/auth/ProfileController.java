package com.ramonnnarokomo.hucha.auth;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ramonnnarokomo.hucha.auth.AuthDtos.ChangePasswordRequest;
import com.ramonnnarokomo.hucha.auth.AuthDtos.SessionView;
import com.ramonnnarokomo.hucha.security.CurrentUser;
import com.ramonnnarokomo.hucha.shared.RequestInfo;
import com.ramonnnarokomo.hucha.user.UserSummary;

/**
 * La cookie de refresco solo se envía a /api/auth, así que el front indica su sesión actual
 * con la cabecera X-Session-Id (el id que devuelve el login), que no es ningún secreto.
 */
@RestController
@RequestMapping("/api/me")
public class ProfileController {

    private final ProfileService profile;

    public ProfileController(ProfileService profile) {
        this.profile = profile;
    }

    @GetMapping
    public UserSummary me(@AuthenticationPrincipal Jwt jwt) {
        return profile.me(CurrentUser.id(jwt));
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody ChangePasswordRequest request,
                                               @RequestHeader(name = "X-Session-Id", required = false) UUID session,
                                               HttpServletRequest http) {
        profile.changePassword(CurrentUser.id(jwt), request, session, RequestInfo.from(http));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    public List<SessionView> sessions(@AuthenticationPrincipal Jwt jwt,
                                      @RequestHeader(name = "X-Session-Id", required = false) UUID session) {
        return profile.sessions(CurrentUser.id(jwt), session);
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, HttpServletRequest http) {
        profile.revokeSession(CurrentUser.id(jwt), id, RequestInfo.from(http));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<Void> revokeOthers(@AuthenticationPrincipal Jwt jwt,
                                             @RequestHeader(name = "X-Session-Id", required = false) UUID session,
                                             HttpServletRequest http) {
        profile.revokeOtherSessions(CurrentUser.id(jwt), session, RequestInfo.from(http));
        return ResponseEntity.noContent().build();
    }
}
