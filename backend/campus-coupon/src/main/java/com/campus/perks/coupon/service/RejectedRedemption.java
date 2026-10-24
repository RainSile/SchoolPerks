package com.campus.perks.coupon.service;

import com.campus.perks.common.exception.ApplicationException;
import org.springframework.http.HttpStatus;

/**
 * Expected rejection commits the rate counter, while unexpected failures roll back all business
 * writes.
 */
public class RejectedRedemption extends ApplicationException {
  public RejectedRedemption(HttpStatus status, String code, String detail) {
    super(status, code, detail);
  }
}
