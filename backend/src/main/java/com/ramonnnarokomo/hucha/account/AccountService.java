package com.ramonnnarokomo.hucha.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ramonnnarokomo.hucha.shared.HuchaProperties;
import com.ramonnnarokomo.hucha.shared.error.ApiException;
import com.ramonnnarokomo.hucha.shared.error.ErrorCode;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final IbanGenerator ibanGenerator;
    private final HuchaProperties properties;

    public AccountService(AccountRepository accounts, IbanGenerator ibanGenerator, HuchaProperties properties) {
        this.accounts = accounts;
        this.ibanGenerator = ibanGenerator;
        this.properties = properties;
    }

    @Transactional
    public Account openChecking(UUID ownerId, Instant now) {
        return accounts.save(Account.checking(ibanGenerator.next(), ownerId,
                properties.accounts().defaultDailyLimit(), now));
    }

    @Transactional
    public Account openSavings(UUID ownerId, String name, BigDecimal goalAmount, String icon, Instant now) {
        return accounts.save(Account.savings(ibanGenerator.next(), ownerId, name, goalAmount, icon, now));
    }

    @Transactional(readOnly = true)
    public List<AccountView> listForUser(UUID userId) {
        return accounts.findByOwnerIdOrderByCreatedAtAsc(userId).stream()
                .filter(account -> account.getStatus() != AccountStatus.CLOSED)
                .map(AccountView::of)
                .toList();
    }

    /** Cuenta del usuario o 404: a otro usuario no se le confirma ni que la cuenta existe. */
    @Transactional(readOnly = true)
    public Account getOwned(UUID userId, UUID accountId) {
        return accounts.findById(accountId)
                .filter(account -> account.isOwnedBy(userId))
                .filter(account -> account.getStatus() != AccountStatus.CLOSED)
                .orElseThrow(() -> ApiException.notFound("Cuenta no encontrada."));
    }

    @Transactional(readOnly = true)
    public Account mainAccount(UUID userId) {
        return accounts.findByOwnerIdAndTypeOrderByCreatedAtAsc(userId, AccountType.CHECKING).stream()
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("No tienes cuenta corriente."));
    }

    public static void requireActive(Account account) {
        if (!account.isActive()) {
            throw ApiException.unprocessable(ErrorCode.ACCOUNT_NOT_ACTIVE,
                    "La cuenta «" + account.getName() + "» está congelada o cerrada.");
        }
    }
}
