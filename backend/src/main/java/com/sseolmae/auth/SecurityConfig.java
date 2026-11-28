package com.sseolmae.auth;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.*;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
  }

  static Map<String,Object> naverAttributes(Map<String,Object> attributes) {
    if (attributes.get("response") instanceof Map<?,?> profile &&
        profile.get("id") instanceof String id && !id.isBlank()) return Map.of("id",id);
    throw new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info_response"));
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http, SocialProviders providers,
      @Value("${venue.origin}") String origin) throws Exception {
    http.addFilterBefore(new AuthRequestLimit(), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
    http.authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/session", "/api/experiences", "/api/slots", "/api/auth/register", "/error").permitAll()
        .anyRequest().authenticated())
        .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .requestCache(cache -> cache.disable())
        .formLogin(form -> form.loginPage("/#login").loginProcessingUrl("/api/auth/login")
            .usernameParameter("email").successHandler((req,res,auth)->res.setStatus(204))
            .failureHandler((req,res,error)->res.setStatus(401)).permitAll())
        .logout(logout -> logout.logoutUrl("/api/logout").logoutSuccessHandler((req,res,auth)->res.setStatus(204)));
    if (!providers.registrations().isEmpty()) {
      var users=new DefaultOAuth2UserService();
      users.setAttributesConverter(request -> attributes ->
          request.getClientRegistration().getRegistrationId().equals("naver") ? naverAttributes(attributes) : attributes);
      var tokens = new org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient();
      tokens.addParametersConverter(grant -> {
        var params = new org.springframework.util.LinkedMultiValueMap<String,String>();
        if ("naver".equals(grant.getClientRegistration().getRegistrationId())) {
          params.add("state", grant.getAuthorizationExchange().getAuthorizationRequest().getState());
        }
        return params;
      });
      http.oauth2Login(oauth -> oauth.clientRegistrationRepository(new InMemoryClientRegistrationRepository(providers.registrations()))
          .tokenEndpoint(endpoint -> endpoint.accessTokenResponseClient(tokens))
          .userInfoEndpoint(info->info.userService(users))
          .defaultSuccessUrl(origin + "/#booking",true).failureUrl(origin + "/?login=failed#login"));
    }
    return http.build();
  }
}
