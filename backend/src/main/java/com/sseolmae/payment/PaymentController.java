package com.sseolmae.payment;

import com.sseolmae.auth.Accounts;
import com.sseolmae.reservation.Reservation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
  public record Approval(
      @NotNull UUID orderId, @NotBlank @Size(max = 200) String paymentKey, @Positive int amount) {}

  private final Accounts accounts;
  private final PaymentService payments;

  PaymentController(Accounts accounts, PaymentService payments) {
    this.accounts = accounts;
    this.payments = payments;
  }

  @PostMapping("/confirm")
  Reservation confirm(Authentication auth, @Valid @RequestBody Approval input) {
    return payments.confirm(accounts.id(auth), input.orderId(), input.paymentKey(), input.amount());
  }

  @PostMapping("/{id}/retry")
  Reservation retry(Authentication auth, @PathVariable UUID id) {
    return payments.retry(accounts.id(auth), id);
  }
}
