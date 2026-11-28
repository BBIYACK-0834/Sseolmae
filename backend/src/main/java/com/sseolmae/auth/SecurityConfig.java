package com.sseolmae.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.util.StringUtils;

@Configuration
public class SecurityConfig {
  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      @Value("${venue.kakao-id}") String id,
      @Value("${venue.kakao-secret}") String secret,
      @Value("${venue.origin}") String origin)
      throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/session", "/api/experiences", "/api/slots", "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            errors ->
                errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .requestCache(cache -> cache.disable())
        .logout(
            logout ->
                logout
                    .logoutUrl("/api/logout")
                    .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)));
    if (StringUtils.hasText(id) && StringUtils.hasText(secret)) {
      var kakao =
          ClientRegistration.withRegistrationId("kakao")
              .clientName("Kakao")
              .clientId(id)
              .clientSecret(secret)
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri(origin + "/login/oauth2/code/kakao")
              .authorizationUri("https://kauth.kakao.com/oauth/authorize")
              .tokenUri("https://kauth.kakao.com/oauth/token")
              .userInfoUri("https://kapi.kakao.com/v2/user/me")
              .userNameAttributeName("id")
              .build();
      http.oauth2Login(
          oauth ->
              oauth
                  .clientRegistrationRepository(new InMemoryClientRegistrationRepository(kakao))
                  .defaultSuccessUrl(origin + "/#booking", true)
                  .failureUrl(origin + "/?login=failed#booking"));
    }
    return http.build();
  }
}
