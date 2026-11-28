package com.sseolmae.auth;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import org.springframework.stereotype.Component;

@Component
public class SocialProviders {
  private final List<ClientRegistration> registrations;

  SocialProviders(@Value("${venue.origin}") String origin,
      @Value("${GOOGLE_CLIENT_ID:}") String googleId, @Value("${GOOGLE_CLIENT_SECRET:}") String googleSecret,
      @Value("${venue.kakao-id}") String kakaoId, @Value("${venue.kakao-secret}") String kakaoSecret,
      @Value("${NAVER_CLIENT_ID:}") String naverId, @Value("${NAVER_CLIENT_SECRET:}") String naverSecret) {
    var list = new java.util.ArrayList<ClientRegistration>();
    if (!googleId.isBlank() && !googleSecret.isBlank()) {
      list.add(CommonOAuth2Provider.GOOGLE.getBuilder("google").scope("openid")
          .issuerUri("https://accounts.google.com").clientId(googleId).clientSecret(googleSecret)
          .redirectUri(origin + "/login/oauth2/code/google").build());
    }
    if (!kakaoId.isBlank() && !kakaoSecret.isBlank()) {
      list.add(provider("kakao", origin, kakaoId, kakaoSecret, "https://kauth.kakao.com/oauth/authorize",
          "https://kauth.kakao.com/oauth/token", "https://kapi.kakao.com/v2/user/me"));
    }
    if (!naverId.isBlank() && !naverSecret.isBlank()) {
      list.add(provider("naver", origin, naverId, naverSecret, "https://nid.naver.com/oauth2.0/authorize",
          "https://nid.naver.com/oauth2.0/token", "https://openapi.naver.com/v1/nid/me"));
    }
    registrations = List.copyOf(list);
  }

  private static ClientRegistration provider(String name, String origin, String id, String secret,
      String authorization, String token, String userInfo) {
    return ClientRegistration.withRegistrationId(name).clientName(name).clientId(id).clientSecret(secret)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
        .authorizationUri(authorization).tokenUri(token).userInfoUri(userInfo).userNameAttributeName("id")
        .redirectUri(origin + "/login/oauth2/code/" + name).build();
  }
  List<ClientRegistration> registrations() { return registrations; }
  List<String> enabled() { return registrations.stream().map(ClientRegistration::getRegistrationId).toList(); }
}
