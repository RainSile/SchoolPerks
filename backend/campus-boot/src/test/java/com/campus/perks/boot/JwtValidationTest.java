package com.campus.perks.boot;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("skeleton")
class JwtValidationTest {
  private static final byte[] KEY = new byte[32];

  static {
    new SecureRandom().nextBytes(KEY);
  }

  @Autowired MockMvc mvc;

  @DynamicPropertySource
  static void security(DynamicPropertyRegistry registry) {
    registry.add("campus.security.jwt.enabled", () -> true);
    registry.add(
        "campus.security.jwt.secret-base64", () -> Base64.getEncoder().encodeToString(KEY));
  }

  @Test
  void validSignedTokenReachesProtectedService() throws Exception {
    mvc.perform(
            get("/api/v1/users/me")
                .header(
                    "Authorization",
                    "Bearer " + token("campus-web", Instant.now().plusSeconds(300))))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void wrongAudienceAndExpiredTokensAreRejected() throws Exception {
    mvc.perform(
            get("/api/v1/users/me")
                .header(
                    "Authorization",
                    "Bearer " + token("other-app", Instant.now().plusSeconds(300))))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/v1/users/me")
                .header(
                    "Authorization",
                    "Bearer " + token("campus-web", Instant.now().minusSeconds(300))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void validIdentityStillNeedsAdminPermission() throws Exception {
    mvc.perform(
            get("/api/v1/admin/merchant-reviews")
                .header(
                    "Authorization",
                    "Bearer " + token("campus-web", Instant.now().plusSeconds(300))))
        .andExpect(status().isForbidden());
  }

  private String token(String audience, Instant expires) {
    var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(KEY));
    var claims =
        JwtClaimsSet.builder()
            .issuer("campus-perks")
            .subject("test-user")
            .audience(List.of(audience))
            .expiresAt(expires)
            .build();
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }
}
