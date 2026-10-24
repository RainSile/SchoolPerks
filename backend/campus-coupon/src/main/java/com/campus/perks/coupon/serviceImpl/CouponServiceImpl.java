package com.campus.perks.coupon.serviceImpl;

import com.campus.perks.common.api.*;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.coupon.config.CouponProperties;
import com.campus.perks.coupon.dto.*;
import com.campus.perks.coupon.entity.*;
import com.campus.perks.coupon.mapper.*;
import com.campus.perks.coupon.service.*;
import com.campus.perks.coupon.vo.*;
import com.campus.perks.integration.contract.ReviewRecord;
import com.campus.perks.integration.port.*;
import com.campus.perks.security.service.CurrentActor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class CouponServiceImpl implements CouponService {
  private final CouponMapper coupons;
  private final CouponCatalogMapper catalog;
  private final ClaimMapper claims;
  private final CouponReviewMapper reviews;
  private final MerchantAccessPort merchants;
  private final AccountDirectoryPort accounts;
  private final AuditPort audit;
  private final OutboxPort outbox;
  private final CouponProperties properties;
  private final RedemptionCodes codes;
  private final ObjectMapper json;
  private final Clock clock;
  private final com.campus.perks.integration.contract.SearchChangeEvents searchEvents;

  public CouponServiceImpl(
      CouponMapper coupons,
      CouponCatalogMapper catalog,
      ClaimMapper claims,
      CouponReviewMapper reviews,
      MerchantAccessPort merchants,
      AccountDirectoryPort accounts,
      AuditPort audit,
      OutboxPort outbox,
      CouponProperties properties,
      RedemptionCodes codes,
      ObjectMapper json,
      Clock clock,
      com.campus.perks.integration.contract.SearchChangeEvents searchEvents) {
    this.coupons = coupons;
    this.catalog = catalog;
    this.claims = claims;
    this.reviews = reviews;
    this.merchants = merchants;
    this.accounts = accounts;
    this.audit = audit;
    this.outbox = outbox;
    this.properties = properties;
    this.codes = codes;
    this.json = json;
    this.clock = clock;
    this.searchEvents = searchEvents;
  }

  private ApplicationException error(HttpStatus status, String code, String detail) {
    return new ApplicationException(status, code, detail);
  }

  private long id(String value) {
    if (value == null || !value.matches("[1-9][0-9]{0,17}"))
      throw error(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "优惠资源不存在");
    return Long.parseLong(value);
  }

  private long actor() {
    return Long.parseLong(CurrentActor.id());
  }

  private void activeActor() {
    accounts.lockActiveUser(CurrentActor.id());
  }

  private CouponRow find(String key) {
    var row = coupons.find(id(key));
    if (row == null) throw error(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "优惠不存在");
    return row;
  }

  private CouponRow lock(String key) {
    var initial = find(key);
    merchants.requireMerchantMember(initial.merchantId().toString());
    return coupons.lock(initial.id());
  }

  private CouponRules rules(String value) {
    try {
      return json.readValue(value, CouponRules.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Stored coupon rules invalid");
    }
  }

  private String serialize(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Coupon serialization failed");
    }
  }

  private String effective(CouponRow row) {
    return !row.validUntil().isAfter(clock.instant()) && !"DRAFT".equals(row.status())
        ? "EXPIRED"
        : row.status();
  }

  private MerchantCoupon view(CouponRow row) {
    var review =
        row.reviewRequestId() == null ? null : reviews.selectReviewByRequest(row.reviewRequestId());
    return new MerchantCoupon(
        row.id().toString(),
        row.merchantId().toString(),
        row.title(),
        row.couponType(),
        rules(row.rules()),
        row.totalStock(),
        row.remainingStock(),
        row.validFrom(),
        row.validUntil(),
        effective(row),
        row.version(),
        row.description(),
        row.campusOnly(),
        row.reviewStatus(),
        row.reviewRequestId(),
        row.publishedAt(),
        review == null ? null : review.reason());
  }

  private CouponSummary view(CatalogRow row) {
    return new CouponSummary(
        row.id().toString(),
        row.merchantId().toString(),
        row.merchantName(),
        row.schoolId().toString(),
        row.title(),
        row.couponType(),
        rules(row.rules()),
        row.remainingStock(),
        row.validFrom(),
        row.validUntil(),
        row.campusOnly(),
        row.description());
  }

  private void version(CouponRow row, int version) {
    if (row.version() != version)
      throw error(HttpStatus.CONFLICT, "VERSION_CONFLICT", "优惠已变更，请刷新后重试");
  }

  private void draft(CouponRow row) {
    if (!"DRAFT".equals(row.status()) || "PENDING".equals(row.reviewStatus()))
      throw error(HttpStatus.CONFLICT, "COUPON_IMMUTABLE", "送审中或已发布优惠不能修改内容");
  }

  private CouponEntity entity(String merchant, CouponInput input) {
    if (input.totalStock() > properties.maxStock()
        || !input.validUntil().isAfter(input.validFrom())
        || !input.validUntil().isAfter(clock.instant())
        || Duration.between(input.validFrom(), input.validUntil())
                .compareTo(properties.maxValidity())
            > 0) throw error(HttpStatus.BAD_REQUEST, "INVALID_COUPON", "库存或有效期不符合配置");
    var r = input.rules();
    boolean amount = "AMOUNT_OFF".equals(input.couponType());
    if (amount
        ? r.amountOff() == null
            || r.discountRate() != null
            || r.amountOff().compareTo(r.threshold()) > 0
        : !"DISCOUNT".equals(input.couponType())
            || r.amountOff() != null
            || r.discountRate() == null)
      throw error(HttpStatus.BAD_REQUEST, "INVALID_COUPON_RULES", "优惠类型与满减或折扣规则不匹配");
    var value = new CouponEntity();
    value.setMerchantId(id(merchant));
    value.setTitle(input.title().strip());
    value.setCouponType(input.couponType());
    value.setRules(serialize(r));
    value.setTotalStock(input.totalStock());
    value.setRemainingStock(input.totalStock());
    value.setValidFrom(input.validFrom());
    value.setValidUntil(input.validUntil());
    value.setDescription(input.description());
    value.setCampusOnly(input.campusOnly());
    return value;
  }

  public PageResponse<CouponSummary> query(CouponQuery q) {
    var now = clock.instant();
    return new PageResponse<>(
        catalog.list(q, now, (long) (q.getPage() - 1) * q.getSize()).stream()
            .map(this::view)
            .toList(),
        catalog.count(q, now),
        q.getPage(),
        q.getSize());
  }

  public CouponSummary detail(String key) {
    var row = catalog.publicDetail(id(key), clock.instant());
    if (row == null) throw error(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "优惠不存在或已下架");
    return view(row);
  }

  @Transactional
  public MerchantCoupon create(String merchant, CouponInput input) {
    activeActor();
    merchants.requirePromotionPublisher(merchant);
    var entity = entity(merchant, input);
    coupons.insert(entity);
    audit.record(
        CurrentActor.id(),
        "COUPON_CREATED",
        "COUPON",
        entity.getId().toString(),
        Map.of("merchantId", merchant));
    return view(coupons.find(entity.getId()));
  }

  @Transactional
  public MerchantCoupon update(String key, UpdateCouponRequest request) {
    activeActor();
    var initial = find(key);
    merchants.requirePromotionPublisher(initial.merchantId().toString());
    var row = coupons.lock(initial.id());
    version(row, request.version());
    draft(row);
    var entity = entity(row.merchantId().toString(), request.content());
    coupons.update(row.id(), request.version(), entity);
    audit.record(
        CurrentActor.id(),
        "COUPON_UPDATED",
        "COUPON",
        key,
        Map.of("version", request.version() + 1));
    return view(coupons.find(row.id()));
  }

  @Transactional
  public PageResponse<MerchantCoupon> merchantCoupons(String merchant, PageQuery q) {
    merchants.requireMerchantMember(merchant);
    return new PageResponse<>(
        coupons
            .merchantList(id(merchant), q.getSize(), (long) (q.getPage() - 1) * q.getSize())
            .stream()
            .map(this::view)
            .toList(),
        coupons.merchantCount(id(merchant)),
        q.getPage(),
        q.getSize());
  }

  @Transactional
  public MerchantCoupon managed(String key) {
    return view(lock(key));
  }

  private MerchantCoupon snapshot(String value) {
    try {
      return json.readValue(value, MerchantCoupon.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Stored review snapshot invalid");
    }
  }

  private CouponReviewRequest requestView(ReviewRequestRow row) {
    return new CouponReviewRequest(
        row.id(),
        row.couponId().toString(),
        row.submittedBy().toString(),
        row.contentVersion(),
        row.status(),
        row.createdAt(),
        row.reviewedAt(),
        snapshot(row.contentSnapshot()),
        reviews.selectReviewByRequest(row.id()));
  }

  @Transactional
  public CouponReviewRequest submitReview(String key, int version) {
    activeActor();
    var initial = find(key);
    merchants.requirePromotionPublisher(initial.merchantId().toString());
    var row = coupons.lock(initial.id());
    version(row, version);
    draft(row);
    if (!row.validUntil().isAfter(clock.instant()))
      throw error(HttpStatus.CONFLICT, "COUPON_EXPIRED", "优惠已到期，请先修改草稿");
    var request =
        new ReviewRequestRow(
            UUID.randomUUID().toString(),
            row.id(),
            actor(),
            version,
            serialize(view(row)),
            "PENDING",
            clock.instant(),
            null);
    reviews.request(request);
    coupons.requestReview(row.id(), version, request.id());
    audit.record(
        CurrentActor.id(),
        "COUPON_REVIEW_REQUESTED",
        "COUPON",
        key,
        Map.of("requestId", request.id()));
    return requestView(request);
  }

  public PageResponse<CouponReviewRequest> reviewQueue(ReviewQueueQuery q) {
    CurrentActor.require("coupon:review");
    return new PageResponse<>(
        reviews.queue(q.getStatus(), q.getSize(), (long) (q.getPage() - 1) * q.getSize()).stream()
            .map(this::requestView)
            .toList(),
        reviews.count(q.getStatus()),
        q.getPage(),
        q.getSize());
  }

  @Transactional
  public CouponReviewRequest reviewRequest(String key) {
    var row = reviews.find(key);
    if (row == null) throw error(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "审核请求不存在");
    var auth =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    if (auth.getAuthorities().stream().noneMatch(a -> "coupon:review".equals(a.getAuthority())))
      return ownedReview(row);
    return requestView(row);
  }

  private CouponReviewRequest ownedReview(ReviewRequestRow row) {
    var initial = coupons.find(row.couponId());
    merchants.requireMerchantMember(initial.merchantId().toString());
    return requestView(row);
  }

  @Transactional
  public ReviewRecord decide(String key, ReviewDecisionRequest input) {
    CurrentActor.require("coupon:review");
    activeActor();
    var initial = reviews.find(key);
    if (initial == null) throw error(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "审核请求不存在");
    var coupon = coupons.find(initial.couponId());
    var scope = merchants.requireActiveMerchant(coupon.merchantId().toString());
    // A reviewer linked to the target store must not review its content.
    if (initial.submittedBy().equals(actor())
        || merchants.isMerchantMember(scope.id(), CurrentActor.id()))
      throw error(HttpStatus.FORBIDDEN, "SELF_REVIEW_FORBIDDEN", "不能审核本人或所属门店的优惠");
    coupon = coupons.lock(coupon.id());
    var row = reviews.lock(key);
    if (!"PENDING".equals(row.status())
        || !key.equals(coupon.reviewRequestId())
        || !"PENDING".equals(coupon.reviewStatus()))
      throw error(HttpStatus.CONFLICT, "ALREADY_REVIEWED", "该请求已处理");
    var record =
        new ReviewRecord(
            UUID.randomUUID().toString(),
            key,
            CurrentActor.id(),
            input.decision(),
            input.reason().strip(),
            clock.instant());
    reviews.decide(key, input.decision(), clock.instant());
    reviews.insertReview(record);
    coupons.review(coupon.id(), key, input.decision());
    audit.record(
        CurrentActor.id(),
        "COUPON_REVIEWED",
        "COUPON",
        coupon.id().toString(),
        Map.of("decision", input.decision(), "requestId", key));
    return record;
  }

  public ReviewRecord review(String key) {
    CurrentActor.require("coupon:review");
    var row = reviews.selectReview(key);
    if (row == null) throw error(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "审核记录不存在");
    return row;
  }

  @Transactional
  public CouponStatusChange changeStatus(String key, CouponStatusRequest input) {
    activeActor();
    var initial = find(key);
    merchants.requirePromotionPublisher(initial.merchantId().toString());
    var row = coupons.lock(initial.id());
    version(row, input.version());
    boolean publish = "PUBLISHED".equals(input.status());
    if (publish
        ? !"DRAFT".equals(row.status())
            || !"APPROVED".equals(row.reviewStatus())
            || !row.validUntil().isAfter(clock.instant())
        : !"PUBLISHED".equals(row.status()))
      throw error(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", "请先通过内容审核；已下架的优惠不能重新发布");
    coupons.status(row.id(), row.version(), input.status(), clock.instant());
    var record =
        new CouponStatusChange(
            UUID.randomUUID().toString(),
            key,
            CurrentActor.id(),
            row.status(),
            input.status(),
            row.version() + 1,
            input.reason().strip(),
            clock.instant());
    coupons.statusHistory(record);
    audit.record(
        CurrentActor.id(),
        "COUPON_STATUS_CHANGED",
        "COUPON",
        key,
        Map.of("status", input.status(), "version", record.version()));
    searchEvents.changed("COUPON", key);
    return record;
  }

  @Transactional
  public CouponStatusChange statusChange(String key, String change) {
    lock(key);
    var row = coupons.statusChange(id(key), change);
    if (row == null) throw error(HttpStatus.NOT_FOUND, "STATUS_CHANGE_NOT_FOUND", "状态记录不存在");
    return row;
  }

  private CouponClaim view(ClaimRow row) {
    var coupon = coupons.find(row.couponId());
    var desc = catalog.description(row.couponId());
    var redemption = claims.redemptionByClaim(row.id());
    String status =
        "AVAILABLE".equals(row.status()) && !coupon.validUntil().isAfter(clock.instant())
            ? "EXPIRED"
            : row.status();
    return new CouponClaim(
        row.id().toString(),
        row.couponId().toString(),
        coupon.merchantId().toString(),
        desc.merchantName(),
        coupon.title(),
        coupon.couponType(),
        rules(coupon.rules()),
        status,
        row.claimedAt(),
        coupon.validFrom(),
        coupon.validUntil(),
        row.redeemedAt(),
        redemption == null ? null : redemption.id());
  }

  private ClaimRow own(String key) {
    var row = claims.find(id(key));
    if (row == null || !row.userId().equals(actor()))
      throw error(HttpStatus.NOT_FOUND, "CLAIM_NOT_FOUND", "本人优惠券不存在");
    return row;
  }

  @Transactional
  public Created<CouponClaim> claim(String key) {
    activeActor();
    var initial = find(key);
    var scope = merchants.requireActiveMerchant(initial.merchantId().toString());
    var coupon = coupons.lock(initial.id());
    var existing = claims.existing(coupon.id(), actor());
    if (existing != null) return new Created<>(false, view(existing));
    var now = clock.instant();
    if (!"PUBLISHED".equals(coupon.status())
        || !"APPROVED".equals(coupon.reviewStatus())
        || coupon.publishedAt() == null
        || now.isBefore(coupon.validFrom())
        || !now.isBefore(coupon.validUntil()))
      throw error(HttpStatus.CONFLICT, "COUPON_NOT_CLAIMABLE", "活动尚未开始、已结束或已下架");
    if (coupon.campusOnly()) accounts.requireVerifiedSchool(CurrentActor.id(), scope.schoolId());
    if (coupons.decrement(coupon.id()) != 1)
      throw error(HttpStatus.CONFLICT, "COUPON_SOLD_OUT", "优惠券已领完");
    claims.insert(coupon.id(), actor(), codes.hash(codes.random()), now);
    long id = claims.id(coupon.id(), actor());
    audit.record(
        CurrentActor.id(),
        "COUPON_CLAIMED",
        "COUPON_CLAIM",
        Long.toString(id),
        Map.of("couponId", key));
    return new Created<>(true, view(claims.find(id)));
  }

  public PageResponse<CouponClaim> ownClaims(PageQuery q) {
    return new PageResponse<>(
        claims.list(actor(), q.getSize(), (long) (q.getPage() - 1) * q.getSize()).stream()
            .map(this::view)
            .toList(),
        claims.count(actor()),
        q.getPage(),
        q.getSize());
  }

  public CouponClaim ownClaim(String key) {
    return view(own(key));
  }

  @Transactional
  public RedemptionTicket ticket(String key) {
    activeActor();
    var initial = own(key);
    var first = coupons.find(initial.couponId());
    merchants.requireActiveMerchant(first.merchantId().toString());
    var coupon = coupons.lock(first.id());
    var row = claims.lock(initial.id());
    var now = clock.instant();
    if (!"AVAILABLE".equals(row.status())
        || now.isBefore(coupon.validFrom())
        || !now.isBefore(coupon.validUntil())
        || coupon.publishedAt() == null
        || !"APPROVED".equals(coupon.reviewStatus()))
      throw error(HttpStatus.CONFLICT, "CLAIM_NOT_REDEEMABLE", "此券尚未生效、已到期或已核销");
    if (row.codeIssuedAt() != null
        && row.codeIssuedAt().plus(properties.ticketResendInterval()).isAfter(now))
      throw error(HttpStatus.TOO_MANY_REQUESTS, "TICKET_RATE_LIMITED", "核销凭据生成过于频繁");
    String code = key + "." + codes.random();
    var expires = now.plus(properties.ticketTtl());
    if (expires.isAfter(coupon.validUntil())) expires = coupon.validUntil();
    claims.ticket(row.id(), codes.hash(code), expires, now);
    audit.record(CurrentActor.id(), "REDEMPTION_TICKET_ISSUED", "COUPON_CLAIM", key, Map.of());
    return new RedemptionTicket(code, codes.qr(code), expires);
  }

  private RejectedRedemption invalid() {
    return new RejectedRedemption(
        HttpStatus.BAD_REQUEST, "INVALID_REDEMPTION_CODE", "核销凭据无效、已失效或不属于本店");
  }

  private void rate() {
    var now = clock.instant();
    var row = claims.rate(actor());
    if (row == null || !row.windowStart().plus(properties.redemptionWindow()).isAfter(now))
      claims.resetRate(actor(), now);
    else {
      if (row.attempts() >= properties.redemptionWindowLimit())
        throw new RejectedRedemption(
            HttpStatus.TOO_MANY_REQUESTS, "REDEMPTION_RATE_LIMITED", "核销请求过于频繁，请稍后再试");
      claims.incrementRate(actor());
    }
  }

  @Transactional(noRollbackFor = RejectedRedemption.class)
  public Created<RedemptionView> redeem(CreateRedemptionRequest input) {
    activeActor();
    merchants.requireCouponOperator(input.merchantId());
    rate();
    if (!input.code().matches("[1-9][0-9]{0,17}[.][A-Za-z0-9_-]{43}")) throw invalid();
    String claimKey = input.code().substring(0, input.code().indexOf('.'));
    var initial = claims.find(id(claimKey));
    if (initial == null) throw invalid();
    var target = coupons.find(initial.couponId());
    if (!target.merchantId().toString().equals(input.merchantId())) throw invalid();
    var coupon = coupons.lock(target.id());
    var row = claims.lock(initial.id());
    if (!codes.matches(input.code(), row.redemptionCodeHash())) throw invalid();
    if ("REDEEMED".equals(row.status()))
      return new Created<>(false, claims.redemptionByClaim(row.id()));
    var now = clock.instant();
    if (!"AVAILABLE".equals(row.status())
        || row.codeExpiresAt() == null
        || !row.codeExpiresAt().isAfter(now)
        || now.isBefore(coupon.validFrom())
        || !now.isBefore(coupon.validUntil())) throw invalid();
    if (claims.redeem(row.id(), now) != 1)
      throw error(HttpStatus.CONFLICT, "ALREADY_REDEEMED", "此券已被核销");
    claims.redemption(row.id(), coupon.merchantId(), actor(), now);
    var record = claims.redemptionByClaim(row.id());
    audit.record(
        CurrentActor.id(),
        "COUPON_REDEEMED",
        "COUPON_CLAIM",
        claimKey,
        Map.of("redemptionId", record.id(), "merchantId", input.merchantId()));
    outbox.append(
        new EventPublisherPort.Event(
            UUID.randomUUID().toString(),
            "COUPON_REDEEMED",
            claimKey,
            now,
            Map.of(
                "userId",
                row.userId().toString(),
                "claimId",
                claimKey,
                "redemptionId",
                record.id(),
                "merchantId",
                input.merchantId())));
    return new Created<>(true, record);
  }

  @Transactional
  public RedemptionView redemption(String key) {
    var row = claims.redemptionById(id(key));
    if (row == null) throw error(HttpStatus.NOT_FOUND, "REDEMPTION_NOT_FOUND", "核销记录不存在");
    merchants.requireMerchantMember(row.merchantId());
    return row;
  }

  @Transactional
  public PageResponse<RedemptionView> redemptions(String merchant, PageQuery q) {
    merchants.requireMerchantMember(merchant);
    return new PageResponse<>(
        claims.redemptionList(id(merchant), q.getSize(), (long) (q.getPage() - 1) * q.getSize()),
        claims.redemptionCount(id(merchant)),
        q.getPage(),
        q.getSize());
  }
}
