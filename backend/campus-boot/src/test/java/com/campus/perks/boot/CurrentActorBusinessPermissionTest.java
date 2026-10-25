package com.campus.perks.boot;

import static org.assertj.core.api.Assertions.*;

import com.campus.perks.security.service.CurrentActor;
import java.util.Arrays;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentActorBusinessPermissionTest {
  void identity(String... permissions) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "17", "", Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void optionalPlatformCheckDoesNotBreakInternalDomainGuardsWithoutPortalMetadata() {
    SecurityContextHolder.clearContext();
    assertThat(CurrentActor.isBusinessAdministrator()).isFalse();
    identity("merchant:read");
    assertThat(CurrentActor.isBusinessAdministrator()).isFalse();
  }

  @Test
  void onlyExplicitAdminBusinessGrantEnablesExpansion() {
    identity("PORTAL_ADMIN");
    assertThat(CurrentActor.isBusinessAdministrator()).isFalse();
    identity("PORTAL_USER", "business:manage");
    assertThat(CurrentActor.isBusinessAdministrator()).isFalse();
    identity("PORTAL_MERCHANT", "business:manage");
    assertThat(CurrentActor.isBusinessAdministrator()).isFalse();
    identity("PORTAL_ADMIN", "business:manage");
    assertThat(CurrentActor.isBusinessAdministrator()).isTrue();
  }

  @Test
  void conflictingPortalIdentityNeverEnablesPlatformExpansion() {
    identity("PORTAL_ADMIN", "PORTAL_USER", "business:manage");
    assertThat(CurrentActor.isBusinessAdministrator()).isFalse();
    identity("PORTAL_ADMIN", "PORTAL_MERCHANT", "business:manage");
    assertThat(CurrentActor.isBusinessAdministrator()).isFalse();
  }
}
