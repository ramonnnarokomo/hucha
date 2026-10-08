package com.ramonnnarokomo.hucha.savings;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ramonnnarokomo.hucha.account.AccountView;
import com.ramonnnarokomo.hucha.savings.SavingsDtos.MoveMoneyRequest;
import com.ramonnnarokomo.hucha.savings.SavingsDtos.SavingsRequest;
import com.ramonnnarokomo.hucha.security.CurrentUser;
import com.ramonnnarokomo.hucha.shared.RequestInfo;

@RestController
@RequestMapping("/api/savings")
public class SavingsController {

    private final SavingsService savings;

    public SavingsController(SavingsService savings) {
        this.savings = savings;
    }

    @GetMapping
    public List<AccountView> list(@AuthenticationPrincipal Jwt jwt) {
        return savings.list(CurrentUser.id(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SavingsRequest request,
                              HttpServletRequest http) {
        return savings.create(CurrentUser.id(jwt), request, CurrentUser.actor(jwt), RequestInfo.from(http));
    }

    @PutMapping("/{id}")
    public AccountView update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                              @Valid @RequestBody SavingsRequest request) {
        return savings.update(CurrentUser.id(jwt), id, request);
    }

    @PostMapping("/{id}/deposit")
    public AccountView deposit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                               @Valid @RequestBody MoveMoneyRequest request) {
        return savings.deposit(CurrentUser.id(jwt), id, request);
    }

    @PostMapping("/{id}/withdraw")
    public AccountView withdraw(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                @Valid @RequestBody MoveMoneyRequest request) {
        return savings.withdraw(CurrentUser.id(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> close(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, HttpServletRequest http) {
        savings.close(CurrentUser.id(jwt), id, CurrentUser.actor(jwt), RequestInfo.from(http));
        return ResponseEntity.noContent().build();
    }
}
