package com.ramonnnarokomo.hucha.statement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ramonnnarokomo.hucha.account.Account;
import com.ramonnnarokomo.hucha.account.AccountService;
import com.ramonnnarokomo.hucha.audit.AuditAction;
import com.ramonnnarokomo.hucha.audit.AuditService;
import com.ramonnnarokomo.hucha.ledger.Posting;
import com.ramonnnarokomo.hucha.ledger.PostingRepository;
import com.ramonnnarokomo.hucha.shared.Amounts;
import com.ramonnnarokomo.hucha.shared.HuchaProperties;
import com.ramonnnarokomo.hucha.shared.PageResponse;
import com.ramonnnarokomo.hucha.shared.RequestInfo;
import com.ramonnnarokomo.hucha.user.UserRepository;

@Service
public class StatementService {

    private final AccountService accounts;
    private final PostingRepository postings;
    private final UserRepository users;
    private final StatementCsv csv;
    private final StatementPdf pdf;
    private final AuditService audit;
    private final HuchaProperties properties;

    public StatementService(AccountService accounts, PostingRepository postings, UserRepository users,
                            StatementCsv csv, StatementPdf pdf, AuditService audit, HuchaProperties properties) {
        this.accounts = accounts;
        this.postings = postings;
        this.users = users;
        this.csv = csv;
        this.pdf = pdf;
        this.audit = audit;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public PageResponse<MovementView> movements(UUID userId, UUID accountId, MovementFilter filter, int page, int size) {
        Account account = accounts.getOwned(userId, accountId);
        Sort newestFirst = Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));
        return PageResponse.of(
                postings.findAll(MovementSpecifications.of(account.getId(), filter, properties.zone()),
                        PageRequest.of(page, Math.min(size, 100), newestFirst)),
                MovementView::of);
    }

    @Transactional(readOnly = true)
    public Statement monthly(UUID userId, UUID accountId, YearMonth month) {
        Account account = accounts.getOwned(userId, accountId);
        Instant from = month.atDay(1).atStartOfDay(properties.zone()).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(properties.zone()).toInstant();
        List<Posting> lines = postings
                .findByAccountIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAscIdAsc(
                        account.getId(), from, to);
        BigDecimal opening = postings.findFirstByAccountIdAndOccurredAtLessThanOrderByOccurredAtDescIdDesc(account.getId(), from)
                .map(Posting::getBalanceAfter)
                .orElse(Amounts.ZERO);
        BigDecimal income = lines.stream().map(Posting::getAmount).filter(a -> a.signum() > 0)
                .reduce(Amounts.ZERO, BigDecimal::add);
        BigDecimal expenses = lines.stream().map(Posting::getAmount).filter(a -> a.signum() < 0)
                .reduce(Amounts.ZERO, BigDecimal::add).negate();
        String holder = users.findById(userId).map(user -> user.getFullName()).orElse("");
        return new Statement(account, holder, month, opening, opening.add(income).subtract(expenses), income, expenses,
                lines.stream().map(MovementView::of).toList());
    }

    @Transactional
    public byte[] csv(UUID userId, UUID accountId, YearMonth month, AuditService.Actor actor, RequestInfo info) {
        Statement statement = monthly(userId, accountId, month);
        audit.record(actor, AuditAction.STATEMENT_DOWNLOADED, "ACCOUNT", accountId, info, month + " CSV");
        return csv.render(statement, properties.zone());
    }

    @Transactional
    public byte[] pdf(UUID userId, UUID accountId, YearMonth month, AuditService.Actor actor, RequestInfo info) {
        Statement statement = monthly(userId, accountId, month);
        audit.record(actor, AuditAction.STATEMENT_DOWNLOADED, "ACCOUNT", accountId, info, month + " PDF");
        return pdf.render(statement, properties.zone());
    }

    public record Statement(Account account, String holderName, YearMonth month, BigDecimal openingBalance,
                            BigDecimal closingBalance, BigDecimal totalIncome, BigDecimal totalExpenses,
                            List<MovementView> movements) {
    }
}
