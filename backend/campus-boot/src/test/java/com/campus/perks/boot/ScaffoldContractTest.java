package com.campus.perks.boot;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("skeleton")
class ScaffoldContractTest {
  @Autowired MockMvc mvc;

  @Test
  void bootsWithoutDatabaseOrExternalServices() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/platform"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stage").value("SCAFFOLD"));
  }

  @Test
  void swaggerAndKnife4jAreActuallyServed() throws Exception {
    mvc.perform(get("/doc.html")).andExpect(status().isOk());
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/merchants'].get.summary").exists())
        .andExpect(jsonPath("$.paths['/api/v1/assistant/conversations'].post").exists())
        .andExpect(jsonPath("$.paths['/api/v1/admin/dashboard'].get.summary").exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/merchants/{merchantId}/dashboard'].get.summary").exists())
        .andExpect(jsonPath("$.paths['/api/v1/admin/operation-audit-events'].get.summary").exists())
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
  }

  @Test
  void dashboardsAndOperationalAuditNeverReturnFakeSuccessWithoutDatabase() throws Exception {
    var admin =
        jwt()
            .authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                    "PORTAL_ADMIN"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                    "analytics:read"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                    "audit:read"));
    mvc.perform(get("/api/v1/admin/dashboard").with(admin))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("ANALYTICS_UNAVAILABLE"));
    mvc.perform(get("/api/v1/admin/operation-audit-events").with(admin))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("AUDIT_UNAVAILABLE"));
  }

  @Test
  void validationAndUnfinishedFeaturesUseRealHttpStatuses() throws Exception {
    mvc.perform(get("/api/v1/merchants").param("size", "101")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/merchants").param("page", "0")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/merchants"))
        .andExpect(status().isNotImplemented())
        .andExpect(jsonPath("$.code").value("FEATURE_NOT_IMPLEMENTED"));
    mvc.perform(get("/api/v1/coupons"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("COUPON_UNAVAILABLE"));
  }

  @Test
  void protectedResourcesCannotBeAccessedAnonymously() throws Exception {
    mvc.perform(get("/api/v1/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    mvc.perform(get("/api/v1/admin/merchant-reviews").with(jwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void allowedOriginsAreRestricted() throws Exception {
    mvc.perform(
            options("/api/v1/merchants")
                .header("Origin", "https://untrusted.invalid")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden());
  }

  @Test
  void disabledAuthenticationIsExplicitAndNeverPretendsToSendSms() throws Exception {
    mvc.perform(get("/api/v1/authentication-capabilities"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false))
        .andExpect(jsonPath("$.smsAvailable").value(false));
    mvc.perform(
            post("/api/v1/verification-challenges")
                .contentType("application/json")
                .content("{\"phone\":\"13900000000\",\"purpose\":\"LOGIN\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("AUTH_UNAVAILABLE"));
  }
}
