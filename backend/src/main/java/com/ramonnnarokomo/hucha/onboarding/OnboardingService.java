package com.ramonnnarokomo.hucha.onboarding;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ramonnnarokomo.hucha.account.Account;
import com.ramonnnarokomo.hucha.account.AccountRepository;
import com.ramonnnarokomo.hucha.account.AccountService;
import com.ramonnnarokomo.hucha.audit.AuditService;
import com.ramonnnarokomo.hucha.card.CardService;
import com.ramonnnarokomo.hucha.ledger.Category;
import com.ramonnnarokomo.hucha.ledger.EntryType;
import com.ramonnnarokomo.hucha.ledger.LedgerEntryRequest;
import com.ramonnnarokomo.hucha.ledger.LedgerService;
import com.ramonnnarokomo.hucha.notification.NotificationService;
import com.ramonnnarokomo.hucha.notification.NotificationType;
import com.ramonnnarokomo.hucha.shared.HuchaProperties;
import com.ramonnnarokomo.hucha.shared.Money;
import com.ramonnnarokomo.hucha.shared.RequestInfo;
import com.ramonnnarokomo.hucha.user.User;

/** Alta de un cliente nuevo: cuenta corriente con IBAN, regalo de bienvenida y tarjeta virtual. */
@Service
public class OnboardingService {

    private final AccountService accountService;
    private final AccountRepository accounts;
    private final LedgerService ledger;
    private final CardService cards;
    private final NotificationService notifications;
    private final HuchaProperties properties;

    public OnboardingService(AccountService accountService, AccountRepository accounts, LedgerService ledger,
                             CardService cards, NotificationService notifications, HuchaProperties properties) {
        this.accountService = accountService;
        this.accounts = accounts;
        this.ledger = ledger;
        this.cards = cards;
        this.notifications = notifications;
        this.properties = properties;
    }

    @Transactional
    public Account onboard(User user, Instant now, RequestInfo request) {
        Account checking = accountService.openChecking(user.getId(), now);
        BigDecimal gift = creditWelcomeGift(checking, now);
        cards.issue(user.getId(), user.getFullName(), new AuditService.Actor(user.getId(), user.getEmail()), request);
        notifications.notify(user.getId(), NotificationType.WELCOME, "¡Bienvenido a Hucha!",
                gift.signum() > 0
                        ? "Te hemos regalado " + Money.format(gift) + " para que pruebes la app. Tu tarjeta virtual ya está activa."
                        : "Tu cuenta y tu tarjeta virtual ya están activas.");
        return checking;
    }

    /** Saldo inicial de regalo, sacado de los fondos propios del banco para que el libro mayor cuadre. */
    private BigDecimal creditWelcomeGift(Account account, Instant now) {
        BigDecimal gift = properties.accounts().welcomeGift();
        if (gift == null || gift.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal amount = gift.setScale(2);
        Account bank = accounts.findById(Account.BANK_FUNDS).orElseThrow();
        ledger.record(LedgerEntryRequest.builder(EntryType.WELCOME_GIFT, "Regalo de bienvenida", now)
                .reference(account.getId())
                .leg(Account.BANK_FUNDS, amount.negate(), null, account.getName(), account.getIban(), "Regalo de bienvenida")
                .leg(account.getId(), amount, Category.GIFT, "Hucha", bank.getIban(), "Regalo de bienvenida (demo)")
                .build());
        return amount;
    }
}
