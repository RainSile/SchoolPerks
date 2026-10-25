package com.campus.perks.security.config;

import com.campus.perks.security.port.SessionStatePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(CampusSecurityProperties.class)
public class SecurityConfiguration {
  private static final int MIN_HMAC_KEY_BYTES = 32;

  private static org.springframework.security.authorization.AuthorizationManager<
          org.springframework.security.web.access.intercept.RequestAuthorizationContext>
      portalAccess(String portal) {
    return (authentication, context) -> {
      var permissions =
          authentication.get().getAuthorities().stream()
              .map(org.springframework.security.core.GrantedAuthority::getAuthority)
              .collect(java.util.stream.Collectors.toSet());
      return new org.springframework.security.authorization.AuthorizationDecision(
          permissions.contains(portal)
              || permissions.contains("PORTAL_ADMIN") && permissions.contains("business:manage"));
    };
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      CampusSecurityProperties props,
      ObjectMapper json,
      ObjectProvider<SessionStatePort> sessionState,
      Environment environment)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        // Stateless Bearer API only. Reconsider CSRF before adding cookie authentication.
        .cors(Customizer.withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .requestCache(cache -> cache.disable())
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (req, res, ex) -> writeProblem(json, res, 401, "UNAUTHENTICATED", "请先登录"))
                    .accessDeniedHandler(
                        (req, res, ex) -> writeProblem(json, res, 403, "FORBIDDEN", "无权访问该资源")))
        .authorizeHttpRequests(
            auth -> {
              if (props.docsEnabled()) {
                auth.requestMatchers(
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll();
              } else {
                auth.requestMatchers(
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .denyAll();
              }
              auth.requestMatchers(
                      HttpMethod.GET,
                      "/actuator/health",
                      "/api/v1/platform",
                      "/api/v1/merchants",
                      "/api/v1/coupons",
                      "/api/v1/posts",
                      "/api/v1/search-results")
                  .permitAll();
              auth.requestMatchers(
                      HttpMethod.GET,
                      "/api/v1/merchants/{id:[0-9]+}",
                      "/api/v1/coupons/{id:[0-9]+}",
                      "/api/v1/notification-stream",
                      "/api/v1/notification-capabilities")
                  .permitAll();
              auth.requestMatchers(
                      HttpMethod.POST,
                      "/api/v1/verification-challenges",
                      "/api/v1/sessions",
                      "/api/v1/password-sessions",
                      "/api/v1/registrations",
                      "/api/v1/password-resets",
                      "/api/v1/session-refreshes",
                      "/api/v1/payment-notifications/alipay-sandbox")
                  .permitAll();
              auth.requestMatchers(
                      HttpMethod.GET,
                      "/api/v1/community-capabilities",
                      "/api/v1/posts/{id:[0-9]+}",
                      "/api/v1/posts/{id:[0-9]+}/comments",
                      "/api/v1/nearby-merchants",
                      "/api/v1/search-capabilities")
                  .permitAll();
              auth.requestMatchers(
                      HttpMethod.GET,
                      "/api/v1/trade-capabilities",
                      "/api/v1/trade-offers",
                      "/api/v1/trade-offers/{id:[0-9]+}",
                      "/api/v1/trade-offers/{id:[0-9]+}/group-purchases")
                  .permitAll();
              auth.requestMatchers(
                      HttpMethod.GET,
                      "/api/v1/verification-challenges/*",
                      "/api/v1/authentication-capabilities",
                      "/api/v1/schools",
                      "/api/v1/storage-capabilities",
                      "/api/v1/merchant-categories")
                  .permitAll();
              auth.requestMatchers(HttpMethod.GET, "/api/v1/assistant/capabilities").permitAll();
              auth.requestMatchers(
                      HttpMethod.GET, "/api/v1/map-capabilities", "/api/v1/maps/_AMapService/**")
                  .permitAll();
              auth.requestMatchers("/api/v1/admin/**").hasAuthority("PORTAL_ADMIN");
              auth.requestMatchers(
                      "/api/v1/merchants/*/**",
                      "/api/v1/users/me/merchants",
                      "/api/v1/redemptions/**",
                      "/api/v1/coupons/*/management")
                  .access(portalAccess("PORTAL_MERCHANT"));
              auth.requestMatchers(HttpMethod.PATCH, "/api/v1/coupons/*", "/api/v1/trade-offers/*")
                  .access(portalAccess("PORTAL_MERCHANT"));
              auth.requestMatchers(
                      HttpMethod.POST,
                      "/api/v1/coupons/*/review-requests",
                      "/api/v1/coupons/*/status-changes")
                  .access(portalAccess("PORTAL_MERCHANT"));
              auth.requestMatchers(
                      "/api/v1/orders/**",
                      "/api/v1/group-purchases/**",
                      "/api/v1/coupon-claims/**",
                      "/api/v1/users/me/coupon-claims",
                      "/api/v1/users/me/posts/**",
                      "/api/v1/users/me/post-comments",
                      "/api/v1/users/me/campus-verifications/**",
                      "/api/v1/merchant-applications/**",
                      "/api/v1/users/me/merchant-applications")
                  .access(portalAccess("PORTAL_USER"));
              auth.requestMatchers(
                      HttpMethod.POST,
                      "/api/v1/coupons/*/claims",
                      "/api/v1/posts",
                      "/api/v1/posts/*/review-requests",
                      "/api/v1/posts/*/comments",
                      "/api/v1/posts/*/reports")
                  .access(portalAccess("PORTAL_USER"));
              auth.requestMatchers(HttpMethod.PATCH, "/api/v1/posts/*")
                  .access(portalAccess("PORTAL_USER"));
              auth.requestMatchers(HttpMethod.DELETE, "/api/v1/posts/*", "/api/v1/post-comments/*")
                  .access(portalAccess("PORTAL_USER"));
              auth.requestMatchers("/api/v1/posts/*/likes/me").access(portalAccess("PORTAL_USER"));
              auth.requestMatchers("/error").permitAll();
              auth.anyRequest().authenticated();
            });
    if (props.jwt().enabled()) {
      var converter = new JwtGrantedAuthoritiesConverter();
      converter.setAuthoritiesClaimName("permissions");
      converter.setAuthorityPrefix("");
      var authentication = new JwtAuthenticationConverter();
      authentication.setJwtGrantedAuthoritiesConverter(converter);
      http.oauth2ResourceServer(
          oauth ->
              oauth
                  .jwt(
                      jwt ->
                          jwt.jwtAuthenticationConverter(
                              token -> {
                                var statePort = sessionState.getIfAvailable();
                                if (statePort == null) {
                                  if (environment.getProperty(
                                      "campus.auth.enabled", Boolean.class, false))
                                    throw new InvalidBearerTokenException(
                                        "Session validation unavailable");
                                  return authentication.convert(token);
                                }
                                var state =
                                    statePort.load(
                                        token.getSubject(), token.getClaimAsString("sid"));
                                if (!state.active())
                                  throw new InvalidBearerTokenException("Session is inactive");
                                var authorities = new java.util.ArrayList<SimpleGrantedAuthority>();
                                state
                                    .permissions()
                                    .forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
                                try {
                                  authorities.add(
                                      new SimpleGrantedAuthority(
                                          com.campus.perks.security.service.AccountPortal.valueOf(
                                                  state.accountType())
                                              .authority()));
                                } catch (IllegalArgumentException invalid) {
                                  throw new InvalidBearerTokenException("Invalid account portal");
                                }
                                return new JwtAuthenticationToken(
                                    token, authorities, token.getSubject());
                              }))
                  .authenticationEntryPoint(
                      (req, res, ex) -> writeProblem(json, res, 401, "INVALID_TOKEN", "令牌无效或已过期"))
                  .accessDeniedHandler(
                      (req, res, ex) -> writeProblem(json, res, 403, "FORBIDDEN", "无权访问该资源")));
    }
    return http.build();
  }

  @Bean
  @ConditionalOnProperty(name = "campus.security.jwt.enabled", havingValue = "true")
  JwtDecoder jwtDecoder(CampusSecurityProperties props) {
    var jwt = props.jwt();
    byte[] key;
    try {
      key = Base64.getDecoder().decode(jwt.secretBase64());
    } catch (IllegalArgumentException ex) {
      throw new IllegalStateException("JWT_SECRET_BASE64 必须是 Base64 编码的随机密钥");
    }
    if (key.length < MIN_HMAC_KEY_BYTES || jwt.issuer().isBlank() || jwt.audience().isBlank()) {
      throw new IllegalStateException("JWT 密钥至少 32 字节，issuer 和 audience 不可为空");
    }
    var decoder =
        NimbusJwtDecoder.withSecretKey(new SecretKeySpec(key, "HmacSHA256"))
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    OAuth2TokenValidator<Jwt> claims =
        token -> {
          boolean valid =
              token.getAudience().contains(jwt.audience())
                  && token.getExpiresAt() != null
                  && token.getSubject() != null
                  && !token.getSubject().isBlank();
          return valid
              ? OAuth2TokenValidatorResult.success()
              : OAuth2TokenValidatorResult.failure(
                  new OAuth2Error("invalid_token", "Missing or invalid required claims", null));
        };
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(jwt.issuer()), claims));
    return decoder;
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(CampusSecurityProperties props) {
    var config = new CorsConfiguration();
    config.setAllowedOrigins(props.allowedOrigins());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
    config.setExposedHeaders(List.of("Location"));
    config.setAllowCredentials(false);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  private static void writeProblem(
      ObjectMapper json, HttpServletResponse response, int status, String code, String detail)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    var problem =
        ProblemDetail.forStatusAndDetail(
            org.springframework.http.HttpStatus.valueOf(status), detail);
    problem.setProperty("code", code);
    json.writeValue(response.getOutputStream(), problem);
  }
}
