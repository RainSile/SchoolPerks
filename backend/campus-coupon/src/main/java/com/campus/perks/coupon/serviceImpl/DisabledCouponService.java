package com.campus.perks.coupon.serviceImpl;

import com.campus.perks.common.api.*;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.coupon.dto.*;
import com.campus.perks.coupon.service.*;
import com.campus.perks.coupon.vo.*;
import com.campus.perks.integration.contract.ReviewRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledCouponService implements CouponService {
  private ApplicationException unavailable() {
    return new ApplicationException(
        HttpStatus.SERVICE_UNAVAILABLE, "COUPON_UNAVAILABLE", "优惠业务需要启用数据库与认证");
  }

  public PageResponse<CouponSummary> query(CouponQuery query) {
    throw unavailable();
  }

  public CouponSummary detail(String id) {
    throw unavailable();
  }

  public MerchantCoupon create(String merchantId, CouponInput request) {
    throw unavailable();
  }

  public MerchantCoupon update(String id, UpdateCouponRequest request) {
    throw unavailable();
  }

  public PageResponse<MerchantCoupon> merchantCoupons(String merchantId, PageQuery query) {
    throw unavailable();
  }

  public MerchantCoupon managed(String id) {
    throw unavailable();
  }

  public CouponReviewRequest submitReview(String id, int version) {
    throw unavailable();
  }

  public PageResponse<CouponReviewRequest> reviewQueue(ReviewQueueQuery query) {
    throw unavailable();
  }

  public CouponReviewRequest reviewRequest(String id) {
    throw unavailable();
  }

  public ReviewRecord decide(String requestId, ReviewDecisionRequest request) {
    throw unavailable();
  }

  public ReviewRecord review(String id) {
    throw unavailable();
  }

  public CouponStatusChange changeStatus(String id, CouponStatusRequest request) {
    throw unavailable();
  }

  public CouponStatusChange statusChange(String id, String changeId) {
    throw unavailable();
  }

  public Created<CouponClaim> claim(String id) {
    throw unavailable();
  }

  public PageResponse<CouponClaim> ownClaims(PageQuery query) {
    throw unavailable();
  }

  public CouponClaim ownClaim(String id) {
    throw unavailable();
  }

  public RedemptionTicket ticket(String id) {
    throw unavailable();
  }

  public Created<RedemptionView> redeem(CreateRedemptionRequest request) {
    throw unavailable();
  }

  public RedemptionView redemption(String id) {
    throw unavailable();
  }

  public PageResponse<RedemptionView> redemptions(String merchantId, PageQuery query) {
    throw unavailable();
  }
}
