package com.campus.perks.analytics.serviceImpl;

import com.campus.perks.analytics.dto.DashboardQuery;
import com.campus.perks.analytics.entity.AnalyticsRows.*;
import com.campus.perks.analytics.mapper.DashboardMapper;
import com.campus.perks.analytics.service.*;
import com.campus.perks.analytics.vo.DashboardSummary;
import com.campus.perks.analytics.vo.DashboardSummary.*;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.port.*;
import com.campus.perks.security.port.SessionStatePort;
import com.campus.perks.security.service.*;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
@EnableConfigurationProperties(AnalyticsProperties.class)
public class DashboardServiceImpl implements DashboardService {
  private final DashboardMapper db;
  private final AnalyticsProperties p;
  private final Clock clock;
  private final AccountDirectoryPort accounts;
  private final MerchantAccessPort merchants;
  private final SessionStatePort sessions;
  private final TransactionTemplate tx;

  private record CacheKey(
      String scope, String merchant, LocalDate from, LocalDate to, String zone) {}

  private final Map<CacheKey, DashboardSummary> cache = new ConcurrentHashMap<>();

  public DashboardServiceImpl(
      DashboardMapper db,
      AnalyticsProperties p,
      Clock clock,
      AccountDirectoryPort accounts,
      MerchantAccessPort merchants,
      SessionStatePort sessions,
      PlatformTransactionManager manager) {
    this.db = db;
    this.p = p;
    this.clock = clock;
    this.accounts = accounts;
    this.merchants = merchants;
    this.sessions = sessions;
    tx = new TransactionTemplate(manager);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
  }

  private ApplicationException error(HttpStatus status, String code, String detail) {
    return new ApplicationException(status, code, detail);
  }

  private SessionStatePort.State actor(AccountPortal portal) {
    CurrentActor.requirePortal(portal);
    accounts.lockActiveUser(CurrentActor.id());
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (!(auth instanceof JwtAuthenticationToken jwt))
      throw error(HttpStatus.UNAUTHORIZED, "INVALID_SESSION", "请重新登录");
    var state = sessions.load(CurrentActor.id(), jwt.getToken().getClaimAsString("sid"));
    if (!state.active() || !state.accountType().equals(CurrentActor.portal().name()))
      throw error(HttpStatus.UNAUTHORIZED, "INVALID_SESSION", "会话或账号授权已变更");
    return state;
  }

  private Window window(DashboardQuery q) {
    try {
      String zone = q.getZone() == null ? p.defaultZone() : q.getZone();
      if (!p.allowedZones().contains(zone))
        throw error(HttpStatus.BAD_REQUEST, "INVALID_ANALYTICS_ZONE", "统计时区不在允许范围内");
      var zoneId = ZoneId.of(zone);
      var today = LocalDate.ofInstant(clock.instant(), zoneId);
      LocalDate from = q.getFrom(), to = q.getTo();
      if ((from == null) != (to == null))
        throw error(HttpStatus.BAD_REQUEST, "INVALID_ANALYTICS_RANGE", "开始和结束日期需要同时填写");
      if (from == null) {
        to = today;
        from = to.minusDays(p.defaultDays() - 1L);
      }
      long size = ChronoUnit.DAYS.between(from, to) + 1;
      if (size < 1 || size > p.maxDays() || to.isAfter(today))
        throw error(
            HttpStatus.BAD_REQUEST,
            "INVALID_ANALYTICS_RANGE",
            "统计日期不可晚于今天，且范围最多 " + p.maxDays() + " 天");
      var days = new ArrayList<Day>();
      for (var date = from; !date.isAfter(to); date = date.plusDays(1))
        days.add(
            new Day(
                date,
                date.atStartOfDay(zoneId).toInstant(),
                date.plusDays(1).atStartOfDay(zoneId).toInstant()));
      return new Window(
          from, to, zone, days.get(0).start(), days.get(days.size() - 1).end(), List.copyOf(days));
    } catch (DateTimeException invalid) {
      throw error(HttpStatus.BAD_REQUEST, "INVALID_ANALYTICS_RANGE", "统计日期或时区不合法");
    }
  }

  public DashboardSummary query(DashboardQuery q) {
    if (q.getMerchantId() == null)
      throw error(HttpStatus.BAD_REQUEST, "MERCHANT_REQUIRED", "请指定需要统计的门店");
    return merchant(q.getMerchantId(), q);
  }

  public DashboardSummary merchant(String merchant, DashboardQuery q) {
    if (merchant == null || !merchant.matches("[1-9][0-9]{0,17}"))
      throw error(HttpStatus.NOT_FOUND, "MERCHANT_NOT_FOUND", "门店不存在");
    return tx.execute(
        s -> {
          var state = actor(AccountPortal.MERCHANT);
          var scope = merchants.requireMerchantAnalyticsReader(merchant);
          if (!Set.of("OWNER", "MANAGER").contains(scope.role())
              || !state.permissions().contains("merchant:analytics:read"))
            throw error(HttpStatus.FORBIDDEN, "MERCHANT_ANALYTICS_FORBIDDEN", "仅店主或店长可以查看本店经营统计");
          return report("MERCHANT", merchant, scope.name(), q);
        });
  }

  public DashboardSummary platform(DashboardQuery q) {
    return tx.execute(
        s -> {
          if (!actor(AccountPortal.ADMIN).permissions().contains("analytics:read"))
            throw error(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权查看平台统计");
          return report("PLATFORM", null, null, q);
        });
  }

  private static final class Bucket {
    final Map<String, Long> counts = new HashMap<>();
    BigDecimal paid = BigDecimal.ZERO, refund = BigDecimal.ZERO;

    void add(Event event) {
      counts.merge(event.kind(), event.count(), Long::sum);
      if (event.kind().equals("PAYMENT")) paid = paid.add(event.amount());
      if (event.kind().equals("REFUND")) refund = refund.add(event.amount());
    }

    long count(String kind) {
      return counts.getOrDefault(kind, 0L);
    }
  }

  private BigDecimal money(BigDecimal amount) {
    return amount.setScale(2, RoundingMode.UNNECESSARY);
  }

  private DashboardSummary report(String scope, String merchant, String name, DashboardQuery q) {
    var w = window(q);
    var key = new CacheKey(scope, merchant, w.from(), w.to(), w.zone());
    var now = clock.instant();
    // Every hit comes after the current account, session, permission and membership guards.
    var cached = cache.get(key);
    if (!q.isRefresh() && cached != null && cached.cacheExpiresAt().isAfter(now)) return cached;
    var totals = new Bucket();
    var days = new LinkedHashMap<LocalDate, Bucket>();
    w.days().forEach(d -> days.put(d.date(), new Bucket()));
    for (var event : db.events(w, merchant)) {
      days.get(event.date()).add(event);
      totals.add(event);
    }
    long claims = totals.count("CLAIM"), cohort = db.cohortRedeemed(w, merchant);
    BigDecimal rate =
        claims == 0
            ? null
            : BigDecimal.valueOf(cohort)
                .divide(BigDecimal.valueOf(claims), 4, RoundingMode.HALF_UP);
    var metrics =
        new Metrics(
            claims,
            totals.count("COUPON_REDEEMED"),
            cohort,
            rate,
            totals.count("ORDER_CREATED"),
            totals.count("PAYMENT"),
            totals.count("ORDER_REDEEMED"),
            totals.count("REFUND"),
            db.participants(w, merchant),
            money(totals.paid),
            money(totals.refund),
            money(totals.paid.subtract(totals.refund)));
    var daily =
        days.entrySet().stream()
            .map(
                e -> {
                  var b = e.getValue();
                  return new Daily(
                      e.getKey(),
                      b.count("CLAIM"),
                      b.count("COUPON_REDEEMED"),
                      b.count("ORDER_CREATED"),
                      b.count("PAYMENT"),
                      b.count("ORDER_REDEEMED"),
                      b.count("REFUND"),
                      money(b.paid),
                      money(b.refund));
                })
            .toList();
    var result =
        new DashboardSummary(
            scope,
            merchant,
            name,
            w.from(),
            w.to(),
            w.zone(),
            w.start(),
            w.end(),
            now,
            now.plus(p.cacheTtl()),
            "ALIPAY_SANDBOX",
            "CNY",
            metrics,
            daily,
            db.top(w, merchant, p.topCoupons()),
            db.statuses(w, merchant),
            merchant == null ? db.newAccounts(w) : null,
            merchant == null ? db.newMerchants(w) : null,
            false,
            null,
            p.allowedZones(),
            p.maxDays(),
            List.of(
                "金额是支付宝沙箱CNY统计，不代表真实营业收入。",
                "支付额按成功支付时间计入，退款不会抹去原支付额；退款额仅按成功退款完成时间计入。",
                "净收款变动=本窗口支付额-本窗口成功退款额，跨窗口退款时可能为负，不等同于净利润。",
                "领券量按领取时间；核销次数按实际核销时间，可能包含本窗口以前领取的券。",
                "领取批次核销率=本窗口领取且截至计算时已核销的券数/本窗口领券数；无领券时为空。",
                "参与人数按本窗口领券或创建套餐订单的账号去重，不是浏览UV；浏览UV尚未采集。",
                "每日分组使用所选时区的日界线，查询区间左闭右开；订单状态按本窗口创建订单的当前状态统计。",
                "短期缓存不缓存权限；可刷新重新计算，暂停门店的历史数据仍保留。"));
    if (!p.cacheTtl().isZero()) {
      synchronized (cache) {
        if (cache.size() >= p.maxCacheEntries()) cache.clear();
        cache.put(key, result);
      }
    }
    return result;
  }
}
