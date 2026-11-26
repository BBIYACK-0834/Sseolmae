package com.sseolmae.reservation;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservations;

    public ReservationController(ReservationService reservations) {
        this.reservations = reservations;
    }

    @PostMapping
    public ResponseEntity<Reservation> create(@Valid @RequestBody ReservationRequest request) {
        Reservation reservation = reservations.create(request);
        return ResponseEntity.created(URI.create("/api/reservations/" + reservation.id())).body(reservation);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Reservation> findById(@PathVariable UUID id) {
        return ResponseEntity.of(reservations.findById(id));
    }
}
