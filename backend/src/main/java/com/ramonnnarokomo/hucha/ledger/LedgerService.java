package com.ramonnnarokomo.hucha.ledger;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ramonnnarokomo.hucha.account.Account;
import com.ramonnnarokomo.hucha.shared.error.ApiException;
import com.ramonnnarokomo.hucha.shared.error.ErrorCode;

/**
 * Libro mayor de doble entrada. Es el único punto por el que cambia el saldo de una cuenta:
 * cada operación se registra como un asiento cuyos apuntes suman exactamente cero, así que
 * el dinero ni se crea ni se pierde, solo se mueve entre cuentas.
 */
@Service
public class LedgerService {

    private final EntityManager entityManager;
    private final JournalEntryRepository entries;
    private final PostingRepository postings;

    public LedgerService(EntityManager entityManager, JournalEntryRepository entries, PostingRepository postings) {
        this.entityManager = entityManager;
        this.entries = entries;
        this.postings = postings;
    }

    /**
     * Registra el asiento. Primero bloquea las cuentas implicadas (en orden por id), valida todo
     * y solo después toca los saldos: si algo falla no queda ningún cambio a medias. Por eso un error
     * de negocio (saldo insuficiente, cuenta congelada) no marca la transacción para deshacer: quien
     * llama puede, por ejemplo, guardar la transferencia como rechazada.
     */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = ApiException.class)
    public JournalEntry record(LedgerEntryRequest request) {
        validateShape(request);

        Map<UUID, Account> locked = lockInIdOrder(request);

        Map<UUID, BigDecimal> newBalances = new HashMap<>();
        for (LedgerEntryRequest.Leg leg : request.legs()) {
            Account account = locked.get(leg.accountId());
            if (!account.isActive()) {
                throw ApiException.unprocessable(ErrorCode.ACCOUNT_NOT_ACTIVE,
                        "Una de las cuentas del movimiento no está operativa.");
            }
            BigDecimal current = newBalances.getOrDefault(account.getId(), account.getBalance());
            BigDecimal updated = current.add(leg.amount());
            if (!account.canHaveBalance(updated)) {
                throw ApiException.unprocessable(ErrorCode.INSUFFICIENT_FUNDS, "No hay saldo suficiente.");
            }
            newBalances.put(account.getId(), updated);
        }

        JournalEntry entry = entries.save(new JournalEntry(
                request.type(), request.description(), request.occurredAt(), request.referenceId()));
        for (LedgerEntryRequest.Leg leg : request.legs()) {
            Account account = locked.get(leg.accountId());
            account.applyPosting(leg.amount());
            postings.save(new Posting(entry, account.getId(), leg.amount(), account.getBalance(), leg.category(),
                    leg.counterpartyName(), leg.counterpartyIban(), leg.concept()));
        }
        return entry;
    }

    /**
     * SELECT ... FOR UPDATE de cada cuenta, siempre en el mismo orden (por id): dos operaciones
     * cruzadas A->B y B->A no pueden quedarse esperando la una a la otra. El refresh relee el
     * saldo ya bloqueado, aunque la cuenta estuviera cargada antes en la transacción.
     */
    private Map<UUID, Account> lockInIdOrder(LedgerEntryRequest request) {
        TreeSet<UUID> ids = new TreeSet<>();
        request.legs().forEach(leg -> ids.add(leg.accountId()));
        // Escribe antes los cambios pendientes de esta transacción, para que el refresh no los pise.
        entityManager.flush();
        Map<UUID, Account> locked = new HashMap<>();
        for (UUID id : ids) {
            Account account = entityManager.find(Account.class, id);
            if (account == null) {
                throw ApiException.notFound("Alguna de las cuentas del movimiento no existe.");
            }
            entityManager.refresh(account, LockModeType.PESSIMISTIC_WRITE);
            locked.put(id, account);
        }
        return locked;
    }

    private static void validateShape(LedgerEntryRequest request) {
        List<LedgerEntryRequest.Leg> legs = request.legs();
        if (legs.size() < 2) {
            throw new IllegalArgumentException("Un asiento necesita al menos dos apuntes");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (LedgerEntryRequest.Leg leg : legs) {
            if (leg.amount().signum() == 0 || leg.amount().scale() != 2) {
                throw new IllegalArgumentException("Cada apunte necesita un importe distinto de cero con dos decimales");
            }
            total = total.add(leg.amount());
        }
        if (total.signum() != 0) {
            throw new IllegalArgumentException("El asiento no cuadra: los apuntes suman " + total);
        }
    }
}
