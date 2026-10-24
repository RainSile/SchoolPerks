package com.campus.perks.common.exception;

public class FeatureNotImplementedException extends RuntimeException {
  public FeatureNotImplementedException(String feature) {
    super(feature + " 尚未实现，请按开发交接文档完成业务逻辑");
  }
}
