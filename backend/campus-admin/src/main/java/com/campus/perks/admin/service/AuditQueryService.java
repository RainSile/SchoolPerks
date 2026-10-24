package com.campus.perks.admin.service;

import com.campus.perks.admin.dto.AuditQuery;
import com.campus.perks.admin.vo.AuditEventView;
import com.campus.perks.common.api.*;

public interface AuditQueryService {
  PageResponse<AuditEventView> query(AuditQuery query);

  AuditEventView get(String id);
}
