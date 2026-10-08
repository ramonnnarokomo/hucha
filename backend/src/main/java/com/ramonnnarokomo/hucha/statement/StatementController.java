package com.ramonnnarokomo.hucha.statement;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ramonnnarokomo.hucha.ledger.Category;
import com.ramonnnarokomo.hucha.security.CurrentUser;
import com.ramonnnarokomo.hucha.shared.PageResponse;
import com.ramonnnarokomo.hucha.shared.RequestInfo;

@RestController
@RequestMapping("/api/accounts/{accountId}")
public class StatementController {

    private final StatementService statements;

    public StatementController(StatementService statements) {
        this.statements = statements;
    }

    @GetMapping("/movements")
    public PageResponse<MovementView> movements(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) MovementFilter.Direction direction,
            @RequestParam(required = false) Category category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return statements.movements(CurrentUser.id(jwt), accountId,
                new MovementFilter(from, to, q, direction, category), page, size);
    }

    /** Extracto de un mes: {@code ?month=2026-09&format=pdf} o {@code format=csv}. */
    @GetMapping("/statement")
    public ResponseEntity<byte[]> statement(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable UUID accountId,
                                            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                                            @RequestParam(defaultValue = "pdf") String format,
                                            HttpServletRequest http) {
        boolean csv = "csv".equalsIgnoreCase(format);
        byte[] body = csv
                ? statements.csv(CurrentUser.id(jwt), accountId, month, CurrentUser.actor(jwt), RequestInfo.from(http))
                : statements.pdf(CurrentUser.id(jwt), accountId, month, CurrentUser.actor(jwt), RequestInfo.from(http));
        String fileName = "extracto-" + month + (csv ? ".csv" : ".pdf");
        return ResponseEntity.ok()
                .contentType(csv ? new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8) : MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(body);
    }
}
