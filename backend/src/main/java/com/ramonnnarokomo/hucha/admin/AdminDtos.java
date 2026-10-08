package com.ramonnnarokomo.hucha.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.ramonnnarokomo.hucha.account.AccountView;
import com.ramonnnarokomo.hucha.user.Role;
import com.ramonnnarokomo.hucha.user.UserStatus;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record Overview(
            long customers,
            long accounts,
            BigDecimal customerDeposits,
            long transfersToday,
            BigDecimal transferVolumeToday,
            long cardApprovalsToday,
            long cardDeclinesToday,
            long pendingOutboxEvents,
            int liveConnections,
            List<DailyVolume> last7Days) {
    }

    public record DailyVolume(LocalDate date, BigDecimal volume) {
    }

    public record UserRow(UUID id, String email, String fullName, Role role, UserStatus status, Instant createdAt,
                          BigDecimal totalBalance) {
    }

    public record UserDetail(UserRow user, List<AccountView> accounts) {
    }
}
