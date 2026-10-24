package com.campus.perks.security.service;

import com.campus.perks.security.config.CampusSecurityProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "campus.security.jwt.enabled", havingValue = "true")
public class AccessTokenIssuer {
  private final JwtEncoder encoder;
  private final CampusSecurityProperties.Jwt properties;

  public AccessTokenIssuer(CampusSecurityProperties security) {
    properties = security.jwt();
    byte[] key = Base64.getDecoder().decode(properties.secretBase64());
    if (key.length < 32)
      throw new IllegalStateException("JWT key must contain at least 32 random bytes");
    encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }

  public String issue(
      String userId, String sessionId, List<String> permissions, Instant now, Instant expires) {
    var claims =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .subject(userId)
            .audience(List.of(properties.audience()))
            .issuedAt(now)
            .expiresAt(expires)
            .claim("sid", sessionId)
            .claim("permissions", permissions)
            .build();
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }
}
