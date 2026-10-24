package com.campus.perks.trade.mapper;

import com.campus.perks.trade.dto.TradeRequests.OfferInput;
import com.campus.perks.trade.entity.*;
import com.campus.perks.trade.vo.TradeViews.Refund;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** SQL belongs to the trade module. Locking reads return the current row under MySQL RR. */
@Repository
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class TradeMapper {
  private final JdbcTemplate jdbc;

  public TradeMapper(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static Instant time(ResultSet r, String name) throws SQLException {
    Timestamp value = r.getTimestamp(name);
    return value == null ? null : value.toInstant();
  }

  private static final RowMapper<OfferRow> OFFER =
      (r, n) ->
          new OfferRow(
              r.getLong("id"),
              r.getLong("merchant_id"),
              r.getString("title"),
              r.getString("description"),
              r.getString("kind"),
              r.getBigDecimal("price"),
              r.getInt("stock_total"),
              r.getInt("stock_available"),
              time(r, "starts_at"),
              time(r, "ends_at"),
              r.getInt("group_size"),
              r.getInt("group_duration_seconds"),
              r.getString("status"),
              r.getInt("version"));
  private static final RowMapper<OrderRow> ORDER =
      (r, n) ->
          new OrderRow(
              r.getLong("id"),
              r.getString("order_no"),
              r.getLong("user_id"),
              r.getLong("merchant_id"),
              r.getLong("offer_id"),
              (Long) r.getObject("group_id"),
              r.getBigDecimal("amount"),
              r.getString("offer_snapshot"),
              r.getString("status"),
              time(r, "expires_at"),
              time(r, "paid_at"),
              r.getBoolean("stock_released"),
              time(r, "created_at"));
  private static final RowMapper<GroupRow> GROUP =
      (r, n) ->
          new GroupRow(
              r.getLong("id"),
              r.getLong("offer_id"),
              r.getLong("creator_id"),
              r.getInt("target_size"),
              r.getInt("reserved_count"),
              r.getInt("paid_count"),
              r.getString("status"),
              time(r, "expires_at"));

  public record PaymentRow(
      String id, long orderId, String orderNo, String tradeNo, String status) {}

  public record RefundJob(String id, long orderId, BigDecimal amount, int attempts, String lease) {}

  private static final RowMapper<PaymentRow> PAYMENT =
      (r, n) ->
          new PaymentRow(
              r.getString("id"),
              r.getLong("order_id"),
              r.getString("provider_order_no"),
              r.getString("provider_trade_no"),
              r.getString("status"));
  private static final RowMapper<Refund> REFUND =
      (r, n) ->
          new Refund(
              r.getString("id"),
              r.getString("order_id"),
              r.getBigDecimal("amount").toPlainString(),
              r.getString("status"),
              r.getInt("attempts"),
              r.getString("last_error_code"));

  private <T> T one(String sql, RowMapper<T> row, Object... args) {
    var values = jdbc.query(sql, row, args);
    return values.isEmpty() ? null : values.get(0);
  }

  private long insert(String sql, Object... args) {
    var keys = new GeneratedKeyHolder();
    jdbc.update(
        c -> {
          var ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < args.length; i++)
            ps.setObject(i + 1, args[i] instanceof Instant t ? Timestamp.from(t) : args[i]);
          return ps;
        },
        keys);
    return Objects.requireNonNull(keys.getKey()).longValue();
  }

  public OfferRow offer(long id, boolean lock) {
    return one(
        "SELECT * FROM campus_trade_offer WHERE id=?" + (lock ? " FOR UPDATE" : ""), OFFER, id);
  }

  public List<OfferRow> publicOffers(int limit, long offset, Instant now) {
    return jdbc.query(
        "SELECT * FROM campus_trade_catalog WHERE ends_at>? ORDER BY starts_at,id LIMIT ? OFFSET ?",
        OFFER,
        Timestamp.from(now),
        limit,
        offset);
  }

  public long publicCount(Instant now) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM campus_trade_catalog WHERE ends_at>?",
        Long.class,
        Timestamp.from(now));
  }

  public OfferRow publicOffer(long id) {
    return one("SELECT * FROM campus_trade_catalog WHERE id=?", OFFER, id);
  }

  public List<OfferRow> merchantOffers(long merchant, int limit, long offset) {
    return jdbc.query(
        "SELECT * FROM campus_trade_offer WHERE merchant_id=? ORDER BY id DESC LIMIT ? OFFSET ?",
        OFFER,
        merchant,
        limit,
        offset);
  }

  public long merchantOfferCount(long merchant) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM campus_trade_offer WHERE merchant_id=?", Long.class, merchant);
  }

  public long createOffer(long merchant, long actor, OfferInput input, Instant now) {
    return insert(
        "INSERT INTO"
            + " campus_trade_offer(merchant_id,title,description,kind,price,stock_total,stock_available,starts_at,ends_at,group_size,group_duration_seconds,created_by,created_at)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
        merchant,
        input.title(),
        input.description(),
        input.kind().name(),
        input.price(),
        input.stock(),
        input.stock(),
        input.startsAt(),
        input.endsAt(),
        input.groupSize(),
        input.groupDurationSeconds(),
        actor,
        now);
  }

  public void offerStatus(long id, String status) {
    jdbc.update("UPDATE campus_trade_offer SET status=?,version=version+1 WHERE id=?", status, id);
  }

  public boolean reserve(long offer) {
    return jdbc.update(
            "UPDATE campus_trade_offer SET stock_available=stock_available-1 WHERE id=? AND"
                + " stock_available>0",
            offer)
        == 1;
  }

  public void release(OrderRow order) {
    if (jdbc.update(
            "UPDATE campus_order SET stock_released=TRUE WHERE id=? AND stock_released=FALSE",
            order.id())
        == 1)
      jdbc.update(
          "UPDATE campus_trade_offer SET stock_available=stock_available+1 WHERE id=?",
          order.offerId());
  }

  public OrderRow order(long id, boolean lock) {
    return one("SELECT * FROM campus_order WHERE id=?" + (lock ? " FOR UPDATE" : ""), ORDER, id);
  }

  public List<OrderRow> orders(long principal, boolean merchant, int limit, long offset) {
    return jdbc.query(
        "SELECT * FROM campus_order WHERE "
            + (merchant ? "merchant_id" : "user_id")
            + "=? AND offer_id IS NOT NULL ORDER BY id DESC LIMIT ? OFFSET ?",
        ORDER,
        principal,
        limit,
        offset);
  }

  public long orderCount(long principal, boolean merchant) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM campus_order WHERE "
            + (merchant ? "merchant_id" : "user_id")
            + "=? AND offer_id IS NOT NULL",
        Long.class,
        principal);
  }

  public OrderRow requested(long user, String key) {
    return one(
        "SELECT o.* FROM campus_trade_order_request r JOIN campus_order o ON o.id=r.order_id WHERE"
            + " r.user_id=? AND r.request_key=? FOR UPDATE",
        ORDER,
        user,
        key);
  }

  public Long requestedGroup(long user, String key) {
    return jdbc.query(
        "SELECT group_id FROM campus_trade_order_request WHERE user_id=? AND request_key=?",
        r -> r.next() ? (Long) r.getObject(1) : null,
        user,
        key);
  }

  public boolean hasSeckillOrder(long user, long offer) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM campus_order WHERE user_id=? AND offer_id=? AND status NOT IN"
                + " ('CANCELLED','REFUNDED')",
            Long.class,
            user,
            offer)
        > 0;
  }

  public long createOrder(
      long user,
      OfferRow offer,
      Long group,
      String number,
      String snapshot,
      Instant expires,
      Instant now) {
    return insert(
        "INSERT INTO"
            + " campus_order(order_no,user_id,merchant_id,offer_id,group_id,amount,currency,offer_snapshot,expires_at,created_at)"
            + " VALUES(?,?,?,?,?,?,'CNY',?,?,?)",
        number,
        user,
        offer.merchantId(),
        offer.id(),
        group,
        offer.price(),
        snapshot,
        expires,
        now);
  }

  public void orderRequest(long user, String key, long offer, Long requestedGroup, long order) {
    jdbc.update(
        "INSERT INTO campus_trade_order_request(user_id,request_key,offer_id,group_id,order_id)"
            + " VALUES(?,?,?,?,?)",
        user,
        key,
        offer,
        requestedGroup,
        order);
  }

  public void orderStatus(long id, String status, Instant paid) {
    jdbc.update(
        "UPDATE campus_order SET status=?,paid_at=COALESCE(paid_at,?),version=version+1 WHERE id=?",
        status,
        paid == null ? null : Timestamp.from(paid),
        id);
  }

  public void cancellation(long id, String reason, Instant now) {
    jdbc.update(
        "INSERT INTO campus_trade_cancellation(order_id,reason,created_at) VALUES(?,?,?)",
        id,
        reason,
        Timestamp.from(now));
  }

  public GroupRow group(long id, boolean lock) {
    return one(
        "SELECT * FROM campus_trade_group WHERE id=?" + (lock ? " FOR UPDATE" : ""), GROUP, id);
  }

  public long createGroup(OfferRow offer, long creator, Instant expires, Instant now) {
    return insert(
        "INSERT INTO campus_trade_group(offer_id,creator_id,target_size,expires_at,created_at)"
            + " VALUES(?,?,?,?,?)",
        offer.id(),
        creator,
        offer.groupSize(),
        expires,
        now);
  }

  public boolean groupMember(long group, long user) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM campus_trade_group_member WHERE group_id=? AND user_id=?",
            Integer.class,
            group,
            user)
        > 0;
  }

  public void joinGroup(long group, long user, long order) {
    jdbc.update(
        "INSERT INTO campus_trade_group_member(group_id,user_id,order_id) VALUES(?,?,?)",
        group,
        user,
        order);
    jdbc.update("UPDATE campus_trade_group SET reserved_count=reserved_count+1 WHERE id=?", group);
  }

  public void groupPaid(long group) {
    jdbc.update("UPDATE campus_trade_group SET paid_count=paid_count+1 WHERE id=?", group);
    jdbc.update(
        "UPDATE campus_trade_group SET status='SUCCEEDED' WHERE id=? AND status='OPEN' AND"
            + " paid_count=target_size",
        group);
  }

  public void leaveUnpaidGroup(long group) {
    jdbc.update("UPDATE campus_trade_group SET reserved_count=reserved_count-1 WHERE id=?", group);
  }

  public void groupFailed(long group) {
    jdbc.update(
        "UPDATE campus_trade_group SET status='FAILED' WHERE id=? AND status='OPEN'", group);
  }

  public List<GroupRow> openGroups(long offer, int limit, long offset, Instant now) {
    return jdbc.query(
        "SELECT * FROM campus_trade_group WHERE offer_id=? AND status='OPEN' AND expires_at>? ORDER"
            + " BY id LIMIT ? OFFSET ?",
        GROUP,
        offer,
        Timestamp.from(now),
        limit,
        offset);
  }

  public long groupCount(long offer, Instant now) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM campus_trade_group WHERE offer_id=? AND status='OPEN' AND"
            + " expires_at>?",
        Long.class,
        offer,
        Timestamp.from(now));
  }

  public PaymentRow payment(long order) {
    return one("SELECT * FROM campus_trade_payment WHERE order_id=? FOR UPDATE", PAYMENT, order);
  }

  public PaymentRow providerPayment(String number) {
    return one("SELECT * FROM campus_trade_payment WHERE provider_order_no=?", PAYMENT, number);
  }

  public void createPayment(String id, OrderRow order, Instant now) {
    jdbc.update(
        "INSERT INTO campus_trade_payment(id,order_id,provider_order_no,next_check_at,created_at)"
            + " VALUES(?,?,?,?,?)",
        id,
        order.id(),
        order.orderNo(),
        Timestamp.from(now),
        Timestamp.from(now));
  }

  public void paymentPaid(String id, String trade) {
    jdbc.update(
        "UPDATE campus_trade_payment SET status='PAID',provider_trade_no=? WHERE id=?", trade, id);
  }

  public void paymentClosed(String id) {
    jdbc.update(
        "UPDATE campus_trade_payment SET status='CLOSED' WHERE id=? AND status='CREATED'", id);
  }

  public String callbackHash(String id) {
    return one(
        "SELECT payload_hash FROM campus_trade_callback WHERE notification_id=? FOR UPDATE",
        (r, n) -> r.getString(1),
        id);
  }

  public void callback(String notice, String payment, String hash, Instant now) {
    jdbc.update(
        "INSERT INTO campus_trade_callback(notification_id,payment_id,payload_hash,received_at)"
            + " VALUES(?,?,?,?)",
        notice,
        payment,
        hash,
        Timestamp.from(now));
  }

  public Refund refund(long order) {
    return one("SELECT * FROM campus_trade_refund WHERE order_id=? FOR UPDATE", REFUND, order);
  }

  public Refund refundById(String id) {
    return one("SELECT * FROM campus_trade_refund WHERE id=?", REFUND, id);
  }

  public void createRefund(String id, OrderRow order, String reason, Instant now) {
    jdbc.update(
        "INSERT INTO campus_trade_refund(id,order_id,amount,reason,next_attempt_at,created_at)"
            + " VALUES(?,?,?,?,?,?)",
        id,
        order.id(),
        order.amount(),
        reason,
        Timestamp.from(now),
        Timestamp.from(now));
  }

  public List<Long> dueOrders(Instant now, int limit) {
    return jdbc.queryForList(
        "SELECT o.id FROM campus_order o LEFT JOIN campus_trade_group g ON g.id=o.group_id WHERE"
            + " o.offer_id IS NOT NULL AND ((o.status='PENDING_PAYMENT' AND o.expires_at<=?) OR"
            + " (o.status IN ('PENDING_PAYMENT','GROUP_PENDING') AND (g.status IN"
            + " ('FAILED','SUCCEEDED') OR g.expires_at<=?))) ORDER BY o.id LIMIT ?",
        Long.class,
        Timestamp.from(now),
        Timestamp.from(now),
        limit);
  }

  public List<Long> dueGroups(Instant now, int limit) {
    return jdbc.queryForList(
        "SELECT id FROM campus_trade_group WHERE status='OPEN' AND expires_at<=? ORDER BY"
            + " expires_at,id LIMIT ?",
        Long.class,
        Timestamp.from(now),
        limit);
  }

  public List<PaymentRow> duePayments(Instant now, int limit) {
    return jdbc.query(
        "SELECT * FROM campus_trade_payment WHERE status='CREATED' AND next_check_at<=? ORDER BY"
            + " next_check_at LIMIT ?",
        PAYMENT,
        Timestamp.from(now),
        limit);
  }

  public void nextPaymentCheck(String id, Instant next) {
    jdbc.update(
        "UPDATE campus_trade_payment SET next_check_at=? WHERE id=?", Timestamp.from(next), id);
  }

  public RefundJob leaseRefund(Instant now, Instant until, String token) {
    var row =
        one(
            "SELECT * FROM campus_trade_refund WHERE (status='PENDING' AND next_attempt_at<=?) OR"
                + " (status='PROCESSING' AND locked_until<=?) ORDER BY next_attempt_at,id LIMIT 1"
                + " FOR UPDATE SKIP LOCKED",
            (r, n) ->
                new RefundJob(
                    r.getString("id"),
                    r.getLong("order_id"),
                    r.getBigDecimal("amount"),
                    r.getInt("attempts"),
                    token),
            Timestamp.from(now),
            Timestamp.from(now));
    if (row != null)
      jdbc.update(
          "UPDATE campus_trade_refund SET"
              + " status='PROCESSING',attempts=attempts+1,lease_token=?,locked_until=? WHERE id=?",
          token,
          Timestamp.from(until),
          row.id());
    return row;
  }

  public boolean refundLease(String id, String token) {
    return one(
            "SELECT id FROM campus_trade_refund WHERE id=? AND lease_token=? AND"
                + " status='PROCESSING' FOR UPDATE",
            (r, n) -> r.getString(1),
            id,
            token)
        != null;
  }

  public void finishRefund(String id, String token, Instant now) {
    jdbc.update(
        "UPDATE campus_trade_refund SET"
            + " status='SUCCEEDED',completed_at=?,lease_token=NULL,locked_until=NULL,last_error_code=NULL"
            + " WHERE id=? AND lease_token=?",
        Timestamp.from(now),
        id,
        token);
  }

  public void failRefund(String id, String token, boolean exhausted, Instant next) {
    jdbc.update(
        "UPDATE campus_trade_refund SET"
            + " status=?,next_attempt_at=?,lease_token=NULL,locked_until=NULL,last_error_code='PROVIDER_UNAVAILABLE'"
            + " WHERE id=? AND lease_token=?",
        exhausted ? "FAILED" : "PENDING",
        Timestamp.from(next),
        id,
        token);
  }

  public void retryRefund(String id, Instant now) {
    jdbc.update(
        "UPDATE campus_trade_refund SET"
            + " status='PENDING',attempts=0,next_attempt_at=?,last_error_code=NULL WHERE id=? AND"
            + " status='FAILED'",
        Timestamp.from(now),
        id);
  }

  public void issueCode(long order, String hash, Instant now, Instant expiry) {
    jdbc.update(
        "UPDATE campus_order SET redemption_hash=?,redemption_issued_at=?,redemption_expires_at=?"
            + " WHERE id=?",
        hash,
        Timestamp.from(now),
        Timestamp.from(expiry),
        order);
  }

  public boolean codeRecentlyIssued(long order, Instant after) {
    return one(
            "SELECT id FROM campus_order WHERE id=? AND redemption_issued_at>? FOR UPDATE",
            (r, n) -> r.getLong(1),
            order,
            Timestamp.from(after))
        != null;
  }

  public boolean validCode(long order, String hash, Instant now, boolean redeemed) {
    return one(
            "SELECT id FROM campus_order WHERE id=? AND redemption_hash=?"
                + (redeemed ? "" : " AND redemption_expires_at>?")
                + " FOR UPDATE",
            (r, n) -> r.getLong(1),
            redeemed ? new Object[] {order, hash} : new Object[] {order, hash, Timestamp.from(now)})
        != null;
  }

  public void redeem(long order, Instant now) {
    jdbc.update(
        "UPDATE campus_order SET status='REDEEMED',redeemed_at=?,version=version+1 WHERE id=?",
        Timestamp.from(now),
        order);
  }
}
