package com.ramonnnarokomo.hucha.card;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ramonnnarokomo.hucha.account.Account;
import com.ramonnnarokomo.hucha.account.AccountRepository;
import com.ramonnnarokomo.hucha.account.AccountService;
import com.ramonnnarokomo.hucha.account.AccountType;
import com.ramonnnarokomo.hucha.audit.AuditAction;
import com.ramonnnarokomo.hucha.audit.AuditService;
import com.ramonnnarokomo.hucha.card.CardDtos.CardTransactionView;
import com.ramonnnarokomo.hucha.card.CardDtos.CardView;
import com.ramonnnarokomo.hucha.card.CardDtos.SimulatePurchaseRequest;
import com.ramonnnarokomo.hucha.card.CardDtos.UpdateCardRequest;
import com.ramonnnarokomo.hucha.ledger.EntryType;
import com.ramonnnarokomo.hucha.ledger.JournalEntry;
import com.ramonnnarokomo.hucha.ledger.LedgerEntryRequest;
import com.ramonnnarokomo.hucha.ledger.LedgerService;
import com.ramonnnarokomo.hucha.ledger.SpendingLimits;
import com.ramonnnarokomo.hucha.outbox.DomainEvents;
import com.ramonnnarokomo.hucha.outbox.OutboxWriter;
import com.ramonnnarokomo.hucha.shared.Amounts;
import com.ramonnnarokomo.hucha.shared.HuchaProperties;
import com.ramonnnarokomo.hucha.shared.Money;
import com.ramonnnarokomo.hucha.shared.PageResponse;
import com.ramonnnarokomo.hucha.shared.RequestInfo;
import com.ramonnnarokomo.hucha.shared.error.ApiException;
import com.ramonnnarokomo.hucha.shared.error.ErrorCode;

import io.micrometer.core.instrument.MeterRegistry;

@Service
public class CardService {

    static final int MAX_CARDS_PER_USER = 2;
    static final BigDecimal DEFAULT_MONTHLY_LIMIT = new BigDecimal("1500.00");

    private final CardRepository cards;
    private final CardTransactionRepository transactions;
    private final AccountRepository accounts;
    private final AccountService accountService;
    private final LedgerService ledger;
    private final SpendingLimits limits;
    private final OutboxWriter outbox;
    private final AuditService audit;
    private final HuchaProperties properties;
    private final MeterRegistry metrics;
    private final Clock clock;

    public CardService(CardRepository cards, CardTransactionRepository transactions, AccountRepository accounts,
                       AccountService accountService, LedgerService ledger, SpendingLimits limits, OutboxWriter outbox,
                       AuditService audit, HuchaProperties properties, MeterRegistry metrics, Clock clock) {
        this.cards = cards;
        this.transactions = transactions;
        this.accounts = accounts;
        this.accountService = accountService;
        this.ledger = ledger;
        this.limits = limits;
        this.outbox = outbox;
        this.audit = audit;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CardView> list(UUID userId) {
        return cards.findByOwnerIdOrderByCreatedAtAsc(userId).stream()
                .map(this::toView)
                .toList();
    }

    /** Emite una tarjeta virtual sobre la cuenta corriente. Máximo dos tarjetas vivas por cliente. */
    @Transactional
    public CardView issue(UUID userId, String holderName, AuditService.Actor actor, RequestInfo request) {
        if (cards.countByOwnerIdAndStatusNot(userId, CardStatus.CANCELLED) >= MAX_CARDS_PER_USER) {
            throw ApiException.unprocessable(ErrorCode.CARD_LIMIT_REACHED,
                    "Ya tienes " + MAX_CARDS_PER_USER + " tarjetas. Cancela una para pedir otra.");
        }
        Account account = accountService.mainAccount(userId);
        Card card = cards.save(new Card(account.getId(), userId, holderName, CardNumbers.generate(),
                DEFAULT_MONTHLY_LIMIT, clock.instant()));
        audit.record(actor, AuditAction.CARD_ISSUED, "CARD", card.getId(), request, "•••• " + card.getLast4());
        return toView(card);
    }

    @Transactional
    public CardView update(UUID userId, UUID cardId, UpdateCardRequest change, AuditService.Actor actor,
                           RequestInfo request) {
        Card card = owned(userId, cardId);
        if (card.getStatus() == CardStatus.CANCELLED) {
            throw ApiException.unprocessable(ErrorCode.INVALID_OPERATION, "La tarjeta está cancelada.");
        }
        if (change.frozen() != null) {
            if (change.frozen()) {
                card.freeze();
            } else {
                card.unfreeze();
            }
        }
        if (change.onlineEnabled() != null) {
            card.setOnlineEnabled(change.onlineEnabled());
        }
        if (change.monthlyLimit() != null) {
            card.setMonthlyLimit(Amounts.positive(change.monthlyLimit()));
        }
        audit.record(actor, AuditAction.CARD_UPDATED, "CARD", card.getId(), request,
                "estado=" + card.getStatus() + ", online=" + card.isOnlineEnabled() + ", límite=" + card.getMonthlyLimit());
        return toView(card);
    }

    @Transactional
    public CardView cancel(UUID userId, UUID cardId, AuditService.Actor actor, RequestInfo request) {
        Card card = owned(userId, cardId);
        card.cancel();
        audit.record(actor, AuditAction.CARD_CANCELLED, "CARD", card.getId(), request, "•••• " + card.getLast4());
        return toView(card);
    }

    /**
     * Autorización de un pago: estado de la tarjeta, compras online, límite mensual de la tarjeta,
     * límite diario de la cuenta y saldo. Un pago denegado no es un error: queda registrado con su motivo.
     */
    @Transactional
    public CardTransactionView simulatePurchase(UUID userId, UUID cardId, SimulatePurchaseRequest purchase,
                                                AuditService.Actor actor, RequestInfo request) {
        Card card = owned(userId, cardId);
        BigDecimal amount = Amounts.positive(purchase.amount());
        Instant now = clock.instant();
        Account account = accounts.findById(card.getAccountId()).orElseThrow();

        DeclineReason reason = decide(card, account, amount, purchase.online(), now);
        CardTransaction tx;
        if (reason == null) {
            JournalEntry entry = ledger.record(LedgerEntryRequest
                    .builder(EntryType.CARD_PAYMENT, "Pago con tarjeta en " + purchase.merchant(), now)
                    .reference(card.getId())
                    .leg(account.getId(), amount.negate(), purchase.category(), purchase.merchant(), null,
                            "Tarjeta •••• " + card.getLast4())
                    .leg(Account.CARD_SETTLEMENT, amount, purchase.category(), card.getHolderName(), account.getIban(),
                            purchase.merchant())
                    .build());
            tx = transactions.save(CardTransaction.approved(card.getId(), purchase.merchant(), purchase.category(),
                    amount, purchase.online(), entry.getId(), now));
        } else {
            tx = transactions.save(CardTransaction.declined(card.getId(), purchase.merchant(), purchase.category(),
                    amount, purchase.online(), reason, now));
            outbox.append(DomainEvents.CARD_PURCHASE_DECLINED, tx.getId(), new DomainEvents.CardPurchaseDeclined(
                    tx.getId(), userId, purchase.merchant(), amount, reason.description()));
            audit.record(actor, AuditAction.CARD_PURCHASE_DECLINED, "CARD", card.getId(), request,
                    purchase.merchant() + " " + Money.format(amount) + " (" + reason + ")");
        }
        metrics.counter("hucha.card.authorizations", "result", tx.getStatus().name().toLowerCase()).increment();
        return CardTransactionView.of(tx);
    }

    @Transactional(readOnly = true)
    public PageResponse<CardTransactionView> transactions(UUID userId, UUID cardId, int page, int size) {
        Card card = owned(userId, cardId);
        return PageResponse.of(
                transactions.findByCardIdOrderByCreatedAtDesc(card.getId(), PageRequest.of(page, Math.min(size, 50))),
                CardTransactionView::of);
    }

    private DeclineReason decide(Card card, Account account, BigDecimal amount, boolean online, Instant now) {
        if (card.getStatus() == CardStatus.CANCELLED) {
            return DeclineReason.CARD_CANCELLED;
        }
        if (card.getStatus() == CardStatus.FROZEN) {
            return DeclineReason.CARD_FROZEN;
        }
        if (online && !card.isOnlineEnabled()) {
            return DeclineReason.ONLINE_DISABLED;
        }
        if (!account.isActive() || account.getType() != AccountType.CHECKING) {
            return DeclineReason.ACCOUNT_NOT_ACTIVE;
        }
        if (spentThisMonth(card, now).add(amount).compareTo(card.getMonthlyLimit()) > 0) {
            return DeclineReason.MONTHLY_LIMIT;
        }
        if (!limits.allows(account, amount, now)) {
            return DeclineReason.DAILY_LIMIT;
        }
        if (account.getBalance().compareTo(amount) < 0) {
            return DeclineReason.INSUFFICIENT_FUNDS;
        }
        return null;
    }

    private BigDecimal spentThisMonth(Card card, Instant now) {
        Instant startOfMonth = YearMonth.from(now.atZone(properties.zone())).atDay(1)
                .atStartOfDay(properties.zone()).toInstant();
        return transactions.sumSince(card.getId(), CardTransactionStatus.APPROVED, startOfMonth);
    }

    private Card owned(UUID userId, UUID cardId) {
        return cards.findById(cardId)
                .filter(card -> card.isOwnedBy(userId))
                .orElseThrow(() -> ApiException.notFound("Tarjeta no encontrada."));
    }

    private CardView toView(Card card) {
        return new CardView(card.getId(), card.getAccountId(), card.getHolderName(), card.getLast4(),
                card.getExpiryMonth(), card.getExpiryYear(), card.getStatus(), card.isOnlineEnabled(),
                card.getMonthlyLimit(), spentThisMonth(card, clock.instant()), card.getCreatedAt());
    }
}
