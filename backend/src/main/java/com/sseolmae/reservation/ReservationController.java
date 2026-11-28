package com.sseolmae.reservation;

import com.sseolmae.auth.Accounts;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ReservationController {
  private final Accounts accounts;
  private final ReservationService bookings;

  public ReservationController(Accounts accounts, ReservationService bookings) {
    this.accounts = accounts;
    this.bookings = bookings;
  }

  @GetMapping("/slots")
  List<ReservationService.Slot> slots() {
    return bookings.slots();
  }

  @PostMapping("/reservations")
  ResponseEntity<Reservation> create(
      OAuth2AuthenticationToken auth, @RequestBody @Valid ReservationRequest request) {
    var booking = bookings.create(accounts.id(auth), request);
    return ResponseEntity.created(URI.create("/api/reservations/" + booking.id())).body(booking);
  }

  @GetMapping("/reservations")
  List<Reservation> mine(OAuth2AuthenticationToken auth) {
    return bookings.list(accounts.id(auth));
  }

  @GetMapping("/reservations/{id}")
  Reservation get(OAuth2AuthenticationToken auth, @PathVariable UUID id) {
    return bookings.get(accounts.id(auth), id);
  }

  @DeleteMapping("/reservations/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void cancel(OAuth2AuthenticationToken auth, @PathVariable UUID id) {
    bookings.cancel(accounts.id(auth), id);
  }
}
