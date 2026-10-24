package com.campus.perks.trade.serviceImpl;

import com.campus.perks.common.api.*;
import com.campus.perks.common.config.BusinessProperties;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.port.*;
import com.campus.perks.security.service.CurrentActor;
import com.campus.perks.trade.dto.TradeRequests.*;
import com.campus.perks.trade.entity.*;
import com.campus.perks.trade.mapper.TradeMapper;
import com.campus.perks.trade.service.*;
import com.campus.perks.trade.vo.TradeViews.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lock order: sorted accounts -> merchant guard -> offer -> group -> order -> payment/refund. */
@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
@EnableConfigurationProperties(TradeProperties.class)
@Transactional
public class OrderServiceImpl implements OrderService {
  public Offer managedOffer(String merchantId, String offerId) {
    merchants.requireMerchantMember(merchantId);
    var o = requireOffer(id(offerId), false);
    if (o.merchantId() != id(merchantId)) throw missing();
    return view(o);
  }

  public Order merchantOrder(String merchantId, String orderId) {
    merchants.requireMerchantMember(merchantId);
    var o = requireOrder(id(orderId), false);
    if (o.merchantId() != id(merchantId)) throw missing();
    return view(o);
  }

  private final TradeMapper db;
  private final AccountDirectoryPort accounts;
  private final MerchantAccessPort merchants;
  private final AuditPort audit;
  private final OutboxPort outbox;
  private final SandboxPaymentPort provider;
  private final TradeProperties config;
  private final BusinessProperties business;
  private final ObjectMapper json;
  private final Clock clock;
  private final com.campus.perks.integration.contract.SearchChangeEvents searchEvents;
  private final SecureRandom random = new SecureRandom();

  public OrderServiceImpl(
      TradeMapper db,
      AccountDirectoryPort accounts,
      MerchantAccessPort merchants,
      AuditPort audit,
      OutboxPort outbox,
      SandboxPaymentPort provider,
      TradeProperties config,
      BusinessProperties business,
      ObjectMapper json,
      Clock clock,
      com.campus.perks.integration.contract.SearchChangeEvents searchEvents) {
    this.db = db;
    this.accounts = accounts;
    this.merchants = merchants;
    this.audit = audit;
    this.outbox = outbox;
    this.provider = provider;
    this.config = config;
    this.business = business;
    this.json = json;
    this.clock = clock;
    this.searchEvents = searchEvents;
    if (business.orderPaymentTimeout() == null
        || business.orderPaymentTimeout().isZero()
        || business.orderPaymentTimeout().isNegative())
      throw new IllegalStateException("Order timeout must be positive");
  }

  private ApplicationException missing() {
    return new ApplicationException(
        HttpStatus.NOT_FOUND, "TRADE_RESOURCE_NOT_FOUND", "交易资源不存在或无权访问");
  }

  private ApplicationException conflict(String code, String detail) {
    return new ApplicationException(HttpStatus.CONFLICT, code, detail);
  }

  private long id(String value) {
    if (value == null || !value.matches("[1-9][0-9]{0,17}")) throw missing();
    return Long.parseLong(value);
  }

  private long actor() {
    return id(CurrentActor.id());
  }

  private long offset(PageQuery q) {
    return (long) (q.getPage() - 1) * q.getSize();
  }

  private OfferRow requireOffer(long id, boolean lock) {
    var row = db.offer(id, lock);
    if (row == null) throw missing();
    return row;
  }

  private OrderRow requireOrder(long id, boolean lock) {
    var row = db.order(id, lock);
    if (row == null || row.offerId() == 0) throw missing();
    return row;
  }

  private GroupRow requireGroup(long id, boolean lock) {
    var row = db.group(id, lock);
    if (row == null) throw missing();
    return row;
  }

  private Offer view(OfferRow o) {
    return new Offer(
        "" + o.id(),
        "" + o.merchantId(),
        o.title(),
        o.description(),
        o.kind(),
        o.price().toPlainString(),
        o.stockAvailable(),
        o.stockTotal(),
        o.startsAt(),
        o.endsAt(),
        o.groupSize(),
        o.groupDurationSeconds(),
        o.status(),
        o.version());
  }

  private Order view(OrderRow o) {
    try {
      return new Order(
          "" + o.id(),
          o.orderNo(),
          "" + o.merchantId(),
          "" + o.offerId(),
          o.groupId() == null ? null : o.groupId().toString(),
          o.amount().toPlainString(),
          "CNY",
          json.readTree(o.snapshot()),
          o.status(),
          o.expiresAt(),
          o.paidAt(),
          o.createdAt());
    } catch (Exception e) {
      throw new IllegalStateException("Invalid order snapshot");
    }
  }

  private Group view(GroupRow g) {
    return new Group(
        "" + g.id(),
        "" + g.offerId(),
        g.targetSize(),
        g.reservedCount(),
        g.paidCount(),
        g.status(),
        g.expiresAt());
  }

  private void record(long who, String action, String type, long resource) {
    audit.record("" + who, action, type, "" + resource, Map.of());
  }

  private void event(OrderRow o, String type) {
    outbox.append(
        new EventPublisherPort.Event(
            UUID.randomUUID().toString(),
            type,
            "" + o.id(),
            clock.instant(),
            Map.of(
                "userId",
                "" + o.userId(),
                "orderId",
                "" + o.id(),
                "merchantId",
                "" + o.merchantId())));
  }

  public PageResponse<Offer> catalog(PageQuery q) {
    return new PageResponse<>(
        db.publicOffers(q.getSize(), offset(q), clock.instant()).stream().map(this::view).toList(),
        db.publicCount(clock.instant()),
        q.getPage(),
        q.getSize());
  }

  public Offer offer(String value) {
    var row = db.publicOffer(id(value));
    if (row == null) throw missing();
    return view(row);
  }

  public PageResponse<Offer> merchantOffers(String merchantId, PageQuery q) {
    merchants.requireMerchantMember(merchantId);
    long merchant = id(merchantId);
    return new PageResponse<>(
        db.merchantOffers(merchant, q.getSize(), offset(q)).stream().map(this::view).toList(),
        db.merchantOfferCount(merchant),
        q.getPage(),
        q.getSize());
  }

  public Offer createOffer(String merchantId, OfferInput input) {
    long actor = actor();
    accounts.lockActiveUser("" + actor);
    merchants.requirePromotionPublisher(merchantId);
    if (input.stock() > config.maxStock()
        || !input.endsAt().isAfter(clock.instant())
        || input.endsAt().isAfter(clock.instant().plus(config.maxOfferDuration()))
        || !input.endsAt().isAfter(input.startsAt())
        || Duration.between(input.startsAt(), input.endsAt()).compareTo(config.maxOfferDuration())
            > 0
        || input.kind() == Kind.GROUP
            && (input.groupSize() < 2
                || input.groupSize() > input.stock()
                || input.groupSize() > config.maxGroupSize()
                || input.groupDurationSeconds() < 1
                || Duration.ofSeconds(input.groupDurationSeconds())
                        .compareTo(config.maxGroupDuration())
                    > 0)
        || input.kind() != Kind.GROUP
            && (input.groupSize() != 1 || input.groupDurationSeconds() != 0))
      throw new ApplicationException(
          HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_OFFER_RULES", "套餐库存、期限或拼团规则无效");
    long created = db.createOffer(id(merchantId), actor, input, clock.instant());
    record(actor, "TRADE_OFFER_CREATED", "TRADE_OFFER", created);
    return view(requireOffer(created, true));
  }

  public Offer changeOffer(String value, OfferStatus input) {
    long actor = actor();
    accounts.lockActiveUser("" + actor);
    var route = requireOffer(id(value), false);
    merchants.requirePromotionPublisher("" + route.merchantId());
    var offer = requireOffer(route.id(), true);
    if (offer.version() != input.version()) throw conflict("STALE_VERSION", "套餐已变化，请刷新");
    if (input.status() == Status.PUBLISHED
        && (!"DRAFT".equals(offer.status()) || !offer.endsAt().isAfter(clock.instant())))
      throw conflict("INVALID_OFFER_TRANSITION", "只能发布未过期草稿；发布内容不可修改或重新上架");
    if (input.status() == Status.OFFLINE && !"PUBLISHED".equals(offer.status()))
      throw conflict("INVALID_OFFER_TRANSITION", "只能下架已发布套餐");
    db.offerStatus(offer.id(), input.status().name());
    searchEvents.changed("OFFER", value);
    record(actor, "TRADE_OFFER_" + input.status(), "TRADE_OFFER", offer.id());
    return view(requireOffer(offer.id(), true));
  }

  public PageResponse<Group> groups(String value, PageQuery q) {
    offer(value);
    long offer = id(value);
    return new PageResponse<>(
        db.openGroups(offer, q.getSize(), offset(q), clock.instant()).stream()
            .map(this::view)
            .toList(),
        db.groupCount(offer, clock.instant()),
        q.getPage(),
        q.getSize());
  }

  public Group group(String value) {
    return view(requireGroup(id(value), false));
  }

  public Result<Order> createOrder(OrderInput input, String key) {
    if (key == null || !key.matches("[A-Za-z0-9_-]{8,64}"))
      throw new ApplicationException(
          HttpStatus.BAD_REQUEST,
          "INVALID_IDEMPOTENCY_KEY",
          "Idempotency-Key 需为8至64位字母、数字、下划线或连字符");
    long user = actor(), offerId = id(input.offerId());
    Long requestedGroup = input.groupId() == null ? null : id(input.groupId());
    accounts.lockActiveUser("" + user);
    var existing = db.requested(user, key);
    if (existing != null) {
      if (existing.offerId() != offerId
          || !Objects.equals(db.requestedGroup(user, key), requestedGroup))
        throw conflict("IDEMPOTENCY_KEY_REUSED", "请求键已被其他订单内容使用");
      return new Result<>(false, view(existing));
    }
    var route = requireOffer(offerId, false);
    merchants.requireActiveMerchant("" + route.merchantId());
    var offer = requireOffer(offerId, true);
    Instant now = clock.instant();
    if (!"PUBLISHED".equals(offer.status())
        || now.isBefore(offer.startsAt())
        || !now.isBefore(offer.endsAt())) throw conflict("OFFER_NOT_ON_SALE", "套餐当前不可购买");
    if ("SECKILL".equals(offer.kind()) && db.hasSeckillOrder(user, offer.id()))
      throw conflict("SECKILL_LIMIT", "每人限购一份有效秒杀订单");
    Long groupId = null;
    if ("GROUP".equals(offer.kind())) {
      groupId =
          requestedGroup == null
              ? db.createGroup(
                  offer,
                  user,
                  min(now.plusSeconds(offer.groupDurationSeconds()), offer.endsAt()),
                  now)
              : requestedGroup;
      var group = requireGroup(groupId, true);
      if (group.offerId() != offer.id()
          || !"OPEN".equals(group.status())
          || !now.isBefore(group.expiresAt())
          || group.reservedCount() >= group.targetSize()
          || db.groupMember(group.id(), user)) throw conflict("GROUP_UNAVAILABLE", "拼团已满、过期或本人已加入");
    } else if (requestedGroup != null) throw conflict("GROUP_NOT_SUPPORTED", "该套餐不支持拼团");
    if (!db.reserve(offer.id())) throw conflict("OUT_OF_STOCK", "套餐已售罄");
    Instant expiry = min(now.plus(business.orderPaymentTimeout()), offer.endsAt());
    if (groupId != null) expiry = min(expiry, requireGroup(groupId, true).expiresAt());
    String snapshot;
    try {
      snapshot =
          json.writeValueAsString(
              Map.of(
                  "offerId",
                  "" + offer.id(),
                  "title",
                  offer.title(),
                  "description",
                  offer.description(),
                  "kind",
                  offer.kind(),
                  "price",
                  offer.price().toPlainString(),
                  "currency",
                  "CNY",
                  "quantity",
                  1,
                  "groupSize",
                  offer.groupSize()));
    } catch (Exception e) {
      throw new IllegalStateException("Order serialization failed");
    }
    long order =
        db.createOrder(
            user,
            offer,
            groupId,
            "CP" + UUID.randomUUID().toString().replace("-", ""),
            snapshot,
            expiry,
            now);
    db.orderRequest(user, key, offer.id(), requestedGroup, order);
    if (groupId != null) db.joinGroup(groupId, user, order);
    record(user, "ORDER_CREATED", "ORDER", order);
    var row = requireOrder(order, true);
    event(row, "ORDER_CREATED");
    return new Result<>(true, view(row));
  }

  private Instant min(Instant a, Instant b) {
    return a.isBefore(b) ? a : b;
  }

  public PageResponse<Order> orders(PageQuery q) {
    long user = actor();
    return new PageResponse<>(
        db.orders(user, false, q.getSize(), offset(q)).stream().map(this::view).toList(),
        db.orderCount(user, false),
        q.getPage(),
        q.getSize());
  }

  public PageResponse<Order> merchantOrders(String merchantId, PageQuery q) {
    merchants.requireMerchantMember(merchantId);
    long merchant = id(merchantId);
    return new PageResponse<>(
        db.orders(merchant, true, q.getSize(), offset(q)).stream().map(this::view).toList(),
        db.orderCount(merchant, true),
        q.getPage(),
        q.getSize());
  }

  private OrderRow own(String value) {
    var row = requireOrder(id(value), false);
    if (row.userId() != actor()) throw missing();
    return row;
  }

  public Order order(String value) {
    return view(own(value));
  }

  /** Routing columns are immutable; authorization and state always use locked current rows. */
  private OrderRow lock(long order, Long operator, String merchant, boolean active) {
    var route = requireOrder(order, false);
    var ids = new TreeSet<Long>();
    ids.add(route.userId());
    if (operator != null) ids.add(operator);
    for (long user : ids) {
      if (active && operator != null && user == operator) accounts.lockActiveUser("" + user);
      else accounts.lockExistingUser("" + user);
    }
    if (merchant != null) {
      if (route.merchantId() != id(merchant)) throw missing();
      merchants.requireCouponOperator(merchant);
    }
    requireOffer(route.offerId(), true);
    if (route.groupId() != null) requireGroup(route.groupId(), true);
    return requireOrder(order, true);
  }

  private OrderRow lockOwn(String value) {
    var route = own(value);
    return lock(route.id(), actor(), null, true);
  }

  public Result<Order> cancel(String value) {
    var order = lockOwn(value);
    if ("CANCELLED".equals(order.status())) return new Result<>(false, view(order));
    if (!"PENDING_PAYMENT".equals(order.status()))
      throw conflict("ORDER_NOT_CANCELLABLE", "已付款或已关闭订单不能取消，请查看退款状态");
    cancelLocked(order, "USER_CANCELLED");
    return new Result<>(true, view(requireOrder(order.id(), true)));
  }

  private void cancelLocked(OrderRow order, String reason) {
    db.orderStatus(order.id(), "CANCELLED", null);
    db.cancellation(order.id(), reason, clock.instant());
    db.release(order);
    if (order.groupId() != null) db.leaveUnpaidGroup(order.groupId());
    record(order.userId(), "ORDER_CANCELLED", "ORDER", order.id());
    event(order, "ORDER_CANCELLED");
  }

  public Result<Payment> payment(String value) {
    if (!provider.available())
      throw new ApplicationException(
          HttpStatus.SERVICE_UNAVAILABLE, "SANDBOX_PAYMENT_UNAVAILABLE", "支付宝沙箱尚未配置");
    var order = lockOwn(value);
    if (!"PENDING_PAYMENT".equals(order.status()) || !clock.instant().isBefore(order.expiresAt()))
      throw conflict("ORDER_NOT_PAYABLE", "订单已过期或不可支付");
    if (order.groupId() != null) {
      var group = requireGroup(order.groupId(), true);
      if (!"OPEN".equals(group.status()) || !clock.instant().isBefore(group.expiresAt()))
        throw conflict("GROUP_UNAVAILABLE", "拼团已关闭");
    }
    var p = db.payment(order.id());
    boolean created = p == null;
    if (created) {
      db.createPayment(UUID.randomUUID().toString(), order, clock.instant());
      p = db.payment(order.id());
      record(order.userId(), "PAYMENT_CREATED", "ORDER", order.id());
    }
    String url =
        provider.checkout(
            order.orderNo(), order.amount(), "Campus Perks " + order.orderNo(), order.expiresAt());
    return new Result<>(created, new Payment(p.id(), value, p.status(), url, order.expiresAt()));
  }

  public Payment payment(String orderId, String paymentId) {
    var o = own(orderId);
    var p = db.payment(o.id());
    if (p == null || !p.id().equals(paymentId)) throw missing();
    return new Payment(p.id(), orderId, p.status(), null, o.expiresAt());
  }

  public Result<Refund> refund(String value) {
    var order = lockOwn(value);
    var existing = db.refund(order.id());
    if (existing != null) return new Result<>(false, existing);
    if (!"PAID".equals(order.status()))
      throw conflict("ORDER_NOT_REFUNDABLE", "仅未核销且已成团/普通已付款订单可主动退款");
    refundLocked(order, "USER_REQUESTED");
    return new Result<>(true, db.refund(order.id()));
  }

  private void refundLocked(OrderRow order, String reason) {
    if (db.refund(order.id()) != null) return;
    db.orderStatus(order.id(), "REFUND_PENDING", null);
    db.createRefund(UUID.randomUUID().toString(), order, reason, clock.instant());
    record(order.userId(), "REFUND_REQUESTED", "ORDER", order.id());
    event(order, "ORDER_REFUND_PENDING");
  }

  public Refund refund(String orderId, String refundId) {
    var o = own(orderId);
    var r = db.refund(o.id());
    if (r == null || !r.id().equals(refundId)) throw missing();
    return r;
  }

  public java.util.List<Refund> refunds(String orderId) {
    var o = own(orderId);
    var r = db.refund(o.id());
    return r == null ? List.of() : List.of(r);
  }

  public Refund retryRefund(String orderId, String refundId) {
    var o = lockOwn(orderId);
    var r = db.refund(o.id());
    if (r == null || !r.id().equals(refundId)) throw missing();
    if (!"FAILED".equals(r.status())) throw conflict("REFUND_NOT_FAILED", "退款尚未失败，无需手动重试");
    db.retryRefund(r.id(), clock.instant());
    record(o.userId(), "REFUND_RETRIED", "ORDER", o.id());
    return db.refund(o.id());
  }

  public RedemptionCode code(String value) {
    var order = lockOwn(value);
    if (!"PAID".equals(order.status())) throw conflict("ORDER_NOT_REDEEMABLE", "订单尚未支付、成团或已经关闭");
    if (db.codeRecentlyIssued(order.id(), clock.instant().minus(config.redemptionResendInterval())))
      throw new ApplicationException(
          HttpStatus.TOO_MANY_REQUESTS, "CODE_RATE_LIMITED", "请稍后再生成核销码");
    byte[] token = new byte[32];
    random.nextBytes(token);
    String code = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    db.issueCode(
        order.id(), hash(code), clock.instant(), clock.instant().plus(config.redemptionTtl()));
    return new RedemptionCode(value, code, clock.instant().plus(config.redemptionTtl()));
  }

  public Result<Order> redeem(String merchantId, Redemption input) {
    var order = lock(id(input.orderId()), actor(), merchantId, true);
    boolean redeemed = "REDEEMED".equals(order.status());
    if (!input.code().matches("[A-Za-z0-9_-]{43}")
        || !db.validCode(order.id(), hash(input.code()), clock.instant(), redeemed))
      throw conflict("INVALID_REDEMPTION_CODE", "核销码无效或已过期");
    if (redeemed) return new Result<>(false, view(order));
    if (!"PAID".equals(order.status())) throw conflict("ORDER_NOT_REDEEMABLE", "订单当前不可核销");
    db.redeem(order.id(), clock.instant());
    record(actor(), "ORDER_REDEEMED", "ORDER", order.id());
    event(order, "ORDER_REDEEMED");
    return new Result<>(true, view(requireOrder(order.id(), true)));
  }

  /** Verified receipts and state transitions commit atomically; no external HTTP in this method. */
  public void accept(SandboxPaymentPort.VerifiedPayment notice, String digest) {
    var route = db.providerPayment(notice.orderNo());
    if (route == null) throw missing();
    var order = lock(route.orderId(), null, null, false);
    var p = db.payment(order.id());
    String existing = db.callbackHash(notice.notificationId());
    if (existing != null) {
      if (!existing.equals(digest)) throw conflict("NOTIFICATION_ID_REUSED", "通知内容不一致");
      return;
    }
    applyPayment(order, p, notice.tradeNo(), notice.amount(), notice.status());
    db.callback(notice.notificationId(), p.id(), digest, clock.instant());
  }

  private void applyPayment(
      OrderRow order,
      TradeMapper.PaymentRow payment,
      String trade,
      java.math.BigDecimal amount,
      String status) {
    if (amount == null
        || amount.compareTo(order.amount()) != 0
        || trade == null
        || !trade.matches("[A-Za-z0-9]{1,64}")
        || payment.tradeNo() != null && !payment.tradeNo().equals(trade))
      throw conflict("PAYMENT_MISMATCH", "支付交易或金额不匹配");
    if ("TRADE_CLOSED".equals(status) && !"PAID".equals(payment.status())) {
      db.paymentClosed(payment.id());
      if ("PENDING_PAYMENT".equals(order.status())) cancelLocked(order, "PROVIDER_CLOSED");
      return;
    }
    if (!Set.of("TRADE_SUCCESS", "TRADE_FINISHED").contains(status)
        || "PAID".equals(payment.status())) return;
    db.paymentPaid(payment.id(), trade);
    boolean late =
        !"PENDING_PAYMENT".equals(order.status()) || !clock.instant().isBefore(order.expiresAt());
    if (order.groupId() != null) {
      var group = requireGroup(order.groupId(), true);
      if ("OPEN".equals(group.status()) && !clock.instant().isBefore(group.expiresAt()))
        db.groupFailed(group.id());
      late = late || !"OPEN".equals(requireGroup(group.id(), true).status());
    }
    if (late) {
      if ("PENDING_PAYMENT".equals(order.status())) cancelLocked(order, "PAYMENT_TOO_LATE");
      db.orderStatus(order.id(), requireOrder(order.id(), true).status(), clock.instant());
      refundLocked(order, "LATE_PAYMENT");
      return;
    }
    String next = "PAID";
    if (order.groupId() != null) {
      db.groupPaid(order.groupId());
      next =
          "SUCCEEDED".equals(requireGroup(order.groupId(), true).status())
              ? "PAID"
              : "GROUP_PENDING";
    }
    db.orderStatus(order.id(), next, clock.instant());
    record(order.userId(), "ORDER_PAID", "ORDER", order.id());
    event(order, "ORDER_PAID");
  }

  public void reconcilePayment(String paymentId, SandboxPaymentPort.PaymentState state) {
    var route = db.providerPayment(state.orderNo());
    if (route == null || !route.id().equals(paymentId)) throw missing();
    var order = lock(route.orderId(), null, null, false);
    var p = db.payment(order.id());
    if (Set.of("TRADE_SUCCESS", "TRADE_FINISHED").contains(state.status()))
      applyPayment(order, p, state.tradeNo(), state.amount(), state.status());
    else if ("TRADE_CLOSED".equals(state.status())) {
      db.paymentClosed(p.id());
      if ("PENDING_PAYMENT".equals(order.status())) cancelLocked(order, "PROVIDER_CLOSED");
    }
    db.nextPaymentCheck(paymentId, clock.instant().plus(config.paymentCheckInterval()));
  }

  public void closeConfirmed(String paymentId, String number) {
    var route = db.providerPayment(number);
    if (route == null || !route.id().equals(paymentId)) throw missing();
    var order = lock(route.orderId(), null, null, false);
    if (!clock.instant().isBefore(order.expiresAt()) || "CANCELLED".equals(order.status()))
      db.paymentClosed(paymentId);
  }

  public void sweepGroup(long id) {
    var route = requireGroup(id, false);
    accounts.lockExistingUser("" + route.creatorId());
    requireOffer(route.offerId(), true);
    var group = requireGroup(id, true);
    if ("OPEN".equals(group.status()) && !clock.instant().isBefore(group.expiresAt())) {
      db.groupFailed(group.id());
      record(group.creatorId(), "GROUP_FAILED", "TRADE_GROUP", group.id());
    }
  }

  public void sweepOrder(long id) {
    var order = lock(id, null, null, false);
    GroupRow group = null;
    if (order.groupId() != null) {
      group = requireGroup(order.groupId(), true);
      if ("OPEN".equals(group.status()) && !clock.instant().isBefore(group.expiresAt())) {
        db.groupFailed(group.id());
        record(order.userId(), "GROUP_FAILED", "TRADE_GROUP", group.id());
        group = requireGroup(group.id(), true);
      }
    }
    if ("PENDING_PAYMENT".equals(order.status())
        && (!clock.instant().isBefore(order.expiresAt())
            || group != null && "FAILED".equals(group.status())))
      cancelLocked(order, "TIMEOUT_OR_GROUP_FAILED");
    else if ("GROUP_PENDING".equals(order.status()) && group != null) {
      if ("SUCCEEDED".equals(group.status())) {
        db.orderStatus(order.id(), "PAID", null);
        record(order.userId(), "GROUP_SUCCEEDED", "ORDER", order.id());
        event(order, "ORDER_GROUP_SUCCEEDED");
      } else if ("FAILED".equals(group.status())) refundLocked(order, "GROUP_FAILED");
    }
  }

  public TradeMapper.RefundJob leaseRefund() {
    return db.leaseRefund(
        clock.instant(), clock.instant().plus(config.leaseTtl()), UUID.randomUUID().toString());
  }

  public void finishRefund(TradeMapper.RefundJob job) {
    var order = lock(job.orderId(), null, null, false);
    if (!db.refundLease(job.id(), job.lease())) return;
    if (!"REFUND_PENDING".equals(order.status()))
      throw new IllegalStateException("Refund state inconsistent");
    db.finishRefund(job.id(), job.lease(), clock.instant());
    db.orderStatus(order.id(), "REFUNDED", null);
    db.release(order);
    record(order.userId(), "ORDER_REFUNDED", "ORDER", order.id());
    event(order, "ORDER_REFUNDED");
  }

  public void failRefund(TradeMapper.RefundJob job) {
    lock(job.orderId(), null, null, false);
    if (!db.refundLease(job.id(), job.lease())) return;
    Duration delay = config.retryBase().multipliedBy(1L << Math.min(job.attempts(), 20));
    if (delay.compareTo(config.retryMax()) > 0) delay = config.retryMax();
    db.failRefund(
        job.id(),
        job.lease(),
        job.attempts() + 1 >= config.refundMaxAttempts(),
        clock.instant().plus(delay));
  }

  public static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
