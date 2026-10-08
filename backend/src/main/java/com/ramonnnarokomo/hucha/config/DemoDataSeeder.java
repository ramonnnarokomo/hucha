package com.ramonnnarokomo.hucha.config;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.ramonnnarokomo.hucha.account.Account;
import com.ramonnnarokomo.hucha.account.AccountRepository;
import com.ramonnnarokomo.hucha.account.Iban;
import com.ramonnnarokomo.hucha.account.IbanGenerator;
import com.ramonnnarokomo.hucha.audit.AuditAction;
import com.ramonnnarokomo.hucha.audit.AuditEvent;
import com.ramonnnarokomo.hucha.audit.AuditEventRepository;
import com.ramonnnarokomo.hucha.card.Card;
import com.ramonnnarokomo.hucha.card.CardNumbers;
import com.ramonnnarokomo.hucha.card.CardRepository;
import com.ramonnnarokomo.hucha.card.CardTransaction;
import com.ramonnnarokomo.hucha.card.CardTransactionRepository;
import com.ramonnnarokomo.hucha.card.DeclineReason;
import com.ramonnnarokomo.hucha.ledger.Category;
import com.ramonnnarokomo.hucha.ledger.EntryType;
import com.ramonnnarokomo.hucha.ledger.JournalEntry;
import com.ramonnnarokomo.hucha.ledger.LedgerEntryRequest;
import com.ramonnnarokomo.hucha.ledger.LedgerService;
import com.ramonnnarokomo.hucha.notification.Notification;
import com.ramonnnarokomo.hucha.notification.NotificationRepository;
import com.ramonnnarokomo.hucha.notification.NotificationType;
import com.ramonnnarokomo.hucha.payee.Payee;
import com.ramonnnarokomo.hucha.payee.PayeeRepository;
import com.ramonnnarokomo.hucha.shared.HuchaProperties;
import com.ramonnnarokomo.hucha.shared.Money;
import com.ramonnnarokomo.hucha.transfer.Transfer;
import com.ramonnnarokomo.hucha.transfer.TransferRepository;
import com.ramonnnarokomo.hucha.user.Role;
import com.ramonnnarokomo.hucha.user.User;
import com.ramonnnarokomo.hucha.user.UserRepository;

/**
 * Datos de demostración: dos clientes con seis meses de historia (nóminas, alquiler, compras con tarjeta,
 * huchas y transferencias entre ellos) y un administrador. Todo pasa por el libro mayor, así que los
 * saldos cuadran igual que con datos reales. Empresas y comercios son inventados.
 */
@Component
@ConditionalOnProperty(name = "hucha.demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "Demo1234";
    public static final String ADMIN_EMAIL = "admin@hucha.dev";
    public static final String LUCIA_EMAIL = "lucia@hucha.dev";
    public static final String MARCOS_EMAIL = "marcos@hucha.dev";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final Locale SPANISH = Locale.forLanguageTag("es-ES");
    private static final String LANDLORD_IBAN = Iban.spanish("9990", "0102", "0000123456");
    private static final String GYM_IBAN = Iban.spanish("9991", "0007", "0000654321");

    private final UserRepository users;
    private final AccountRepository accounts;
    private final IbanGenerator ibans;
    private final LedgerService ledger;
    private final CardRepository cards;
    private final CardTransactionRepository cardTransactions;
    private final TransferRepository transfers;
    private final PayeeRepository payees;
    private final NotificationRepository notifications;
    private final AuditEventRepository auditEvents;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transaction;
    private final HuchaProperties properties;
    private final Clock clock;

    private final Random random = new Random(42);
    private final List<Event> events = new ArrayList<>();

    public DemoDataSeeder(UserRepository users, AccountRepository accounts, IbanGenerator ibans, LedgerService ledger,
                          CardRepository cards, CardTransactionRepository cardTransactions, TransferRepository transfers,
                          PayeeRepository payees, NotificationRepository notifications,
                          AuditEventRepository auditEvents, PasswordEncoder passwordEncoder,
                          TransactionTemplate transaction, HuchaProperties properties, Clock clock) {
        this.users = users;
        this.accounts = accounts;
        this.ibans = ibans;
        this.ledger = ledger;
        this.cards = cards;
        this.cardTransactions = cardTransactions;
        this.transfers = transfers;
        this.payees = payees;
        this.notifications = notifications;
        this.auditEvents = auditEvents;
        this.passwordEncoder = passwordEncoder;
        this.transaction = transaction;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByEmail(ADMIN_EMAIL)) {
            return;
        }
        transaction.executeWithoutResult(status -> seed());
        log.info("Datos de demo creados: {} / {} / {} (contraseña {})", LUCIA_EMAIL, MARCOS_EMAIL, ADMIN_EMAIL,
                DEMO_PASSWORD);
    }

    private void seed() {
        ZoneId zone = properties.zone();
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);
        LocalDate start = YearMonth.from(today).minusMonths(5).atDay(1);
        Instant startInstant = start.atTime(9, 0).atZone(zone).toInstant();
        String hash = passwordEncoder.encode(DEMO_PASSWORD);

        users.save(new User(ADMIN_EMAIL, "Equipo Hucha", hash, Role.ADMIN, startInstant));
        User lucia = users.save(new User(LUCIA_EMAIL, "Lucía Fernández", hash, Role.CUSTOMER, startInstant));
        User marcos = users.save(new User(MARCOS_EMAIL, "Marcos Ruiz", hash, Role.CUSTOMER, startInstant));

        Account luciaMain = accounts.save(Account.checking(ibans.next(), lucia.getId(),
                properties.accounts().defaultDailyLimit(), startInstant));
        Account marcosMain = accounts.save(Account.checking(ibans.next(), marcos.getId(),
                properties.accounts().defaultDailyLimit(), startInstant));
        Account japan = accounts.save(Account.savings(ibans.next(), lucia.getId(), "Viaje a Japón",
                new BigDecimal("3000.00"), "plane", startInstant));
        Account emergency = accounts.save(Account.savings(ibans.next(), lucia.getId(), "Fondo de emergencia",
                new BigDecimal("6000.00"), "umbrella", startInstant));
        Account bike = accounts.save(Account.savings(ibans.next(), marcos.getId(), "Bici nueva",
                new BigDecimal("1200.00"), "star", startInstant));

        Card luciaCard = cards.save(new Card(luciaMain.getId(), lucia.getId(), lucia.getFullName(),
                CardNumbers.generate(), new BigDecimal("1500.00"), startInstant));
        Card marcosCard = cards.save(new Card(marcosMain.getId(), marcos.getId(), marcos.getFullName(),
                CardNumbers.generate(), new BigDecimal("1500.00"), startInstant));

        payees.save(new Payee(lucia.getId(), "Inmobiliaria Brisa", LANDLORD_IBAN, "Alquiler", startInstant));
        payees.save(new Payee(lucia.getId(), "Marcos Ruiz", marcosMain.getIban(), "Marcos", startInstant));
        payees.save(new Payee(marcos.getId(), "Lucía Fernández", luciaMain.getIban(), "Lucía", startInstant));

        welcomeGift(luciaMain, startInstant);
        welcomeGift(marcosMain, startInstant.plusSeconds(60));

        for (YearMonth month = YearMonth.from(start); !month.isAfter(YearMonth.from(today)); month = month.plusMonths(1)) {
            planMonth(month, today, zone, lucia, marcos, luciaMain, marcosMain, japan, emergency, bike, luciaCard, marcosCard);
        }
        events.stream()
                .filter(event -> event.at().isBefore(now))
                .sorted(Comparator.comparing(Event::at))
                .forEach(Event::apply);

        seedNotifications(lucia, marcos, now);
        auditEvents.save(new AuditEvent(now.minus(Duration.ofHours(3)), lucia.getId(), lucia.getEmail(),
                AuditAction.LOGIN_SUCCEEDED, "USER", lucia.getId().toString(), "83.45.120.7", "Firefox en Windows"));
        auditEvents.save(new AuditEvent(now.minus(Duration.ofHours(2)), marcos.getId(), marcos.getEmail(),
                AuditAction.LOGIN_FAILED, "USER", marcos.getId().toString(), "91.126.33.18", null));
        auditEvents.save(new AuditEvent(now.minus(Duration.ofHours(1)), marcos.getId(), marcos.getEmail(),
                AuditAction.LOGIN_SUCCEEDED, "USER", marcos.getId().toString(), "91.126.33.18", "Chrome en Android"));
    }

    private void planMonth(YearMonth month, LocalDate today, ZoneId zone, User lucia, User marcos, Account luciaMain,
                           Account marcosMain, Account japan, Account emergency, Account bike, Card luciaCard,
                           Card marcosCard) {
        String monthName = month.getMonth().getDisplayName(TextStyle.FULL, SPANISH);
        boolean current = month.equals(YearMonth.from(today));

        income(at(month, 1, 10, zone), luciaMain, new BigDecimal("2150.00"), "Tecnologías Atlántico S.L.",
                "Nómina " + monthName);
        income(at(month, 1, 10, zone).plusSeconds(30), marcosMain, new BigDecimal("1890.00"), "Grupo Meridiano S.A.",
                "Nómina " + monthName);

        externalTransfer(at(month, 3, 10, zone), lucia, luciaMain, LANDLORD_IBAN, "Inmobiliaria Brisa",
                new BigDecimal("750.00"), Category.HOUSING, "Alquiler " + monthName);
        externalTransfer(at(month, 4, 9, zone), marcos, marcosMain, GYM_IBAN, "Gimnasio Vital",
                new BigDecimal("39.90"), Category.LEISURE, "Cuota " + monthName);

        savingsMove(at(month, 2, 20, zone), luciaMain, japan, new BigDecimal("200.00"));
        savingsMove(at(month, 2, 20, zone).plusSeconds(30), luciaMain, emergency, new BigDecimal("300.00"));
        savingsMove(at(month, 2, 21, zone), marcosMain, bike, new BigDecimal("150.00"));

        card(at(month, 6, 19, zone), luciaCard, luciaMain, "Luz Verde Energía", Category.BILLS, amount(45, 78), false);
        card(at(month, 7, 11, zone), luciaCard, luciaMain, "Fibra Total", Category.BILLS, new BigDecimal("35.00"), true);
        card(at(month, 5, 18, zone), marcosCard, marcosMain, "Fibra Total", Category.BILLS, new BigDecimal("29.90"), true);

        int purchases = current ? 6 : 14;
        for (int i = 0; i < purchases; i++) {
            Purchase luciaPurchase = randomPurchase();
            card(at(month, 2 + random.nextInt(26), 9 + random.nextInt(12), zone), luciaCard, luciaMain,
                    luciaPurchase.merchant(), luciaPurchase.category(), luciaPurchase.amount(), luciaPurchase.online());
            Purchase marcosPurchase = randomPurchase();
            card(at(month, 2 + random.nextInt(26), 9 + random.nextInt(12), zone), marcosCard, marcosMain,
                    marcosPurchase.merchant(), marcosPurchase.category(), marcosPurchase.amount(), marcosPurchase.online());
        }

        internalTransfer(at(month, 12, 22, zone), lucia, luciaMain, marcosMain, "Marcos Ruiz", amount(25, 45), "Cena del viernes");
        internalTransfer(at(month, 20, 13, zone), marcos, marcosMain, luciaMain, "Lucía Fernández", amount(30, 60),
                "Entradas del concierto");
    }

    private void welcomeGift(Account account, Instant at) {
        BigDecimal gift = new BigDecimal("1000.00");
        ledger.record(LedgerEntryRequest.builder(EntryType.WELCOME_GIFT, "Regalo de bienvenida", at)
                .leg(Account.BANK_FUNDS, gift.negate(), null, account.getName(), account.getIban(), "Regalo de bienvenida")
                .leg(account.getId(), gift, Category.GIFT, "Hucha", null, "Regalo de bienvenida (demo)")
                .build());
    }

    private void income(Instant at, Account account, BigDecimal amount, String payer, String concept) {
        events.add(new Event(at, () -> ledger.record(LedgerEntryRequest
                .builder(EntryType.INCOMING_TRANSFER, "Transferencia recibida de " + payer, at)
                .leg(Account.SEPA_CLEARING, amount.negate(), Category.SALARY, account.getName(), account.getIban(), concept)
                .leg(account.getId(), amount, Category.SALARY, payer, Iban.spanish("9992", "0001", "0000777777"), concept)
                .build())));
    }

    private void externalTransfer(Instant at, User user, Account from, String iban, String beneficiary,
                                  BigDecimal amount, Category category, String concept) {
        events.add(new Event(at, () -> {
            Transfer transfer = new Transfer(user.getId(), from.getId(), null, iban, beneficiary, amount, concept,
                    "seed-" + UUID.randomUUID(), "seed", at);
            JournalEntry entry = ledger.record(LedgerEntryRequest
                    .builder(EntryType.TRANSFER, "Transferencia a " + beneficiary, at)
                    .reference(transfer.getId())
                    .leg(from.getId(), amount.negate(), category, beneficiary, iban, concept)
                    .leg(Account.SEPA_CLEARING, amount, category, user.getFullName(), from.getIban(), concept)
                    .build());
            transfer.complete(entry.getId(), at);
            transfers.save(transfer);
        }));
    }

    private void internalTransfer(Instant at, User sender, Account from, Account to, String beneficiary,
                                  BigDecimal amount, String concept) {
        events.add(new Event(at, () -> {
            Transfer transfer = new Transfer(sender.getId(), from.getId(), to.getId(), to.getIban(), beneficiary, amount,
                    concept, "seed-" + UUID.randomUUID(), "seed", at);
            JournalEntry entry = ledger.record(LedgerEntryRequest
                    .builder(EntryType.TRANSFER, "Transferencia a " + beneficiary, at)
                    .reference(transfer.getId())
                    .leg(from.getId(), amount.negate(), Category.TRANSFER, beneficiary, to.getIban(), concept)
                    .leg(to.getId(), amount, Category.TRANSFER, sender.getFullName(), from.getIban(), concept)
                    .build());
            transfer.complete(entry.getId(), at);
            transfers.save(transfer);
        }));
    }

    private void savingsMove(Instant at, Account from, Account savings, BigDecimal amount) {
        String description = "Ahorro en «" + savings.getName() + "»";
        events.add(new Event(at, () -> ledger.record(LedgerEntryRequest.builder(EntryType.SAVINGS_MOVE, description, at)
                .leg(from.getId(), amount.negate(), Category.SAVINGS, savings.getName(), savings.getIban(), description)
                .leg(savings.getId(), amount, Category.SAVINGS, from.getName(), from.getIban(), description)
                .build())));
    }

    private void card(Instant at, Card card, Account account, String merchant, Category category, BigDecimal amount,
                      boolean online) {
        events.add(new Event(at, () -> {
            JournalEntry entry = ledger.record(LedgerEntryRequest
                    .builder(EntryType.CARD_PAYMENT, "Pago con tarjeta en " + merchant, at)
                    .reference(card.getId())
                    .leg(account.getId(), amount.negate(), category, merchant, null, "Tarjeta •••• " + card.getLast4())
                    .leg(Account.CARD_SETTLEMENT, amount, category, card.getHolderName(), account.getIban(), merchant)
                    .build());
            cardTransactions.save(CardTransaction.approved(card.getId(), merchant, category, amount, online,
                    entry.getId(), at));
        }));
    }

    private void seedNotifications(User lucia, User marcos, Instant now) {
        notifications.save(new Notification(lucia.getId(), NotificationType.WELCOME, "¡Bienvenida a Hucha!",
                "Tu cuenta y tu tarjeta virtual ya están activas.", now.minus(Duration.ofDays(150))));
        notifications.save(new Notification(lucia.getId(), NotificationType.TRANSFER_RECEIVED,
                "Has recibido " + Money.format(new BigDecimal("48.00")),
                "Marcos Ruiz: «Entradas del concierto»", now.minus(Duration.ofDays(2))));
        Notification declined = new Notification(lucia.getId(), NotificationType.CARD_DECLINED,
                "Pago con tarjeta denegado",
                Money.format(new BigDecimal("89.99")) + " en Tienda Online Nube: " + DeclineReason.ONLINE_DISABLED.description(),
                now.minus(Duration.ofHours(20)));
        notifications.save(declined);
        notifications.save(new Notification(marcos.getId(), NotificationType.WELCOME, "¡Bienvenido a Hucha!",
                "Tu cuenta y tu tarjeta virtual ya están activas.", now.minus(Duration.ofDays(150))));
    }

    private Purchase randomPurchase() {
        Purchase[] catalog = {
                new Purchase("Supermercado La Plaza", Category.GROCERIES, amount(18, 95), false),
                new Purchase("Frutería Martín", Category.GROCERIES, amount(8, 30), false),
                new Purchase("Bar Casa Paco", Category.RESTAURANTS, amount(9, 32), false),
                new Purchase("Sushi Kenzo", Category.RESTAURANTS, amount(22, 58), false),
                new Purchase("Café Central", Category.RESTAURANTS, amount(3, 12), false),
                new Purchase("Gasolinera Norte", Category.TRANSPORT, amount(35, 70), false),
                new Purchase("Taxi Madrid Centro", Category.TRANSPORT, amount(9, 24), false),
                new Purchase("Tienda Moda Urbana", Category.SHOPPING, amount(25, 120), true),
                new Purchase("Librería Tintero", Category.SHOPPING, amount(12, 40), false),
                new Purchase("Cine Lumière", Category.LEISURE, amount(8, 22), false),
                new Purchase("Farmacia del Sol", Category.HEALTH, amount(6, 35), false),
                new Purchase("Plataforma Streamly", Category.LEISURE, new BigDecimal("12.99"), true),
        };
        return catalog[random.nextInt(catalog.length)];
    }

    private BigDecimal amount(int min, int max) {
        int cents = min * 100 + random.nextInt((max - min) * 100 + 1);
        return BigDecimal.valueOf(cents, 2);
    }

    private Instant at(YearMonth month, int day, int hour, ZoneId zone) {
        int safeDay = Math.min(day, month.lengthOfMonth());
        return month.atDay(safeDay).atTime(LocalTime.of(hour, random.nextInt(60))).atZone(zone).toInstant();
    }

    private record Event(Instant at, Runnable action) {
        void apply() {
            action.run();
        }
    }

    private record Purchase(String merchant, Category category, BigDecimal amount, boolean online) {
    }
}
