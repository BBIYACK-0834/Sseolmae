package com.sseolmae.auth;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class SessionController {
  private final com.sseolmae.ops.Staff staff;
  private final Accounts accounts;
  private final SocialProviders providers;

  public SessionController(Accounts accounts, SocialProviders providers, com.sseolmae.ops.Staff staff) {
    this.staff = staff;
    this.accounts = accounts;
    this.providers = providers;
  }

  public record Session(UUID userId, boolean kakaoReady, java.util.List<String> oauthProviders, String clientKey, String csrf, boolean staff) {}

  @GetMapping("/api/session")
  Session session(
      Authentication auth,
      CsrfToken csrf,
      @Value("${venue.toss-client}") String clientKey,
      @Value("${venue.toss-secret}") String paymentSecret) {
    UUID user = auth == null ? null : accounts.id(auth);
    return new Session(
        user,
        providers.enabled().contains("kakao"),
        providers.enabled(),
        paymentSecret.isBlank() ? "" : clientKey,
        csrf.getToken(), staff.includes(user));
  }
}
