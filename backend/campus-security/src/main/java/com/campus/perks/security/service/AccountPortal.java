package com.campus.perks.security.service;

/** A portal is exclusive; roles add permissions within it, never another portal. */
public enum AccountPortal {
  USER("/app"),
  MERCHANT("/merchant"),
  ADMIN("/admin");

  private final String home;

  AccountPortal(String home) {
    this.home = home;
  }

  public String home() {
    return home;
  }

  public String authority() {
    return "PORTAL_" + name();
  }
}
