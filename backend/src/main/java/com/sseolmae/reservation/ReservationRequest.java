package com.sseolmae.reservation;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record ReservationRequest(
    @NotNull UUID slotId,
    @NotNull UUID requestId,
    @NotNull @Min(0) @Max(20) Integer adults,
    @NotNull @Min(0) @Max(20) Integer children) {}
