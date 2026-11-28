package com.sseolmae.reservation;

import com.sseolmae.auth.Accounts;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ReservationController {
  private final com.sseolmae.payment.TossGateway gateway;
  private final Accounts accounts;
  private final ReservationService bookings;

  public ReservationController(Accounts accounts, ReservationService bookings, com.sseolmae.payment.TossGateway gateway) {
    this.gateway = gateway;
    this.accounts = accounts;
    this.bookings = bookings;
  }

  @GetMapping("/slots")
  List<ReservationService.Slot> slots() {
    return bookings.slots();
  }

  @PostMapping("/reservations")
  ResponseEntity<Reservation> create(
      Authentication auth, @RequestBody @Valid ReservationRequest request) {
    gateway.requireSetup();
    var booking = bookings.create(accounts.id(auth), request);
    return ResponseEntity.created(URI.create("/api/reservations/" + booking.id())).body(booking);
  }

  @GetMapping("/reservations")
  List<Reservation> mine(Authentication auth) {
    return bookings.list(accounts.id(auth));
  }

  @GetMapping("/reservations/{id}")
  Reservation get(Authentication auth, @PathVariable UUID id) {
    return bookings.get(accounts.id(auth), id);
  }

  @DeleteMapping("/reservations/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void cancel(Authentication auth, @PathVariable UUID id) {
    bookings.cancel(accounts.id(auth), id);
  }
}
