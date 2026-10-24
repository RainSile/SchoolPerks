package com.campus.perks.common.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(ApplicationException.class)
  public ProblemDetail application(ApplicationException ex) {
    return problem(ex.status(), ex.code(), ex.getMessage());
  }

  @ExceptionHandler(FeatureNotImplementedException.class)
  public ProblemDetail unfinished(FeatureNotImplementedException ex) {
    return problem(HttpStatus.NOT_IMPLEMENTED, "FEATURE_NOT_IMPLEMENTED", ex.getMessage());
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    ConstraintViolationException.class,
    HttpMessageNotReadableException.class,
    HandlerMethodValidationException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ProblemDetail badRequest(Exception ex) {
    return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "请求格式或参数校验失败");
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ProblemDetail notFound() {
    return problem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "资源不存在");
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ProblemDetail methodNotAllowed() {
    return problem(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "请求方法不受支持");
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ProblemDetail mediaType() {
    return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "请求媒体类型不受支持");
  }

  // Do not catch Exception here: Spring Security must retain its 401/403 semantics.
  private ProblemDetail problem(HttpStatus status, String code, String detail) {
    ProblemDetail result = ProblemDetail.forStatusAndDetail(status, detail);
    result.setProperty("code", code);
    return result;
  }
}
