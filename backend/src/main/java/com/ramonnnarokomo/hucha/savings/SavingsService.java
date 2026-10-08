package com.ramonnnarokomo.hucha.savings;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ramonnnarokomo.hucha.account.Account;
import com.ramonnnarokomo.hucha.account.AccountRepository;
import com.ramonnnarokomo.hucha.account.AccountService;
import com.ramonnnarokomo.hucha.account.AccountStatus;
import com.ramonnnarokomo.hucha.account.AccountType;
import com.ramonnnarokomo.hucha.account.AccountView;
import com.ramonnnarokomo.hucha.audit.AuditAction;
import com.ramonnnarokomo.hucha.audit.AuditService;
import com.ramonnnarokomo.hucha.ledger.Category;
import com.ramonnnarokomo.hucha.ledger.EntryType;
import com.ramonnnarokomo.hucha.ledger.LedgerEntryRequest;
import com.ramonnnarokomo.hucha.ledger.LedgerService;
import com.ramonnnarokomo.hucha.savings.SavingsDtos.MoveMoneyRequest;
import com.ramonnnarokomo.hucha.savings.SavingsDtos.SavingsRequest;
import com.ramonnnarokomo.hucha.shared.Amounts;
import com.ramonnnarokomo.hucha.shared.RequestInfo;
import com.ramonnnarokomo.hucha.shared.error.ApiException;
import com.ramonnnarokomo.hucha.shared.error.ErrorCode;

/** Huchas de ahorro: subcuentas con objetivo opcional. Mover dinero entre cuentas propias no tiene límites. */
@Service
public class SavingsService {

    static final int MAX_SAVINGS = 10;

    private final AccountRepository accounts;
    private final AccountService accountService;
    private final LedgerService ledger;
    private final AuditService audit;
    private final Clock clock;

    public SavingsService(AccountRepository accounts, AccountService accountService, LedgerService ledger,
                          AuditService audit, Clock clock) {
        this.accounts = accounts;
        this.accountService = accountService;
        this.ledger = ledger;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AccountView> list(UUID userId) {
        return accounts.findByOwnerIdAndTypeOrderByCreatedAtAsc(userId, AccountType.SAVINGS).stream()
                .filter(account -> account.getStatus() != AccountStatus.CLOSED)
                .map(AccountView::of)
                .toList();
    }

    @Transactional
    public AccountView create(UUID userId, SavingsRequest request, AuditService.Actor actor, RequestInfo info) {
        if (accounts.countByOwnerIdAndTypeAndStatusNot(userId, AccountType.SAVINGS, AccountStatus.CLOSED) >= MAX_SAVINGS) {
            throw ApiException.unprocessable(ErrorCode.INVALID_OPERATION, "Puedes tener como mucho " + MAX_SAVINGS + " huchas.");
        }
        Account savings = accountService.openSavings(userId, request.name(), goal(request.goalAmount()),
                request.icon() == null ? "piggy" : request.icon(), clock.instant());
        audit.record(actor, AuditAction.SAVINGS_CREATED, "ACCOUNT", savings.getId(), info, savings.getName());
        return AccountView.of(savings);
    }

    @Transactional
    public AccountView update(UUID userId, UUID savingsId, SavingsRequest request) {
        Account savings = ownedSavings(userId, savingsId);
        savings.updateSavings(request.name(), goal(request.goalAmount()),
                request.icon() == null ? savings.getIcon() : request.icon());
        return AccountView.of(savings);
    }

    /** Mete dinero en la hucha desde otra cuenta propia. */
    @Transactional
    public AccountView deposit(UUID userId, UUID savingsId, MoveMoneyRequest request) {
        Account savings = ownedSavings(userId, savingsId);
        Account source = accountService.getOwned(userId, request.accountId());
        move(source, savings, Amounts.positive(request.amount()), "Ahorro en «" + savings.getName() + "»");
        return AccountView.of(accounts.findById(savingsId).orElseThrow());
    }

    /** Saca dinero de la hucha hacia otra cuenta propia. */
    @Transactional
    public AccountView withdraw(UUID userId, UUID savingsId, MoveMoneyRequest request) {
        Account savings = ownedSavings(userId, savingsId);
        Account target = accountService.getOwned(userId, request.accountId());
        move(savings, target, Amounts.positive(request.amount()), "Retirada de «" + savings.getName() + "»");
        return AccountView.of(accounts.findById(savingsId).orElseThrow());
    }

    /** Solo se puede cerrar vacía: así nunca desaparece dinero con la cuenta. */
    @Transactional
    public void close(UUID userId, UUID savingsId, AuditService.Actor actor, RequestInfo info) {
        Account savings = ownedSavings(userId, savingsId);
        if (savings.getBalance().signum() != 0) {
            throw ApiException.unprocessable(ErrorCode.SAVINGS_NOT_EMPTY,
                    "Antes de cerrar la hucha, retira el dinero que tiene.");
        }
        savings.close();
        audit.record(actor, AuditAction.SAVINGS_CLOSED, "ACCOUNT", savings.getId(), info, savings.getName());
    }

    private void move(Account from, Account to, BigDecimal amount, String description) {
        if (from.getId().equals(to.getId())) {
            throw ApiException.unprocessable(ErrorCode.SAME_ACCOUNT, "La cuenta de origen y la de destino son la misma.");
        }
        Instant now = clock.instant();
        ledger.record(LedgerEntryRequest.builder(EntryType.SAVINGS_MOVE, description, now)
                .leg(from.getId(), amount.negate(), Category.SAVINGS, to.getName(), to.getIban(), description)
                .leg(to.getId(), amount, Category.SAVINGS, from.getName(), from.getIban(), description)
                .build());
    }

    private Account ownedSavings(UUID userId, UUID savingsId) {
        Account account = accountService.getOwned(userId, savingsId);
        if (account.getType() != AccountType.SAVINGS) {
            throw ApiException.notFound("Hucha no encontrada.");
        }
        return account;
    }

    private static BigDecimal goal(BigDecimal goalAmount) {
        return goalAmount == null ? null : Amounts.positive(goalAmount);
    }
}
