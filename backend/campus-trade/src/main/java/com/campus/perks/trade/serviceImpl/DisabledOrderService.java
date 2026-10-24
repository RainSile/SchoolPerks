package com.campus.perks.trade.serviceImpl;

import com.campus.perks.common.api.*;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.trade.dto.TradeRequests.*;
import com.campus.perks.trade.service.OrderService;
import com.campus.perks.trade.vo.TradeViews.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledOrderService implements OrderService {
  public Offer managedOffer(String merchantId, String offerId) {
    throw unavailable();
  }

  public Order merchantOrder(String merchantId, String orderId) {
    throw unavailable();
  }

  private ApplicationException unavailable() {
    return new ApplicationException(
        HttpStatus.SERVICE_UNAVAILABLE, "TRADE_UNAVAILABLE", "交易业务需要启用数据库与认证");
  }

  public PageResponse<Offer> catalog(PageQuery q) {
    throw unavailable();
  }

  public Offer offer(String id) {
    throw unavailable();
  }

  public PageResponse<Offer> merchantOffers(String id, PageQuery q) {
    throw unavailable();
  }

  public Offer createOffer(String id, OfferInput i) {
    throw unavailable();
  }

  public Offer changeOffer(String id, OfferStatus i) {
    throw unavailable();
  }

  public PageResponse<Group> groups(String id, PageQuery q) {
    throw unavailable();
  }

  public Group group(String id) {
    throw unavailable();
  }

  public Result<Order> createOrder(OrderInput i, String key) {
    throw unavailable();
  }

  public PageResponse<Order> orders(PageQuery q) {
    throw unavailable();
  }

  public PageResponse<Order> merchantOrders(String id, PageQuery q) {
    throw unavailable();
  }

  public Order order(String id) {
    throw unavailable();
  }

  public Result<Order> cancel(String id) {
    throw unavailable();
  }

  public Result<Payment> payment(String id) {
    throw unavailable();
  }

  public Payment payment(String id, String payment) {
    throw unavailable();
  }

  public Result<Refund> refund(String id) {
    throw unavailable();
  }

  public Refund refund(String id, String refund) {
    throw unavailable();
  }

  public Refund retryRefund(String id, String refund) {
    throw unavailable();
  }

  public java.util.List<Refund> refunds(String id) {
    throw unavailable();
  }

  public RedemptionCode code(String id) {
    throw unavailable();
  }

  public Result<Order> redeem(String id, Redemption i) {
    throw unavailable();
  }
}
