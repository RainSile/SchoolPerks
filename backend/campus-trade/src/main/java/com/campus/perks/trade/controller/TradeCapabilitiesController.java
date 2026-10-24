package com.campus.perks.trade.controller;

import com.campus.perks.integration.port.SandboxPaymentPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "交易能力")
public class TradeCapabilitiesController {
  private final SandboxPaymentPort provider;
  private final Environment environment;

  public TradeCapabilitiesController(SandboxPaymentPort provider, Environment environment) {
    this.provider = provider;
    this.environment = environment;
  }

  public record Capabilities(
      boolean enabled, boolean paymentAvailable, String paymentEnvironment) {}

  @GetMapping("/api/v1/trade-capabilities")
  @Operation(summary = "交易与沙箱支付可用性，不返回凭据")
  public Capabilities capabilities() {
    boolean enabled = environment.getProperty("campus.auth.enabled", Boolean.class, false);
    return new Capabilities(enabled, enabled && provider.available(), "ALIPAY_SANDBOX");
  }
}
