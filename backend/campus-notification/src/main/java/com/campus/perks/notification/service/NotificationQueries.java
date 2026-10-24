package com.campus.perks.notification.service;

import com.campus.perks.common.api.*;
import com.campus.perks.notification.vo.NotificationView;

public interface NotificationQueries {
  PageResponse<NotificationView> list(PageQuery q);

  NotificationView get(String id);

  NotificationView read(String id);
}
