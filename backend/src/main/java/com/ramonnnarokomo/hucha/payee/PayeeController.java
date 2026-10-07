package com.ramonnnarokomo.hucha.payee;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ramonnnarokomo.hucha.payee.PayeeDtos.CreatePayeeRequest;
import com.ramonnnarokomo.hucha.payee.PayeeDtos.IbanCheck;
import com.ramonnnarokomo.hucha.payee.PayeeDtos.PayeeView;
import com.ramonnnarokomo.hucha.payee.PayeeDtos.UpdatePayeeRequest;
import com.ramonnnarokomo.hucha.security.CurrentUser;
import com.ramonnnarokomo.hucha.shared.RequestInfo;

@RestController
@RequestMapping("/api/payees")
public class PayeeController {

    private final PayeeService payees;

    public PayeeController(PayeeService payees) {
        this.payees = payees;
    }

    @GetMapping
    public List<PayeeView> list(@AuthenticationPrincipal Jwt jwt) {
        return payees.list(CurrentUser.id(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PayeeView add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreatePayeeRequest request,
                         HttpServletRequest http) {
        return payees.add(CurrentUser.id(jwt), request, CurrentUser.actor(jwt), RequestInfo.from(http));
    }

    @PutMapping("/{id}")
    public PayeeView update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                            @Valid @RequestBody UpdatePayeeRequest request) {
        return payees.update(CurrentUser.id(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, HttpServletRequest http) {
        payees.delete(CurrentUser.id(jwt), id, CurrentUser.actor(jwt), RequestInfo.from(http));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/check-iban")
    public IbanCheck checkIban(@AuthenticationPrincipal Jwt jwt, @RequestParam String iban) {
        return payees.check(CurrentUser.id(jwt), iban);
    }
}
