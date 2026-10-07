package com.ramonnnarokomo.hucha.transfer;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ramonnnarokomo.hucha.security.CurrentUser;
import com.ramonnnarokomo.hucha.shared.PageResponse;
import com.ramonnnarokomo.hucha.shared.RequestInfo;
import com.ramonnnarokomo.hucha.transfer.TransferDtos.ConfirmTransferRequest;
import com.ramonnnarokomo.hucha.transfer.TransferDtos.CreateTransferRequest;
import com.ramonnnarokomo.hucha.transfer.TransferDtos.TransferView;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transfers;

    public TransferController(TransferService transfers) {
        this.transfers = transfers;
    }

    /**
     * 201 si se ha ejecutado, 202 si espera el código de verificación y 200 si es una repetición
     * de una petición anterior con la misma Idempotency-Key (cabecera Idempotent-Replayed: true).
     */
    @PostMapping
    public ResponseEntity<TransferView> create(@AuthenticationPrincipal Jwt jwt,
                                               @RequestHeader(name = "Idempotency-Key", required = false) String key,
                                               @Valid @RequestBody CreateTransferRequest request,
                                               HttpServletRequest http) {
        UUID userId = CurrentUser.id(jwt);
        TransferService.Outcome outcome;
        try {
            outcome = transfers.create(userId, key, request, CurrentUser.actor(jwt), RequestInfo.from(http));
        } catch (DataIntegrityViolationException race) {
            outcome = transfers.replayAfterRace(userId, key, request);
        }
        HttpStatus status;
        if (outcome.replayed()) {
            status = HttpStatus.OK;
        } else if (outcome.transfer().status() == TransferStatus.PENDING_SCA) {
            status = HttpStatus.ACCEPTED;
        } else {
            status = HttpStatus.CREATED;
        }
        return ResponseEntity.status(status)
                .header("Idempotent-Replayed", String.valueOf(outcome.replayed()))
                .body(outcome.transfer());
    }

    @PostMapping("/{id}/confirm")
    public TransferView confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                @Valid @RequestBody ConfirmTransferRequest request, HttpServletRequest http) {
        return transfers.confirm(CurrentUser.id(jwt), id, request.code(), CurrentUser.actor(jwt), RequestInfo.from(http));
    }

    @PostMapping("/{id}/cancel")
    public TransferView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, HttpServletRequest http) {
        return transfers.cancel(CurrentUser.id(jwt), id, CurrentUser.actor(jwt), RequestInfo.from(http));
    }

    @GetMapping
    public PageResponse<TransferView> list(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return transfers.list(CurrentUser.id(jwt), page, size);
    }

    @GetMapping("/{id}")
    public TransferView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return transfers.get(CurrentUser.id(jwt), id);
    }
}
