package com.campus.perks.security.service;

import java.lang.annotation.*;

/** Opt-in controller boundary audit. Never serializes arguments, results or exception messages. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditedOperation {
  String action();

  String resourceType();

  int resourceArgument() default -1;
}
