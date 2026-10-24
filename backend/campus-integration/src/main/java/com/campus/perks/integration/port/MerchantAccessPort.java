package com.campus.perks.integration.port;

/** Write guards join the caller's transaction and lock the merchant before coupon/claim rows. */
public interface MerchantAccessPort {
  void requirePromotionPublisher(String merchantId);

  Scope requireActiveMerchant(String merchantId);

  Scope requireCouponOperator(String merchantId);

  Scope requireMerchantMember(String merchantId);

  /** Locks current store/membership; only OWNER/MANAGER of approved or suspended stores. */
  Scope requireMerchantAnalyticsReader(String merchantId);

  boolean isMerchantMember(String merchantId, String userId);

  record Scope(String id, String schoolId, String name, String role) {}
}
