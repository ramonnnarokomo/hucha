package com.ramonnnarokomo.hucha.transfer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ramonnnarokomo.hucha.account.Account;
import com.ramonnnarokomo.hucha.account.AccountRepository;
import com.ramonnnarokomo.hucha.account.AccountService;
import com.ramonnnarokomo.hucha.account.AccountType;
import com.ramonnnarokomo.hucha.account.Iban;
import com.ramonnnarokomo.hucha.audit.AuditAction;
import com.ramonnnarokomo.hucha.audit.AuditService;
import com.ramonnnarokomo.hucha.ledger.Category;
import com.ramonnnarokomo.hucha.ledger.EntryType;
import com.ramonnnarokomo.hucha.ledger.JournalEntry;
import com.ramonnnarokomo.hucha.ledger.LedgerEntryRequest;
import com.ramonnnarokomo.hucha.ledger.LedgerService;
import com.ramonnnarokomo.hucha.ledger.SpendingLimits;
import com.ramonnnarokomo.hucha.notification.NotificationService;
import com.ramonnnarokomo.hucha.notification.NotificationType;
import com.ramonnnarokomo.hucha.outbox.DomainEvents;
import com.ramonnnarokomo.hucha.outbox.OutboxWriter;
import com.ramonnnarokomo.hucha.payee.PayeeService;
import com.ramonnnarokomo.hucha.shared.Amounts;
import com.ramonnnarokomo.hucha.shared.Hashing;
import com.ramonnnarokomo.hucha.shared.HuchaProperties;
import com.ramonnnarokomo.hucha.shared.Money;
import com.ramonnnarokomo.hucha.shared.PageResponse;
import com.ramonnnarokomo.hucha.shared.RequestInfo;
import com.ramonnnarokomo.hucha.shared.error.ApiException;
import com.ramonnnarokomo.hucha.shared.error.ErrorCode;
import com.ramonnnarokomo.hucha.transfer.TransferDtos.CreateTransferRequest;
import com.ramonnnarokomo.hucha.transfer.TransferDtos.TransferView;
import com.ramonnnarokomo.hucha.user.User;
import com.ramonnnarokomo.hucha.user.UserRepository;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Transferencias SEPA simuladas:
 * <ul>
 *   <li>Idempotencia: la misma cabecera Idempotency-Key nunca cobra dos veces.</li>
 *   <li>Límites: máximo por operación y límite diario de salidas de la cuenta.</li>
 *   <li>Autenticación reforzada (PSD2): por encima de un importe, o a un IBAN que no está entre tus
 *       beneficiarios, la transferencia queda pendiente hasta confirmar un código de 6 dígitos.</li>
 *   <li>Entre clientes de Hucha el dinero llega al momento; hacia fuera sale por la cuenta de compensación SEPA.</li>
 * </ul>
 */
@Service
public class TransferService {

    private final TransferRepository transfers;
    private final AccountRepository accounts;
    private final AccountService accountService;
    private final LedgerService ledger;
    private final SpendingLimits limits;
    private final PayeeService payees;
    private final ScaCodeGenerator codes;
    private final NotificationService notifications;
    private final OutboxWriter outbox;
    private final AuditService audit;
    private final UserRepository users;
    private final HuchaProperties properties;
    private final MeterRegistry metrics;
    private final Clock clock;

    public TransferService(TransferRepository transfers, AccountRepository accounts, AccountService accountService,
                           LedgerService ledger, SpendingLimits limits, PayeeService payees, ScaCodeGenerator codes,
                           NotificationService notifications, OutboxWriter outbox, AuditService audit,
                           UserRepository users, HuchaProperties properties, MeterRegistry metrics, Clock clock) {
        this.transfers = transfers;
        this.accounts = accounts;
        this.accountService = accountService;
        this.ledger = ledger;
        this.limits = limits;
        this.payees = payees;
        this.codes = codes;
        this.notifications = notifications;
        this.outbox = outbox;
        this.audit = audit;
        this.users = users;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public Outcome create(UUID userId, String idempotencyKey, CreateTransferRequest request, AuditService.Actor actor,
                          RequestInfo info) {
        String key = requireIdempotencyKey(idempotencyKey);
        String requestHash = Hashing.sha256(fingerprint(request));
        Optional<Transfer> existing = transfers.findByCreatedByAndIdempotencyKey(userId, key);
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash);
        }

        Instant now = clock.instant();
        Account from = accountService.getOwned(userId, request.fromAccountId());
        if (from.getType() != AccountType.CHECKING) {
            throw ApiException.unprocessable(ErrorCode.INVALID_OPERATION,
                    "Las transferencias salen de la cuenta corriente. Para sacar dinero de una hucha usa «Retirar».");
        }
        AccountService.requireActive(from);

        String toIban = Iban.normalize(request.toIban());
        if (!Iban.isValid(toIban)) {
            throw ApiException.unprocessable(ErrorCode.INVALID_IBAN, "El IBAN de destino no es válido.");
        }
        if (toIban.equals(from.getIban())) {
            throw ApiException.unprocessable(ErrorCode.SAME_ACCOUNT, "La cuenta de origen y la de destino son la misma.");
        }
        BigDecimal amount = Amounts.positive(request.amount());
        if (amount.compareTo(properties.transfers().maxAmount()) > 0) {
            throw ApiException.unprocessable(ErrorCode.TRANSFER_LIMIT_EXCEEDED,
                    "El máximo por transferencia es " + Money.format(properties.transfers().maxAmount()) + ".");
        }

        Account to = accounts.findByIban(toIban).orElse(null);
        if (to != null && to.isSystem()) {
            throw ApiException.unprocessable(ErrorCode.INVALID_OPERATION, "No se puede transferir a esa cuenta.");
        }
        boolean ownDestination = to != null && to.isOwnedBy(userId);
        if (!ownDestination && !limits.allows(from, amount, now)) {
            throw dailyLimitExceeded(from, now);
        }
        if (from.getBalance().compareTo(amount) < 0) {
            throw ApiException.unprocessable(ErrorCode.INSUFFICIENT_FUNDS, "No tienes saldo suficiente.");
        }

        String beneficiary = ownDestination ? to.getName() : request.beneficiaryName();
        Transfer transfer = new Transfer(userId, from.getId(), to != null ? to.getId() : null, toIban, beneficiary,
                amount, request.concept(), key, requestHash, now);

        if (!ownDestination && requiresSca(userId, toIban, amount)) {
            String code = codes.generate();
            transfer.requireSca(Hashing.sha256(transfer.getId() + ":" + code), now.plus(properties.transfers().scaCodeTtl()));
            // saveAndFlush: si otra petición con la misma clave se ha colado, falla aquí con DataIntegrityViolation.
            transfers.saveAndFlush(transfer);
            notifications.notify(userId, NotificationType.SCA_CODE, "Código de verificación",
                    "Tu código para enviar " + Money.format(amount) + " a " + transfer.getBeneficiaryName() + " es "
                            + code + ". Caduca en " + properties.transfers().scaCodeTtl().toMinutes()
                            + " minutos. Nunca te lo pediremos por teléfono.");
            audit.record(actor, AuditAction.TRANSFER_PENDING_SCA, "TRANSFER", transfer.getId(), info,
                    Money.format(amount) + " a " + toIban);
            metrics.counter("hucha.transfers", "status", "pending_sca").increment();
            return new Outcome(TransferView.of(transfer), false);
        }

        transfers.saveAndFlush(transfer);
        execute(transfer, from, to, actor, info, now);
        return new Outcome(TransferView.of(transfer), false);
    }

    /** Otra petición con la misma clave ganó la carrera: se devuelve su resultado. */
    @Transactional(readOnly = true)
    public Outcome replayAfterRace(UUID userId, String idempotencyKey, CreateTransferRequest request) {
        Transfer existing = transfers.findByCreatedByAndIdempotencyKey(userId, requireIdempotencyKey(idempotencyKey))
                .orElseThrow(() -> ApiException.conflict(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "Esta operación ya se está procesando."));
        return replay(existing, Hashing.sha256(fingerprint(request)));
    }

    /** Confirma con el código de verificación. Los intentos fallidos y el rechazo se guardan (noRollbackFor). */
    @Transactional(noRollbackFor = ApiException.class)
    public TransferView confirm(UUID userId, UUID transferId, String code, AuditService.Actor actor, RequestInfo info) {
        Transfer transfer = owned(userId, transferId);
        if (transfer.getStatus() != TransferStatus.PENDING_SCA) {
            throw ApiException.conflict(ErrorCode.TRANSFER_NOT_PENDING, "Esta transferencia ya no está pendiente.");
        }
        Instant now = clock.instant();
        if (transfer.isScaExpired(now)) {
            transfer.expire();
            audit.record(actor, AuditAction.SCA_FAILED, "TRANSFER", transfer.getId(), info, "código caducado");
            throw ApiException.unprocessable(ErrorCode.SCA_CODE_EXPIRED, "El código ha caducado. Vuelve a hacer la transferencia.");
        }
        if (!Hashing.matches(transfer.getScaCodeHash(), transfer.getId() + ":" + code)) {
            int remaining = transfer.registerFailedScaAttempt(properties.transfers().scaMaxAttempts());
            audit.record(actor, AuditAction.SCA_FAILED, "TRANSFER", transfer.getId(), info, "código incorrecto");
            if (remaining == 0) {
                metrics.counter("hucha.transfers", "status", "rejected").increment();
                throw ApiException.unprocessable(ErrorCode.SCA_TOO_MANY_ATTEMPTS,
                        "Has agotado los intentos. Hemos cancelado la transferencia por seguridad.");
            }
            throw ApiException.unprocessable(ErrorCode.SCA_CODE_INVALID,
                    "Código incorrecto. Te queda" + (remaining == 1 ? " 1 intento." : "n " + remaining + " intentos."));
        }

        Account from = accounts.findById(transfer.getFromAccountId()).orElseThrow();
        Account to = transfer.getToAccountId() != null ? accounts.findById(transfer.getToAccountId()).orElse(null) : null;
        try {
            AccountService.requireActive(from);
            if (!limits.allows(from, transfer.getAmount(), now)) {
                throw dailyLimitExceeded(from, now);
            }
            execute(transfer, from, to, actor, info, now);
        } catch (ApiException e) {
            transfer.reject(e.code());
            audit.record(actor, AuditAction.TRANSFER_REJECTED, "TRANSFER", transfer.getId(), info, e.code().name());
            metrics.counter("hucha.transfers", "status", "rejected").increment();
            throw e;
        }
        return TransferView.of(transfer);
    }

    @Transactional
    public TransferView cancel(UUID userId, UUID transferId, AuditService.Actor actor, RequestInfo info) {
        Transfer transfer = owned(userId, transferId);
        if (transfer.getStatus() != TransferStatus.PENDING_SCA) {
            throw ApiException.conflict(ErrorCode.TRANSFER_NOT_PENDING, "Esta transferencia ya no está pendiente.");
        }
        transfer.cancel();
        audit.record(actor, AuditAction.TRANSFER_CANCELLED, "TRANSFER", transfer.getId(), info, null);
        return TransferView.of(transfer);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransferView> list(UUID userId, int page, int size) {
        return PageResponse.of(
                transfers.findByCreatedByOrderByCreatedAtDesc(userId, PageRequest.of(page, Math.min(size, 50))),
                TransferView::of);
    }

    @Transactional(readOnly = true)
    public TransferView get(UUID userId, UUID transferId) {
        return TransferView.of(owned(userId, transferId));
    }

    private void execute(Transfer transfer, Account from, Account to, AuditService.Actor actor, RequestInfo info,
                         Instant now) {
        boolean own = to != null && to.isOwnedBy(transfer.getCreatedBy());
        User sender = users.findById(transfer.getCreatedBy()).orElseThrow();
        Category category = own && to.getType() == AccountType.SAVINGS ? Category.SAVINGS : Category.TRANSFER;
        BigDecimal amount = transfer.getAmount();

        JournalEntry entry = ledger.record(LedgerEntryRequest
                .builder(own ? EntryType.OWN_TRANSFER : EntryType.TRANSFER,
                        "Transferencia a " + transfer.getBeneficiaryName(), now)
                .reference(transfer.getId())
                .leg(from.getId(), amount.negate(), category, transfer.getBeneficiaryName(), transfer.getToIban(),
                        transfer.getConcept())
                .leg(to != null ? to.getId() : Account.SEPA_CLEARING, amount, category, sender.getFullName(),
                        from.getIban(), transfer.getConcept())
                .build());
        transfer.complete(entry.getId(), now);

        if (to != null && !own) {
            outbox.append(DomainEvents.TRANSFER_RECEIVED, transfer.getId(), new DomainEvents.TransferReceived(
                    transfer.getId(), to.getOwnerId(), amount, sender.getFullName(), transfer.getConcept()));
        }
        audit.record(actor, AuditAction.TRANSFER_COMPLETED, "TRANSFER", transfer.getId(), info,
                Money.format(transfer.getAmount()) + " a " + transfer.getToIban());
        metrics.counter("hucha.transfers", "status", "completed").increment();
    }

    private boolean requiresSca(UUID userId, String toIban, BigDecimal amount) {
        if (amount.compareTo(properties.transfers().scaThreshold()) >= 0) {
            return true;
        }
        return amount.compareTo(properties.transfers().scaNewPayeeThreshold()) > 0
                && !payees.isSavedPayee(userId, toIban);
    }

    private Outcome replay(Transfer existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw ApiException.unprocessable(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "Esa Idempotency-Key ya se usó con otros datos.");
        }
        return new Outcome(TransferView.of(existing), true);
    }

    private ApiException dailyLimitExceeded(Account from, Instant now) {
        BigDecimal remaining = limits.remainingToday(from, now);
        return ApiException.unprocessable(ErrorCode.DAILY_LIMIT_EXCEEDED,
                "Supera tu límite diario. Hoy aún puedes enviar " + Money.format(remaining) + ".");
    }

    private Transfer owned(UUID userId, UUID transferId) {
        return transfers.findByIdAndCreatedBy(transferId, userId)
                .orElseThrow(() -> ApiException.notFound("Transferencia no encontrada."));
    }

    private static String requireIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 80) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "Falta la cabecera Idempotency-Key (máximo 80 caracteres).");
        }
        return key.trim();
    }

    /** Huella de la petición: la misma clave con datos distintos es un error del cliente. */
    private static String fingerprint(CreateTransferRequest request) {
        return String.join("|",
                String.valueOf(request.fromAccountId()),
                Iban.normalize(request.toIban()),
                request.beneficiaryName() == null ? "" : request.beneficiaryName().trim(),
                request.amount() == null ? "" : request.amount().stripTrailingZeros().toPlainString(),
                request.concept() == null ? "" : request.concept().trim());
    }

    public record Outcome(TransferView transfer, boolean replayed) {
    }
}
