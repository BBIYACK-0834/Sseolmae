package com.sseolmae.reservation;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReservationService {

    private final Clock venueClock;
    private final Map<UUID, Reservation> reservations = new ConcurrentHashMap<>();

    public ReservationService(Clock venueClock) {
        this.venueClock = venueClock;
    }

    public Reservation create(ReservationRequest request) {
        if (request.visitDate().isBefore(LocalDate.now(venueClock))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지난 날짜는 선택할 수 없습니다.");
        }

        int partySize = request.adults() + request.children();
        if (partySize < 1 || partySize > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "전체 인원은 1명 이상 20명 이하여야 합니다.");
        }

        Reservation reservation = new Reservation(
            UUID.randomUUID(), request.visitDate(), request.ticketType(),
            request.adults(), request.children(), Reservation.Status.DEMO_RECEIVED
        );
        reservations.put(reservation.id(), reservation);
        return reservation;
    }

    public Optional<Reservation> findById(UUID id) {
        return Optional.ofNullable(reservations.get(id));
    }
}
