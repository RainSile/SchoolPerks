package com.campus.perks.notification.serviceImpl;

import com.campus.perks.common.api.*;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.notification.service.NotificationQueries;
import com.campus.perks.notification.vo.NotificationView;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledNotificationQueries implements NotificationQueries {
  private ApplicationException unavailable() {
    return new ApplicationException(
        HttpStatus.SERVICE_UNAVAILABLE, "NOTIFICATION_UNAVAILABLE", "通知需要启用认证和数据库");
  }

  public PageResponse<NotificationView> list(PageQuery q) {
    throw unavailable();
  }

  public NotificationView get(String id) {
    throw unavailable();
  }

  public NotificationView read(String id) {
    throw unavailable();
  }
}
