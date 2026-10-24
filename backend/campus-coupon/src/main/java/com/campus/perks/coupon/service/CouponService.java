package com.campus.perks.coupon.service;

import com.campus.perks.common.api.*;
import com.campus.perks.coupon.dto.*;
import com.campus.perks.coupon.vo.*;
import com.campus.perks.integration.contract.ReviewRecord;

public interface CouponService {
  PageResponse<CouponSummary> query(CouponQuery query);

  CouponSummary detail(String id);

  MerchantCoupon create(String merchantId, CouponInput request);

  MerchantCoupon update(String id, UpdateCouponRequest request);

  PageResponse<MerchantCoupon> merchantCoupons(String merchantId, PageQuery query);

  MerchantCoupon managed(String id);

  CouponReviewRequest submitReview(String id, int version);

  PageResponse<CouponReviewRequest> reviewQueue(ReviewQueueQuery query);

  CouponReviewRequest reviewRequest(String id);

  ReviewRecord decide(String requestId, ReviewDecisionRequest request);

  ReviewRecord review(String id);

  CouponStatusChange changeStatus(String id, CouponStatusRequest request);

  CouponStatusChange statusChange(String id, String changeId);

  Created<CouponClaim> claim(String id);

  PageResponse<CouponClaim> ownClaims(PageQuery query);

  CouponClaim ownClaim(String id);

  RedemptionTicket ticket(String id);

  Created<RedemptionView> redeem(CreateRedemptionRequest request);

  RedemptionView redemption(String id);

  PageResponse<RedemptionView> redemptions(String merchantId, PageQuery query);
}
