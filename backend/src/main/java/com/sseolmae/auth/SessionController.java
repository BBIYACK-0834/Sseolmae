package com.sseolmae.auth;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class SessionController {
  private final Accounts accounts;
  private final SocialProviders providers;

  public SessionController(Accounts accounts, SocialProviders providers) {
    this.accounts = accounts;
    this.providers = providers;
  }

  public record Session(UUID userId, boolean kakaoReady, java.util.List<String> oauthProviders, String clientKey, String csrf) {}

  @GetMapping("/api/session")
  Session session(
      Authentication auth,
      CsrfToken csrf,
      @Value("${venue.toss-client}") String clientKey,
      @Value("${venue.toss-secret}") String paymentSecret) {
    return new Session(
        auth == null ? null : accounts.id(auth),
        providers.enabled().contains("kakao"),
        providers.enabled(),
        paymentSecret.isBlank() ? "" : clientKey,
        csrf.getToken());
  }
}
