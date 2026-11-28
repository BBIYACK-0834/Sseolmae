package com.sseolmae.reservation;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Reservation(
    UUID id,
    UUID slotId,
    TicketType ticketType,
    OffsetDateTime startsAt,
    int adults,
    int children,
    int amount,
    String status,
    OffsetDateTime expiresAt) {}
