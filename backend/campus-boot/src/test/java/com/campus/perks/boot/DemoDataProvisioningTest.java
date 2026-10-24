package com.campus.perks.boot;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.campus.perks.community.dto.CommunityRequests.*;
import com.campus.perks.community.service.PostService;
import com.campus.perks.coupon.dto.*;
import com.campus.perks.coupon.service.CouponService;
import com.campus.perks.coupon.vo.MerchantCoupon;
import com.campus.perks.integration.contract.SearchChangeEvents;
import com.campus.perks.integration.port.CampusReviewPort;
import com.campus.perks.notification.serviceImpl.*;
import com.campus.perks.security.service.AccessTokenIssuer;
import com.campus.perks.user.dto.CreateCampusVerificationRequest;
import com.campus.perks.user.service.*;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.Statement;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Explicit user-authorized DATA IMPORT, not a normal rollback fixture. Never runs at startup or
 * unless CAMPUS_DEMO_SEED=true and a private input file is supplied. Leaves imported demo rows. No
 * SMS/password-login bypass, mock provider, fake storage object or fake payment is installed.
 */
@EnabledIfEnvironmentVariable(named = "CAMPUS_DEMO_SEED", matches = "true")
@SpringBootTest(
    properties = {
      "campus.notifications.dispatch-enabled=false",
      "campus.search.rebuild-enabled=false",
      "campus.trade.scheduler-enabled=false"
    })
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("local")
class DemoDataProvisioningTest {
  private static final String IMPORT_ID = "demo-data-import-20261023";
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @Autowired PlatformTransactionManager transactions;
  @Autowired CouponService coupons;
  @Autowired PostService posts;
  @Autowired UserService profiles;
  @Autowired UserPermissions permissions;
  @Autowired CampusReviewPort campusReviews;
  @Autowired SearchChangeEvents searchEvents;
  @Autowired OutboxDispatcher dispatcher;
  @Autowired RabbitOutboxTransport rabbit;
  @Autowired MockMvc mvc;
  @Autowired AccessTokenIssuer issuer;
  @Autowired Clock clock;
  private final Map<String, Long> users = new LinkedHashMap<>(),
      schools = new LinkedHashMap<>(),
      stores = new LinkedHashMap<>(),
      couponIds = new LinkedHashMap<>(),
      offerIds = new LinkedHashMap<>(),
      postIds = new LinkedHashMap<>();
  private final Map<String, String> claims = new LinkedHashMap<>();
  private final List<String> verificationSessions = new ArrayList<>();
  private final Map<String, Account> accounts = new LinkedHashMap<>();
  private final Map<String, Object> manifest = new LinkedHashMap<>();
  private Instant now;

  private record Account(String key, String phone, String name, String type, String password) {
    @Override
    public String toString() {
      return "DemoAccount[REDACTED]";
    }
  }

  private long insert(String sql, Object... args) {
    var key = new GeneratedKeyHolder();
    db.update(
        c -> {
          var p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
          return p;
        },
        key);
    return key.getKey().longValue();
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("Demo serialization failed");
    }
  }

  private <T> T as(String key, Supplier<T> work) {
    long id = users.get(key);
    var authorities = new ArrayList<SimpleGrantedAuthority>();
    permissions.forUser(id).forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
    authorities.add(new SimpleGrantedAuthority("PORTAL_" + accounts.get(key).type()));
    var before = SecurityContextHolder.getContext();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        new JwtAuthenticationToken(
            Jwt.withTokenValue("data-import-only").header("alg", "none").subject("" + id).build(),
            authorities));
    SecurityContextHolder.setContext(context);
    try {
      return work.get();
    } finally {
      SecurityContextHolder.setContext(before);
    }
  }

  private void audit(String resourceType, Object id, String action) {
    db.update(
        "INSERT INTO"
            + " campus_audit_event(id,actor_id,action,resource_type,resource_id,details,created_at)"
            + " VALUES(?,?,?,?,?,?,?)",
        UUID.randomUUID().toString(),
        users.get("admin"),
        action,
        resourceType,
        "" + id,
        encode(Map.of("source", IMPORT_ID, "demoOnly", true)),
        now);
  }

  private void accountData() {
    var encoder = new BCryptPasswordEncoder(12);
    var roles = new HashMap<String, Long>();
    for (String type : List.of("USER", "MERCHANT", "ADMIN"))
      roles.put(
          type,
          insert(
              "INSERT INTO campus_role(code,name,account_type) VALUES(?,?,?)",
              "DEMO_" + type,
              "演示·" + type,
              type));
    db.update(
        "INSERT INTO campus_role_permission(role_id,permission_id) SELECT ?,id FROM"
            + " campus_permission",
        roles.get("ADMIN"));
    for (var a : accounts.values()) {
      if (a.password() == null
          || a.password().length() < 16
          || !a.phone().matches("1[3-9][0-9]{9}"))
        throw new IllegalArgumentException("Invalid private demo account input");
      if (db.queryForObject(
              "SELECT COUNT(*) FROM campus_user WHERE phone=?", Integer.class, a.phone())
          != 0) throw new IllegalStateException("Account collision; existing account preserved");
      long id =
          insert(
              "INSERT INTO campus_user(phone,nickname,password_hash,account_type) VALUES(?,?,?,?)",
              a.phone(),
              a.name(),
              encoder.encode(a.password()),
              a.type());
      users.put(a.key(), id);
      db.update(
          "INSERT INTO campus_user_role(user_id,role_id) VALUES(?,?)", id, roles.get(a.type()));
    }
    for (long id : users.values()) audit("USER", id, "DEMO_ACCOUNT_IMPORTED");
  }

  private void schoolData() {
    schools.put("haitang", insert("INSERT INTO campus_school(name) VALUES(?)", "演示·海棠大学"));
    schools.put("qinghe", insert("INSERT INTO campus_school(name) VALUES(?)", "演示·青禾职业技术学院"));
    var request =
        as(
            "verified",
            () ->
                profiles.apply(
                    "" + users.get("verified"),
                    new CreateCampusVerificationRequest(
                        "" + schools.get("haitang"), "演示学生", "DEMO20261023")));
    as("admin", () -> campusReviews.decide(request.id(), "APPROVED", "演示数据：虚构学生身份，仅用于本机功能验收"));
  }

  private void store(
      String key,
      String owner,
      String school,
      String name,
      String category,
      String address,
      String lon,
      String lat) {
    long id =
        insert(
            "INSERT INTO"
                + " campus_merchant(owner_id,school_id,name,category,address,status,description,business_hours)"
                + " VALUES(?,?,?,?,?,'APPROVED',?,?)",
            users.get(owner),
            schools.get(school),
            name,
            category,
            address,
            "演示门店，仅供功能验收；无真实资质文件，不能代替正式商家核验。",
            "周一至周日 09:00-21:30");
    stores.put(key, id);
    db.update(
        "INSERT INTO campus_merchant_member(merchant_id,user_id,member_role) VALUES(?,?,'OWNER')",
        id,
        users.get(owner));
    db.update(
        "INSERT INTO"
            + " campus_merchant_location(merchant_id,longitude,latitude,coordinate_system,version,updated_at)"
            + " VALUES(?,?,?,'WGS84',1,?)",
        id,
        new BigDecimal(lon),
        new BigDecimal(lat),
        now);
    audit("MERCHANT", id, "DEMO_MERCHANT_IMPORTED");
    searchEvents.changed("MERCHANT", "" + id);
  }

  private void merchantData() {
    store(
        "noodles",
        "ownerA",
        "haitang",
        "演示·海棠巷面馆",
        "DINING",
        "海棠大学东门演示街1号",
        "116.3500000",
        "39.9900000");
    store(
        "tea",
        "ownerA",
        "haitang",
        "演示·青柠茶铺",
        "DINING",
        "海棠大学东门演示街6号",
        "116.3520000",
        "39.9905000");
    store(
        "print",
        "ownerA",
        "haitang",
        "演示·知行文印店",
        "LIFE_SERVICE",
        "海棠大学南门演示街3号",
        "116.3490000",
        "39.9875000");
    store(
        "sports",
        "ownerB",
        "qinghe",
        "演示·青禾运动馆",
        "ENTERTAINMENT",
        "青禾学院西门演示街9号",
        "116.3600000",
        "39.9920000");
    for (String member : List.of("manager", "cashier"))
      db.update(
          "INSERT INTO campus_merchant_member(merchant_id,user_id,member_role) VALUES(?,?,?)",
          stores.get("noodles"),
          users.get(member),
          member.equals("manager") ? "MANAGER" : "CASHIER");
  }

  private String owner(String store) {
    return store.equals("sports") ? "ownerB" : "ownerA";
  }

  private void coupon(
      String key,
      String store,
      String title,
      String type,
      String threshold,
      String value,
      int stock,
      boolean campus,
      String state,
      boolean future) {
    var rules =
        new CouponRules(
            new BigDecimal(threshold),
            type.equals("AMOUNT_OFF") ? new BigDecimal(value) : null,
            type.equals("DISCOUNT") ? new BigDecimal(value) : null);
    Instant from = future ? now.plus(Duration.ofDays(1)) : now.minus(Duration.ofHours(1)),
        until = now.plus(Duration.ofDays(14));
    String description = "演示优惠，仅用于本机验收；" + (campus ? "需海棠大学校园认证。" : "领取后到店核销，不涉及真实支付。 ");
    long id =
        insert(
            "INSERT INTO"
                + " campus_coupon(merchant_id,title,coupon_type,rules,total_stock,remaining_stock,valid_from,valid_until,description,campus_only)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?)",
            stores.get(store),
            title,
            type,
            encode(rules),
            stock,
            stock,
            from,
            until,
            description,
            campus);
    couponIds.put(key, id);
    if (!state.equals("DRAFT")) {
      String review = UUID.randomUUID().toString();
      var snapshot =
          new MerchantCoupon(
              "" + id,
              "" + stores.get(store),
              title,
              type,
              rules,
              stock,
              stock,
              from,
              until,
              "DRAFT",
              0,
              description,
              campus,
              "NONE",
              null,
              null,
              null);
      db.update(
          "INSERT INTO"
              + " campus_coupon_review_request(id,coupon_id,submitted_by,content_version,content_snapshot,status,created_at)"
              + " VALUES(?,?,?,0,?,'PENDING',?)",
          review,
          id,
          users.get(owner(store)),
          encode(snapshot),
          now);
      db.update(
          "UPDATE campus_coupon SET review_status='PENDING',review_request_id=?,version=1 WHERE"
              + " id=?",
          review,
          id);
      if (!state.equals("PENDING")) {
        as(
            "admin",
            () ->
                coupons.decide(
                    review,
                    new ReviewDecisionRequest(
                        state.equals("REJECTED") ? "REJECTED" : "APPROVED",
                        "演示数据审核：" + (state.equals("REJECTED") ? "请补充活动适用说明" : "规则已核对"))));
        if (!state.equals("REJECTED")) {
          db.update(
              "UPDATE campus_coupon SET status=?,version=?,published_at=? WHERE id=?",
              state.equals("OFFLINE") ? "OFFLINE" : "PUBLISHED",
              state.equals("OFFLINE") ? 4 : 3,
              now,
              id);
          db.update(
              "INSERT INTO"
                  + " campus_coupon_status_change(id,coupon_id,actor_id,previous_status,status,version,reason,created_at)"
                  + " VALUES(?,?,?,'DRAFT','PUBLISHED',3,?,?)",
              UUID.randomUUID().toString(),
              id,
              users.get(owner(store)),
              "演示数据导入：预置已发布优惠，不代表真实资质审核",
              now);
          if (state.equals("OFFLINE"))
            db.update(
                "INSERT INTO"
                    + " campus_coupon_status_change(id,coupon_id,actor_id,previous_status,status,version,reason,created_at)"
                    + " VALUES(?,?,?,'PUBLISHED','OFFLINE',4,?,?)",
                UUID.randomUUID().toString(),
                id,
                users.get(owner(store)),
                "演示：活动下架，对照公开列表过滤",
                now);
        }
      }
    }
    audit("COUPON", id, "DEMO_COUPON_IMPORTED");
    searchEvents.changed("COUPON", "" + id);
  }

  private void couponData() {
    coupon(
        "noodles5",
        "noodles",
        "演示·面馆满30减5元",
        "AMOUNT_OFF",
        "30",
        "5",
        100,
        false,
        "PUBLISHED",
        false);
    coupon(
        "noodles10",
        "noodles",
        "演示·双人餐满60减10元",
        "AMOUNT_OFF",
        "60",
        "10",
        80,
        false,
        "PUBLISHED",
        false);
    coupon(
        "student",
        "noodles",
        "演示·海棠学生专享满20减6元",
        "AMOUNT_OFF",
        "20",
        "6",
        60,
        true,
        "PUBLISHED",
        false);
    coupon("tea85", "tea", "演示·青柠茶饮85折券", "DISCOUNT", "0", "0.85", 120, false, "PUBLISHED", false);
    coupon("tea5", "tea", "演示·双杯茶饮满35减5元", "AMOUNT_OFF", "35", "5", 100, false, "PUBLISHED", false);
    coupon(
        "print8", "print", "演示·文印装订8折券", "DISCOUNT", "10", "0.80", 150, false, "PUBLISHED", false);
    coupon(
        "sport10",
        "sports",
        "演示·运动馆满50减10元",
        "AMOUNT_OFF",
        "50",
        "10",
        80,
        false,
        "PUBLISHED",
        false);
    coupon("future", "tea", "演示·明日开抢满20减4元", "AMOUNT_OFF", "20", "4", 50, false, "PUBLISHED", true);
    coupon(
        "soldout", "tea", "演示·限量尝鲜券（已领完）", "AMOUNT_OFF", "15", "3", 1, false, "PUBLISHED", false);
    coupon("draft", "noodles", "演示·新品优惠草稿", "AMOUNT_OFF", "25", "5", 50, false, "DRAFT", false);
    coupon(
        "pending",
        "noodles",
        "演示·周末学生套餐（待审核）",
        "AMOUNT_OFF",
        "40",
        "8",
        80,
        false,
        "PENDING",
        false);
    coupon(
        "rejected", "tea", "演示·说明待完善（已驳回）", "DISCOUNT", "0", "0.90", 60, false, "REJECTED", false);
    coupon(
        "offline", "sports", "演示·旧活动（已下架）", "AMOUNT_OFF", "30", "5", 40, false, "OFFLINE", false);
    claims.put(
        "available", as("user", () -> coupons.claim("" + couponIds.get("noodles5")).value().id()));
    claims.put(
        "redeemed", as("user", () -> coupons.claim("" + couponIds.get("noodles10")).value().id()));
    claims.put(
        "soldout", as("verified", () -> coupons.claim("" + couponIds.get("soldout")).value().id()));
    String code = as("user", () -> coupons.ticket(claims.get("redeemed"))).code();
    as(
        "cashier",
        () -> coupons.redeem(new CreateRedemptionRequest("" + stores.get("noodles"), code)));
  }

  private void offer(
      String key, String store, String title, String kind, String price, String state) {
    long id =
        insert(
            "INSERT INTO"
                + " campus_trade_offer(merchant_id,title,description,kind,price,stock_total,stock_available,starts_at,ends_at,group_size,group_duration_seconds,status,created_by,created_at,version)"
                + " VALUES(?,?,?,?,?,60,60,?,?,?,?,?,?,?,1)",
            stores.get(store),
            title,
            "演示套餐，仅用于下单与状态验收，未接真实支付。",
            kind,
            new BigDecimal(price),
            now.minus(Duration.ofHours(1)),
            now.plus(Duration.ofDays(14)),
            kind.equals("GROUP") ? 2 : 1,
            kind.equals("GROUP") ? 3600 : 0,
            state,
            users.get(owner(store)),
            now);
    offerIds.put(key, id);
    audit("OFFER", id, "DEMO_OFFER_IMPORTED");
    searchEvents.changed("OFFER", "" + id);
  }

  private void offerData() {
    offer("lunch", "noodles", "演示·招牌牛肉面单人餐", "REGULAR", "19.90", "PUBLISHED");
    offer("tea", "tea", "演示·青柠双杯下午茶", "REGULAR", "22.00", "PUBLISHED");
    offer("printing", "print", "演示·论文打印装订套餐", "REGULAR", "29.90", "PUBLISHED");
    offer("seckill", "tea", "演示·限量秒杀柠檬茶", "SECKILL", "6.90", "PUBLISHED");
    offer("group", "sports", "演示·双人运动体验拼团", "GROUP", "15.00", "PUBLISHED");
    offer("offline", "noodles", "演示·已下架旧套餐", "REGULAR", "18.00", "OFFLINE");
  }

  private String post(String key, String store, String title, String body, String state) {
    String school = store.equals("sports") ? "qinghe" : "haitang";
    var created =
        as(
            "user",
            () ->
                posts.create(
                    new CreatePost(
                        "" + schools.get(school),
                        "" + stores.get(store),
                        new Content(title, body, List.of()))));
    postIds.put(key, Long.parseLong(created.id()));
    if (!state.equals("DRAFT")) {
      var review = as("user", () -> posts.submit(created.id(), new Version(created.version())));
      if (state.equals("PUBLISHED"))
        as(
            "admin",
            () ->
                posts.decide(
                    review.id(), new Decision(ReviewDecision.APPROVED, "演示探店内容已核对，非真实消费评价")));
    }
    return created.id();
  }

  private void communityData() {
    String first =
        post(
            "noodles",
            "noodles",
            "演示·东门面馆午餐怎么选",
            "这是功能验收示例，不是真实探店评价。可以查看面馆满减券、领券后在券包获取核销码。",
            "PUBLISHED");
    post("tea", "tea", "演示·下午茶与茶饮优惠攻略", "演示茶铺有85折券和双杯满减券，注意活动开始时间、库存和适用门店。", "PUBLISHED");
    post("sports", "sports", "演示·周末运动与拼团体验", "演示运动馆提供双人拼团套餐。真实支付尚未接入，这里只展示活动与状态入口。", "PUBLISHED");
    post("pending", "print", "演示·毕业论文装订体验（待审核）", "演示待审核帖子，仅本人和审核管理员可见。", "PENDING");
    post("draft", "tea", "演示·尚未提交的草稿", "演示草稿，不在公开社区显示。", "DRAFT");
    var comment =
        as(
                "verified",
                () ->
                    posts.comment(
                        first,
                        new CommentInput("演示评论：券的适用门店和有效期很清楚。"),
                        UUID.randomUUID().toString()))
            .value();
    as(
        "admin",
        () ->
            posts.moderateComment(
                comment.id(),
                new CommentDecision(comment.version(), CommentStatus.APPROVED, "演示评论审核通过")));
    as(
        "verified",
        () ->
            posts.comment(
                first, new CommentInput("演示评论：这一条仍在审核队列。"), UUID.randomUUID().toString()));
    as("verified", () -> posts.like(first, true));
    as("verified", () -> posts.report(first, new ReportInput("演示举报，请在后台核对处理流程")));
  }

  private String bearer(String key) {
    String sid = UUID.randomUUID().toString();
    long id = users.get(key);
    db.update(
        "INSERT INTO campus_auth_session(id,user_id,created_at,expires_at,access_version) SELECT"
            + " ?,id,?,?,access_version FROM campus_user WHERE id=?",
        sid,
        now,
        now.plusSeconds(600),
        id);
    verificationSessions.add(sid);
    return "Bearer " + issuer.issue("" + id, sid, List.of(), now, now.plusSeconds(300));
  }

  private void verifyImportedData() throws Exception {
    mvc.perform(get("/api/v1/coupons"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(9));
    mvc.perform(get("/api/v1/trade-offers"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(5));
    mvc.perform(get("/api/v1/posts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(3));
    for (String hidden : List.of("draft", "pending", "rejected", "offline"))
      mvc.perform(get("/api/v1/coupons/" + couponIds.get(hidden))).andExpect(status().isNotFound());
    for (String key : accounts.keySet()) {
      String token = bearer(key);
      String type = accounts.get(key).type();
      mvc.perform(get("/api/v1/users/me").header("Authorization", token))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.accountType").value(type));
      if (!type.equals("ADMIN"))
        mvc.perform(get("/api/v1/admin/roles").header("Authorization", token))
            .andExpect(status().isForbidden());
    }
    mvc.perform(
            get("/api/v1/merchants/" + stores.get("sports") + "/workspace")
                .header("Authorization", bearer("ownerA")))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/admin/roles").header("Authorization", bearer("admin")))
        .andExpect(status().isOk());
    var encoder = new BCryptPasswordEncoder();
    for (var a : accounts.values())
      assertThat(
              encoder.matches(
                  a.password(),
                  db.queryForObject(
                      "SELECT password_hash FROM campus_user WHERE id=?",
                      String.class,
                      users.get(a.key()))))
          .isTrue();
    assertThat(
            db.queryForObject(
                "SELECT remaining_stock FROM campus_coupon WHERE id=?",
                Integer.class,
                couponIds.get("soldout")))
        .isZero();
    assertThat(as("user", () -> coupons.claim("" + couponIds.get("noodles5")).value().id()))
        .isEqualTo(claims.get("available"));
  }

  @Test
  void importExplicitDemoDataAndVerifyRealQueries() throws Exception {
    Path input = Path.of(Objects.requireNonNull(System.getenv("CAMPUS_DEMO_INPUT")));
    Path output = Path.of(Objects.requireNonNull(System.getenv("CAMPUS_DEMO_MANIFEST")));
    if (!input.isAbsolute() || !output.isAbsolute())
      throw new IllegalArgumentException("Absolute private paths required");
    for (JsonNode node :
        json.readTree(Files.readString(input).replaceFirst("^\\uFEFF", "")).path("accounts")) {
      var a = json.treeToValue(node, Account.class);
      accounts.put(a.key(), a);
    }
    if (!accounts
        .keySet()
        .equals(
            new LinkedHashSet<>(
                List.of("user", "verified", "ownerA", "ownerB", "manager", "cashier", "admin"))))
      throw new IllegalArgumentException("Expected explicitly prepared account set");
    if (db.queryForObject(
            "SELECT COUNT(*) FROM campus_audit_event WHERE id=?", Integer.class, IMPORT_ID)
        != 0) {
      if (!Files.exists(output))
        throw new IllegalStateException(
            "Import exists but manifest missing; do not reseed or overwrite user data");
      System.out.println("Demo data already imported; existing data and passwords were preserved.");
      return;
    }
    now = clock.instant();
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              accountData();
              schoolData();
              merchantData();
              couponData();
              offerData();
              communityData();
              db.update(
                  "INSERT INTO"
                      + " campus_audit_event(id,actor_id,action,resource_type,resource_id,details,created_at)"
                      + " VALUES(?,?,'DEMO_DATA_IMPORTED','DEMO_DATA',?,?,?)",
                  IMPORT_ID,
                  users.get("admin"),
                  IMPORT_ID,
                  encode(Map.of("demoOnly", true)),
                  now);
              manifest.put("batch", IMPORT_ID);
              manifest.put("createdAt", now);
              manifest.put("users", users);
              manifest.put("schools", schools);
              manifest.put("stores", stores);
              manifest.put("coupons", couponIds);
              manifest.put("offers", offerIds);
              manifest.put("posts", postIds);
              manifest.put("claims", claims);
              manifest.put(
                  "events",
                  db.queryForList(
                      "SELECT id FROM campus_outbox_event WHERE created_at>=?", String.class, now));
            });
    Files.writeString(output, json.writerWithDefaultPrettyPrinter().writeValueAsString(manifest));
    try {
      verifyImportedData();
    } finally {
      for (String sid : verificationSessions)
        db.update("DELETE FROM campus_auth_session WHERE id=?", sid);
    }
    @SuppressWarnings("unchecked")
    var events = (List<String>) manifest.get("events");
    long deadline = System.nanoTime() + Duration.ofMinutes(3).toNanos();
    boolean complete;
    do {
      dispatcher.dispatch();
      rabbit.consume();
      complete =
          events.stream()
              .allMatch(
                  id ->
                      db.queryForObject(
                              "SELECT COUNT(*) FROM campus_outbox_event o WHERE o.id=? AND"
                                  + " o.status='PUBLISHED' AND"
                                  + " (o.event_type<>'SEARCH_RESOURCE_CHANGED' OR EXISTS(SELECT 1"
                                  + " FROM campus_consumed_event c WHERE"
                                  + " c.consumer_name='campus-search' AND c.event_id=o.id))",
                              Integer.class,
                              id)
                          == 1);
      if (!complete) Thread.sleep(500);
    } while (!complete && System.nanoTime() < deadline);
    assertThat(complete)
        .as("Imported data committed; external projection delivery must finish")
        .isTrue();
    System.out.println(
        "Imported 7 accounts, 2 schools, 4 merchants, 13 coupons (9 public), 6 offers (5 public), 5"
            + " posts (3 public), 3 claims and 1 redemption. Queries, role isolation and real"
            + " external projections verified. Password login remains unimplemented; no service"
            + " ports started.");
  }
}
