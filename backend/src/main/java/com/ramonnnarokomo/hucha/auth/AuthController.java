package com.ramonnnarokomo.hucha.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ramonnnarokomo.hucha.auth.AuthDtos.AuthResponse;
import com.ramonnnarokomo.hucha.auth.AuthDtos.LoginRequest;
import com.ramonnnarokomo.hucha.auth.AuthDtos.RegisterRequest;
import com.ramonnnarokomo.hucha.shared.RequestInfo;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final RefreshCookie refreshCookie;

    public AuthController(AuthService auth, RefreshCookie refreshCookie) {
        this.auth = auth;
        this.refreshCookie = refreshCookie;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        return withCookie(HttpStatus.CREATED, auth.register(request, RequestInfo.from(http)));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return withCookie(HttpStatus.OK, auth.login(request, RequestInfo.from(http)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@CookieValue(name = RefreshCookie.NAME, required = false) String token,
                                                HttpServletRequest http) {
        return withCookie(HttpStatus.OK, auth.refresh(token, RequestInfo.from(http)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = RefreshCookie.NAME, required = false) String token,
                                       HttpServletRequest http) {
        auth.logout(token, RequestInfo.from(http));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.clear().toString())
                .build();
    }

    private ResponseEntity<AuthResponse> withCookie(HttpStatus status, AuthService.LoginResult result) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie.create(result.refreshToken()).toString())
                .body(new AuthResponse(result.accessToken(), result.expiresIn(), result.sessionId(), result.user()));
    }
}
