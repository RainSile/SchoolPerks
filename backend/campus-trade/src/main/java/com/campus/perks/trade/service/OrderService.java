package com.campus.perks.trade.service;

import com.campus.perks.common.api.*;
import com.campus.perks.trade.dto.TradeRequests.*;
import com.campus.perks.trade.vo.TradeViews.*;

public interface OrderService {
  PageResponse<Offer> catalog(PageQuery query);

  Offer offer(String id);

  PageResponse<Offer> merchantOffers(String merchantId, PageQuery query);

  Offer managedOffer(String merchantId, String offerId);

  Offer createOffer(String merchantId, OfferInput input);

  Offer changeOffer(String id, OfferStatus input);

  PageResponse<Group> groups(String offerId, PageQuery query);

  Group group(String id);

  Result<Order> createOrder(OrderInput input, String requestKey);

  PageResponse<Order> orders(PageQuery query);

  PageResponse<Order> merchantOrders(String merchantId, PageQuery query);

  Order merchantOrder(String merchantId, String orderId);

  Order order(String id);

  Result<Order> cancel(String id);

  Result<Payment> payment(String id);

  Payment payment(String orderId, String paymentId);

  Result<Refund> refund(String id);

  Refund refund(String orderId, String refundId);

  java.util.List<Refund> refunds(String orderId);

  Refund retryRefund(String orderId, String refundId);

  RedemptionCode code(String id);

  Result<Order> redeem(String merchantId, Redemption input);
}
