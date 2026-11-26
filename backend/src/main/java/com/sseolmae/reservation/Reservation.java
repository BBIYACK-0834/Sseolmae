package com.sseolmae.reservation;

import java.time.LocalDate;
import java.util.UUID;

public record Reservation(
    UUID id,
    LocalDate visitDate,
    TicketType ticketType,
    int adults,
    int children,
    Status status
) {
    public enum Status {
        DEMO_RECEIVED
    }
}
