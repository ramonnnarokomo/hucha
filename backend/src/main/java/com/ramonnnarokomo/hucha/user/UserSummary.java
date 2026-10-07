package com.ramonnnarokomo.hucha.user;

import java.util.UUID;

public record UserSummary(UUID id, String email, String fullName, Role role) {

    public static UserSummary of(User user) {
        return new UserSummary(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
    }
}
