package com.campus.perks.boot;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 显式启用才连接本机测试库；临时数据在测试事务结束时回滚。 */
@EnabledIfEnvironmentVariable(named = "CAMPUS_MYSQL_TEST", matches = "true")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class MySqlReadTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @Test
  void migrationAndMerchantPaginationWorkOnMysql() throws Exception {
    mvc.perform(get("/api/v1/merchants").param("page", "1").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(2))
        .andExpect(jsonPath("$.total").isNumber());
  }

  @Test
  @Transactional
  void onlyApprovedMerchantsArePublicAndFixtureIsRolledBack() throws Exception {
    String marker = UUID.randomUUID().toString();
    jdbc.update("INSERT INTO campus_school(name) VALUES (?)", marker);
    Long schoolId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update(
        "INSERT INTO campus_user(phone,nickname,school_id) VALUES (?,?,?)",
        marker.replace("-", ""),
        "temporary test user",
        schoolId);
    Long userId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update(
        "INSERT INTO campus_merchant(owner_id,school_id,name,category,address,status) VALUES"
            + " (?,?,?,?,?,?)",
        userId,
        schoolId,
        marker + "-approved",
        "TEST",
        "temporary test address",
        "APPROVED");
    jdbc.update(
        "INSERT INTO campus_merchant(owner_id,school_id,name,category,address,status) VALUES"
            + " (?,?,?,?,?,?)",
        userId,
        schoolId,
        marker + "-pending",
        "TEST",
        "temporary test address",
        "PENDING");
    mvc.perform(get("/api/v1/merchants").param("keyword", marker))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].name").value(marker + "-approved"))
        .andExpect(jsonPath("$.items[0].id").isString());
  }
}
