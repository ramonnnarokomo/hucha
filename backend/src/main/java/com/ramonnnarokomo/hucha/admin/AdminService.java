package com.ramonnnarokomo.hucha.admin;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ramonnnarokomo.hucha.account.Account;
import com.ramonnnarokomo.hucha.account.AccountRepository;
import com.ramonnnarokomo.hucha.account.AccountStatus;
import com.ramonnnarokomo.hucha.account.AccountType;
import com.ramonnnarokomo.hucha.account.AccountView;
import com.ramonnnarokomo.hucha.admin.AdminDtos.DailyVolume;
import com.ramonnnarokomo.hucha.admin.AdminDtos.Overview;
import com.ramonnnarokomo.hucha.admin.AdminDtos.UserDetail;
import com.ramonnnarokomo.hucha.admin.AdminDtos.UserRow;
import com.ramonnnarokomo.hucha.audit.AuditAction;
import com.ramonnnarokomo.hucha.audit.AuditEventRepository;
import com.ramonnnarokomo.hucha.audit.AuditService;
import com.ramonnnarokomo.hucha.audit.AuditView;
import com.ramonnnarokomo.hucha.auth.RefreshTokenRepository;
import com.ramonnnarokomo.hucha.card.CardTransactionRepository;
import com.ramonnnarokomo.hucha.card.CardTransactionStatus;
import com.ramonnnarokomo.hucha.notification.SseHub;
import com.ramonnnarokomo.hucha.outbox.DomainEvents;
import com.ramonnnarokomo.hucha.outbox.OutboxRelay;
import com.ramonnnarokomo.hucha.outbox.OutboxWriter;
import com.ramonnnarokomo.hucha.shared.HuchaProperties;
import com.ramonnnarokomo.hucha.shared.PageResponse;
import com.ramonnnarokomo.hucha.shared.RequestInfo;
import com.ramonnnarokomo.hucha.shared.error.ApiException;
import com.ramonnnarokomo.hucha.shared.error.ErrorCode;
import com.ramonnnarokomo.hucha.transfer.TransferRepository;
import com.ramonnnarokomo.hucha.transfer.TransferStatus;
import com.ramonnnarokomo.hucha.user.Role;
import com.ramonnnarokomo.hucha.user.User;
import com.ramonnnarokomo.hucha.user.UserRepository;

/** Back-office: métricas del banco, gestión de clientes y registro de auditoría. */
@Service
public class AdminService {

    private final UserRepository users;
    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final CardTransactionRepository cardTransactions;
    private final AuditEventRepository auditEvents;
    private final RefreshTokenRepository refreshTokens;
    private final OutboxRelay outboxRelay;
    private final OutboxWriter outbox;
    private final SseHub sseHub;
    private final AuditService audit;
    private final HuchaProperties properties;
    private final Clock clock;

    public AdminService(UserRepository users, AccountRepository accounts, TransferRepository transfers,
                        CardTransactionRepository cardTransactions, AuditEventRepository auditEvents,
                        RefreshTokenRepository refreshTokens, OutboxRelay outboxRelay, OutboxWriter outbox,
                        SseHub sseHub, AuditService audit, HuchaProperties properties, Clock clock) {
        this.users = users;
        this.accounts = accounts;
        this.transfers = transfers;
        this.cardTransactions = cardTransactions;
        this.auditEvents = auditEvents;
        this.refreshTokens = refreshTokens;
        this.outboxRelay = outboxRelay;
        this.outbox = outbox;
        this.sseHub = sseHub;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        ZoneId zone = properties.zone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Instant startOfToday = today.atStartOfDay(zone).toInstant();

        List<DailyVolume> last7Days = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            last7Days.add(new DailyVolume(day, transfers.volumeBetween(TransferStatus.COMPLETED,
                    day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant())));
        }
        return new Overview(
                users.countByRole(Role.CUSTOMER),
                accounts.count() - accounts.countByType(AccountType.SYSTEM),
                accounts.totalBalanceExcluding(AccountType.SYSTEM),
                transfers.countByStatusAndCompletedAtGreaterThanEqual(TransferStatus.COMPLETED, startOfToday),
                last7Days.get(last7Days.size() - 1).volume(),
                cardTransactions.countByStatusAndCreatedAtGreaterThanEqual(CardTransactionStatus.APPROVED, startOfToday),
                cardTransactions.countByStatusAndCreatedAtGreaterThanEqual(CardTransactionStatus.DECLINED, startOfToday),
                outboxRelay.pendingCount(),
                sseHub.connectedClients(),
                last7Days);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserRow> users(String query, int page, int size) {
        String pattern = "%" + (query == null ? "" : query.trim().toLowerCase(Locale.ROOT)) + "%";
        return PageResponse.of(
                users.search(pattern, PageRequest.of(page, Math.min(size, 50), Sort.by("createdAt").descending())),
                this::row);
    }

    @Transactional(readOnly = true)
    public UserDetail user(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("Usuario no encontrado."));
        return new UserDetail(row(user), accounts.findByOwnerIdOrderByCreatedAtAsc(userId).stream()
                .map(AccountView::of).toList());
    }

    /** Bloquear a un usuario cierra todas sus sesiones al momento. */
    @Transactional
    public UserRow setBlocked(UUID userId, boolean blocked, AuditService.Actor actor, RequestInfo info) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("Usuario no encontrado."));
        if (user.getId().equals(actor.id())) {
            throw ApiException.unprocessable(ErrorCode.INVALID_OPERATION, "No puedes bloquearte a ti mismo.");
        }
        if (blocked) {
            user.block();
            refreshTokens.revokeAllForUser(user.getId(), clock.instant());
        } else {
            user.unblock();
        }
        audit.record(actor, blocked ? AuditAction.USER_BLOCKED : AuditAction.USER_UNBLOCKED, "USER", user.getId(), info,
                user.getEmail());
        return row(user);
    }

    @Transactional
    public AccountView setFrozen(UUID accountId, boolean frozen, AuditService.Actor actor, RequestInfo info) {
        Account account = accounts.findById(accountId)
                .filter(a -> !a.isSystem())
                .orElseThrow(() -> ApiException.notFound("Cuenta no encontrada."));
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw ApiException.unprocessable(ErrorCode.INVALID_OPERATION, "La cuenta está cerrada.");
        }
        if (frozen) {
            account.freeze();
        } else {
            account.unfreeze();
        }
        outbox.append(DomainEvents.ACCOUNT_STATUS_CHANGED, account.getId(), new DomainEvents.AccountStatusChanged(
                account.getId(), account.getOwnerId(), account.getName(), frozen));
        audit.record(actor, frozen ? AuditAction.ACCOUNT_FROZEN : AuditAction.ACCOUNT_UNFROZEN, "ACCOUNT",
                account.getId(), info, account.getIban());
        return AccountView.of(account);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditView> audit(AuditAction action, int page, int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100));
        return PageResponse.of(
                action == null
                        ? auditEvents.findAllByOrderByOccurredAtDesc(pageable)
                        : auditEvents.findByActionOrderByOccurredAtDesc(action, pageable),
                AuditView::of);
    }

    private UserRow row(User user) {
        BigDecimal total = accounts.totalBalanceOfOwner(user.getId(), AccountStatus.CLOSED);
        return new UserRow(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.getStatus(),
                user.getCreatedAt(), total);
    }
}
