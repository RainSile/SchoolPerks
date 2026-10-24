package com.campus.perks.trade.serviceImpl;

import com.campus.perks.integration.port.SandboxPaymentPort;
import com.campus.perks.trade.mapper.TradeMapper;
import com.campus.perks.trade.service.TradeProperties;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Bounded scheduler; provider I/O runs outside DB transactions. Durable IDs make retries safe. */
@Component
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class TradeScheduler {
  private static final Logger LOG = LoggerFactory.getLogger(TradeScheduler.class);
  private final TradeMapper db;
  private final OrderServiceImpl service;
  private final SandboxPaymentPort provider;
  private final TradeProperties config;
  private final Clock clock;
  private final boolean enabled;

  public TradeScheduler(
      TradeMapper db,
      OrderServiceImpl service,
      SandboxPaymentPort provider,
      TradeProperties config,
      Clock clock,
      org.springframework.core.env.Environment environment) {
    this.db = db;
    this.service = service;
    this.provider = provider;
    this.config = config;
    this.clock = clock;
    enabled = environment.getProperty("campus.trade.scheduler-enabled", Boolean.class, true);
  }

  @Scheduled(fixedDelayString = "${campus.trade.poll-interval-ms}")
  public void scheduled() {
    if (enabled) runOnce();
  }

  public void runOnce() {
    for (long id : db.dueGroups(clock.instant(), config.batchSize())) {
      try {
        service.sweepGroup(id);
      } catch (RuntimeException e) {
        LOG.warn("Trade group reconciliation failed; resourceId={}", id);
      }
    }
    for (long id : db.dueOrders(clock.instant(), config.batchSize())) {
      try {
        service.sweepOrder(id);
      } catch (RuntimeException e) {
        LOG.warn("Trade order reconciliation failed; resourceId={}", id);
      }
    }
    if (!provider.available()) return;
    for (var payment : db.duePayments(clock.instant(), config.batchSize())) {
      try {
        var state = provider.query(payment.orderNo());
        service.reconcilePayment(payment.id(), state);
        var order = db.order(payment.orderId(), false);
        if (("NOT_FOUND".equals(state.status()) || "WAIT_BUYER_PAY".equals(state.status()))
            && (!clock.instant().isBefore(order.expiresAt())
                || "CANCELLED".equals(order.status()))) {
          provider.close(payment.orderNo());
          // Close may race with payment; query once more instead of trusting an ambiguous close
          // error.
          var confirmed = provider.query(payment.orderNo());
          service.reconcilePayment(payment.id(), confirmed);
          // Absence can be transient. Keep querying until the provider explicitly closes or pays.
          if ("TRADE_CLOSED".equals(confirmed.status()))
            service.closeConfirmed(payment.id(), payment.orderNo());
        }
      } catch (RuntimeException e) {
        db.nextPaymentCheck(payment.id(), clock.instant().plus(config.paymentCheckInterval()));
        LOG.warn("Sandbox payment reconciliation failed; paymentId={}", payment.id());
      }
    }
    for (int i = 0; i < config.batchSize(); i++) {
      var job = service.leaseRefund();
      if (job == null) break;
      try {
        var order = db.order(job.orderId(), false);
        provider.refund(order.orderNo(), job.id(), job.amount());
        service.finishRefund(job);
      } catch (RuntimeException e) {
        service.failRefund(job);
        LOG.warn("Sandbox refund pending retry; refundId={}", job.id());
      }
    }
  }
}
