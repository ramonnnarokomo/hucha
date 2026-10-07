package com.ramonnnarokomo.hucha.payee;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ramonnnarokomo.hucha.account.AccountRepository;
import com.ramonnnarokomo.hucha.account.Iban;
import com.ramonnnarokomo.hucha.audit.AuditAction;
import com.ramonnnarokomo.hucha.audit.AuditService;
import com.ramonnnarokomo.hucha.payee.PayeeDtos.CreatePayeeRequest;
import com.ramonnnarokomo.hucha.payee.PayeeDtos.IbanCheck;
import com.ramonnnarokomo.hucha.payee.PayeeDtos.PayeeView;
import com.ramonnnarokomo.hucha.payee.PayeeDtos.UpdatePayeeRequest;
import com.ramonnnarokomo.hucha.shared.RequestInfo;
import com.ramonnnarokomo.hucha.shared.error.ApiException;
import com.ramonnnarokomo.hucha.shared.error.ErrorCode;

@Service
public class PayeeService {

    private final PayeeRepository payees;
    private final AccountRepository accounts;
    private final AuditService audit;
    private final Clock clock;

    public PayeeService(PayeeRepository payees, AccountRepository accounts, AuditService audit, Clock clock) {
        this.payees = payees;
        this.accounts = accounts;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PayeeView> list(UUID userId) {
        return payees.findByOwnerIdOrderByNameAsc(userId).stream().map(this::toView).toList();
    }

    @Transactional
    public PayeeView add(UUID userId, CreatePayeeRequest request, AuditService.Actor actor, RequestInfo info) {
        String iban = Iban.normalize(request.iban());
        if (!Iban.isValid(iban)) {
            throw ApiException.unprocessable(ErrorCode.INVALID_IBAN, "El IBAN no es válido.");
        }
        if (payees.existsByOwnerIdAndIban(userId, iban)) {
            throw ApiException.conflict(ErrorCode.PAYEE_ALREADY_EXISTS, "Ya tienes guardado ese IBAN.");
        }
        Payee payee = payees.save(new Payee(userId, request.name(), iban, request.alias(), clock.instant()));
        audit.record(actor, AuditAction.PAYEE_ADDED, "PAYEE", payee.getId(), info, payee.getName() + " " + iban);
        return toView(payee);
    }

    @Transactional
    public PayeeView update(UUID userId, UUID payeeId, UpdatePayeeRequest request) {
        Payee payee = owned(userId, payeeId);
        payee.rename(request.name(), request.alias());
        return toView(payee);
    }

    @Transactional
    public void delete(UUID userId, UUID payeeId, AuditService.Actor actor, RequestInfo info) {
        Payee payee = owned(userId, payeeId);
        payees.delete(payee);
        audit.record(actor, AuditAction.PAYEE_REMOVED, "PAYEE", payee.getId(), info, payee.getName());
    }

    @Transactional(readOnly = true)
    public boolean isSavedPayee(UUID userId, String iban) {
        return payees.existsByOwnerIdAndIban(userId, Iban.normalize(iban));
    }

    @Transactional(readOnly = true)
    public IbanCheck check(UUID userId, String rawIban) {
        String iban = Iban.normalize(rawIban);
        boolean valid = Iban.isValid(iban);
        boolean internal = valid && accounts.findByIban(iban).filter(account -> !account.isSystem()).isPresent();
        return new IbanCheck(iban, valid, internal, valid && payees.existsByOwnerIdAndIban(userId, iban));
    }

    private Payee owned(UUID userId, UUID payeeId) {
        return payees.findByIdAndOwnerId(payeeId, userId)
                .orElseThrow(() -> ApiException.notFound("Beneficiario no encontrado."));
    }

    private PayeeView toView(Payee payee) {
        boolean internal = accounts.findByIban(payee.getIban()).filter(account -> !account.isSystem()).isPresent();
        return new PayeeView(payee.getId(), payee.getName(), payee.getIban(), payee.getAlias(), internal,
                payee.getCreatedAt());
    }
}
