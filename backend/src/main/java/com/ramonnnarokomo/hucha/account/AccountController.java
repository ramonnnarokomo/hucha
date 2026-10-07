package com.ramonnnarokomo.hucha.account;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ramonnnarokomo.hucha.security.CurrentUser;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public List<AccountView> list(@AuthenticationPrincipal Jwt jwt) {
        return accounts.listForUser(CurrentUser.id(jwt));
    }

    @GetMapping("/{id}")
    public AccountView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return AccountView.of(accounts.getOwned(CurrentUser.id(jwt), id));
    }
}
