package com.campus.perks.admin.service;

import com.campus.perks.admin.dto.OperationAuditQuery;
import com.campus.perks.common.api.PageResponse;
import com.campus.perks.security.port.OperationAuditPort;

public interface OperationAuditService {
  PageResponse<OperationAuditPort.Event> query(OperationAuditQuery q);
}
