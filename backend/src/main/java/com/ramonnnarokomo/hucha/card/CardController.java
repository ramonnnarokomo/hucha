package com.ramonnnarokomo.hucha.card;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ramonnnarokomo.hucha.card.CardDtos.CardTransactionView;
import com.ramonnnarokomo.hucha.card.CardDtos.CardView;
import com.ramonnnarokomo.hucha.card.CardDtos.SimulatePurchaseRequest;
import com.ramonnnarokomo.hucha.card.CardDtos.UpdateCardRequest;
import com.ramonnnarokomo.hucha.security.CurrentUser;
import com.ramonnnarokomo.hucha.shared.PageResponse;
import com.ramonnnarokomo.hucha.shared.RequestInfo;

@RestController
@RequestMapping("/api/cards")
public class CardController {

    private final CardService cards;

    public CardController(CardService cards) {
        this.cards = cards;
    }

    @GetMapping
    public List<CardView> list(@AuthenticationPrincipal Jwt jwt) {
        return cards.list(CurrentUser.id(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CardView issue(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
        return cards.issue(CurrentUser.id(jwt), jwt.getClaimAsString("name"), CurrentUser.actor(jwt), RequestInfo.from(http));
    }

    @PatchMapping("/{id}")
    public CardView update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                           @Valid @RequestBody UpdateCardRequest request, HttpServletRequest http) {
        return cards.update(CurrentUser.id(jwt), id, request, CurrentUser.actor(jwt), RequestInfo.from(http));
    }

    @PostMapping("/{id}/cancel")
    public CardView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, HttpServletRequest http) {
        return cards.cancel(CurrentUser.id(jwt), id, CurrentUser.actor(jwt), RequestInfo.from(http));
    }

    @PostMapping("/{id}/simulate-purchase")
    public CardTransactionView simulatePurchase(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                @Valid @RequestBody SimulatePurchaseRequest request,
                                                HttpServletRequest http) {
        return cards.simulatePurchase(CurrentUser.id(jwt), id, request, CurrentUser.actor(jwt), RequestInfo.from(http));
    }

    @GetMapping("/{id}/transactions")
    public PageResponse<CardTransactionView> transactions(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return cards.transactions(CurrentUser.id(jwt), id, page, size);
    }
}
