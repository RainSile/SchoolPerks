package com.campus.perks.boot;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.campus.perks.security.service.AccessTokenIssuer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Opt-in: one real model generation, no schema changes, exact SQL fixture cleanup. */
@EnabledIfEnvironmentVariable(named = "CAMPUS_EXTERNAL_TEST", matches = "true")
@SpringBootTest(
    properties = {
      "campus.assistant.marketing.enabled=true",
      "campus.integrations.ai.max-tokens=512",
      "campus.notifications.dispatch-enabled=false",
      "campus.search.rebuild-enabled=false",
      "campus.trade.scheduler-enabled=false"
    })
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("local")
class MarketingStageSixExternalTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @Autowired AccessTokenIssuer issuer;

  long insert(String sql, Object... args) {
    var keys = new GeneratedKeyHolder();
    db.update(
        c -> {
          var ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
          return ps;
        },
        keys);
    return keys.getKey().longValue();
  }

  @Test
  void realModelCreatesOnlyAPrivateDraftAndIdempotentReplayDoesNotBillAgain() throws Exception {
    long user = 0, school = 0, merchant = 0, coupon = 0;
    try {
      user =
          insert(
              "INSERT INTO campus_user(phone,nickname,account_type)"
                  + " VALUES(?,'stage6-live','MERCHANT')",
              "s6-" + UUID.randomUUID().toString().substring(0, 20));
      school = insert("INSERT INTO campus_school(name) VALUES(?)", "s6-live-" + UUID.randomUUID());
      merchant =
          insert(
              "INSERT INTO"
                  + " campus_merchant(owner_id,school_id,name,category,address,status,description)"
                  + " VALUES(?,?,'校园聚餐小店','DINING','test-address','APPROVED','适合朋友聚餐，欢迎同学到店')",
              user,
              school);
      db.update(
          "INSERT INTO campus_merchant_member(merchant_id,user_id,member_role) VALUES(?,?,'OWNER')",
          merchant,
          user);
      coupon =
          insert(
              "INSERT INTO"
                  + " campus_coupon(merchant_id,title,coupon_type,rules,total_stock,remaining_stock,valid_from,valid_until,status,review_status,published_at,description)"
                  + " VALUES(?,'同学聚餐满减','AMOUNT_OFF',?,10,10,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1"
                  + " HOUR),DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1"
                  + " DAY),'PUBLISHED','APPROVED',UTC_TIMESTAMP(3),'满50减8，详情以规则为准')",
              merchant,
              "{\"threshold\":50,\"amountOff\":8,\"discountRate\":null}");
      var now = Instant.now();
      String sid = UUID.randomUUID().toString();
      db.update(
          "INSERT INTO campus_auth_session(id,user_id,created_at,expires_at,access_version)"
              + " VALUES(?,?,?,?,0)",
          sid,
          user,
          Timestamp.from(now),
          Timestamp.from(now.plusSeconds(900)));
      String auth = "Bearer " + issuer.issue("" + user, sid, List.of(), now, now.plusSeconds(600));
      String key = UUID.randomUUID().toString(),
          path = "/api/v1/merchants/" + merchant + "/marketing-drafts";
      byte[] input =
          json.writeValueAsBytes(
              Map.of(
                  "brief",
                  "面向同学写一则60字以内的自然聚餐文案，不夸大、不增加资料以外的优惠。标题10字以内。",
                  "tone",
                  "CONCISE",
                  "couponId",
                  "" + coupon));
      var first =
          mvc.perform(
                  post(path)
                      .header("Authorization", auth)
                      .header("Idempotency-Key", key)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(input))
              .andExpect(status().isCreated())
              .andReturn();
      var value = json.readTree(first.getResponse().getContentAsByteArray());
      assertThat(value.path("status").asText()).isEqualTo("DRAFT");
      assertThat(value.path("title").asText()).isNotBlank();
      assertThat(value.path("body").asText()).isNotBlank();
      assertThat(value.path("sourceCurrent").asBoolean()).isTrue();
      mvc.perform(
              post(path)
                  .header("Authorization", auth)
                  .header("Idempotency-Key", key)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(input))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(value.path("id").asText()));
      assertThat(
              db.queryForObject(
                  "SELECT call_count FROM campus_ai_usage WHERE user_id=? AND kind='CHAT'",
                  Integer.class,
                  user))
          .isEqualTo(1);
      assertThat(
              db.queryForObject(
                  "SELECT COUNT(*) FROM campus_coupon WHERE merchant_id=?",
                  Integer.class,
                  merchant))
          .isEqualTo(1);
      assertThat(
              db.queryForObject(
                  "SELECT remaining_stock FROM campus_coupon WHERE id=?", Integer.class, coupon))
          .isEqualTo(10);
      db.update("UPDATE campus_coupon SET status='OFFLINE' WHERE id=?", coupon);
      mvc.perform(get(path + "/" + value.path("id").asText()).header("Authorization", auth))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.sourceCurrent").value(false));
    } finally {
      if (merchant != 0)
        db.update("DELETE FROM campus_marketing_draft WHERE merchant_id=?", merchant);
      if (user != 0) {
        db.update("DELETE FROM campus_ai_usage WHERE user_id=?", user);
        db.update("DELETE FROM campus_audit_event WHERE actor_id=?", user);
        db.update("DELETE FROM campus_auth_session WHERE user_id=?", user);
      }
      if (coupon != 0) db.update("DELETE FROM campus_coupon WHERE id=?", coupon);
      if (merchant != 0) {
        db.update("DELETE FROM campus_merchant_member WHERE merchant_id=?", merchant);
        db.update("DELETE FROM campus_merchant WHERE id=?", merchant);
      }
      if (user != 0) db.update("DELETE FROM campus_user WHERE id=?", user);
      if (school != 0) db.update("DELETE FROM campus_school WHERE id=?", school);
    }
  }
}
