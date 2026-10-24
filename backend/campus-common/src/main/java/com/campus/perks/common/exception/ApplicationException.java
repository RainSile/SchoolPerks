package com.campus.perks.common.exception;

import org.springframework.http.HttpStatus;

/** Safe client-facing errors only. Never put credentials/provider errors in detail. */
public class ApplicationException extends RuntimeException {
  private final HttpStatus status;
  private final String code;

  public ApplicationException(HttpStatus status, String code, String detail) {
    super(detail);
    this.status = status;
    this.code = code;
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }
}
