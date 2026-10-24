package com.campus.perks.analytics.mapper;

import com.campus.perks.analytics.entity.AnalyticsRows.*;
import com.campus.perks.analytics.vo.DashboardSummary.*;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read-only migration-owned views. No cross-module writes or mapper dependencies. */
@Repository
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class DashboardMapper {
  private final JdbcTemplate db;

  public DashboardMapper(JdbcTemplate db) {
    this.db = db;
  }

  private String filter(String merchant) {
    return merchant == null ? "" : " AND merchant_id=?";
  }

  private Object[] params(Window w, String merchant) {
    var args = new ArrayList<Object>(List.of(Timestamp.from(w.start()), Timestamp.from(w.end())));
    if (merchant != null) args.add(merchant);
    return args.toArray();
  }

  public List<Event> events(Window w, String merchant) {
    var sql = new StringBuilder("SELECT CASE ");
    var args = new ArrayList<Object>();
    for (var day : w.days()) {
      sql.append("WHEN occurred_at>=? AND occurred_at<? THEN ? ");
      args.add(Timestamp.from(day.start()));
      args.add(Timestamp.from(day.end()));
      args.add(day.date().toString());
    }
    sql.append(
            "END AS metric_day,kind,COUNT(*) AS event_count,COALESCE(SUM(amount),0) AS event_amount"
                + " FROM campus_analytics_event WHERE occurred_at>=? AND occurred_at<?")
        .append(filter(merchant))
        .append(" GROUP BY metric_day,kind");
    args.addAll(Arrays.asList(params(w, merchant)));
    return db.query(
        sql.toString(),
        (r, n) ->
            new Event(
                java.time.LocalDate.parse(r.getString(1)),
                r.getString(2),
                r.getLong(3),
                r.getBigDecimal(4)),
        args.toArray());
  }

  public long cohortRedeemed(Window w, String merchant) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM campus_analytics_claim WHERE claimed_at>=? AND claimed_at<? AND"
            + " redeemed_at IS NOT NULL"
            + filter(merchant),
        Long.class,
        params(w, merchant));
  }

  public long participants(Window w, String merchant) {
    return db.queryForObject(
        "SELECT COUNT(DISTINCT user_id) FROM campus_analytics_event WHERE occurred_at>=? AND"
            + " occurred_at<? AND kind IN ('CLAIM','ORDER_CREATED')"
            + filter(merchant),
        Long.class,
        params(w, merchant));
  }

  public List<CouponRank> top(Window w, String merchant, int maximum) {
    var args = new ArrayList<>(Arrays.asList(params(w, merchant)));
    args.add(maximum);
    return db.query(
        "SELECT CAST(coupon_id AS CHAR),title,COUNT(*) AS claims,SUM(CASE WHEN redeemed_at IS NOT"
            + " NULL THEN 1 ELSE 0 END) FROM campus_analytics_claim WHERE claimed_at>=? AND"
            + " claimed_at<?"
            + filter(merchant)
            + " GROUP BY coupon_id,title ORDER BY claims DESC,coupon_id LIMIT ?",
        (r, n) -> new CouponRank(r.getString(1), r.getString(2), r.getLong(3), r.getLong(4)),
        args.toArray());
  }

  public List<OrderStatus> statuses(Window w, String merchant) {
    return db.query(
        "SELECT status,COUNT(*) FROM campus_analytics_order WHERE created_at>=? AND created_at<?"
            + filter(merchant)
            + " GROUP BY status ORDER BY status",
        (r, n) -> new OrderStatus(r.getString(1), r.getLong(2)),
        params(w, merchant));
  }

  public long newAccounts(Window w) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM campus_analytics_account WHERE created_at>=? AND created_at<?",
        Long.class,
        params(w, null));
  }

  public long newMerchants(Window w) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM campus_analytics_merchant WHERE created_at>=? AND created_at<?",
        Long.class,
        params(w, null));
  }
}
