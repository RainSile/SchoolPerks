package com.campus.perks.boot.support;

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Correlates HTTP evidence with the JUnit method, including newly created worker threads. */
public final class BusinessTestIdentityExtension
    implements BeforeTestExecutionCallback, AfterTestExecutionCallback {
  private static final InheritableThreadLocal<String> IDENTITY = new InheritableThreadLocal<>();

  public static String current() {
    String value = IDENTITY.get();
    return value == null ? "fixture-or-background" : value;
  }

  @Override
  public void beforeTestExecution(ExtensionContext context) {
    IDENTITY.set(context.getRequiredTestClass().getSimpleName() + "#"
        + context.getRequiredTestMethod().getName());
  }

  @Override
  public void afterTestExecution(ExtensionContext context) {
    IDENTITY.remove();
  }
}
