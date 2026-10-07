package com.ramonnnarokomo.hucha.payee;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class PayeeDtos {

    private PayeeDtos() {
    }

    public record CreatePayeeRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 42) String iban,
            @Size(max = 60) String alias) {
    }

    public record UpdatePayeeRequest(@NotBlank @Size(max = 120) String name, @Size(max = 60) String alias) {
    }

    /** {@code internal}: el IBAN es de Hucha, así que la transferencia llega al momento. */
    public record PayeeView(UUID id, String name, String iban, String alias, boolean internal, Instant createdAt) {
    }

    /** Comprobación de un IBAN antes de transferir. No revela a quién pertenece. */
    public record IbanCheck(String iban, boolean valid, boolean internal, boolean savedPayee) {
    }
}
