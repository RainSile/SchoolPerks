package com.campus.perks.merchant.serviceImpl;

import com.campus.perks.merchant.mapper.MerchantWorkflowMapper;
import com.campus.perks.security.port.PermissionContributor;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class MerchantPermissionContributor implements PermissionContributor {
  private final MerchantWorkflowMapper mapper;

  public MerchantPermissionContributor(MerchantWorkflowMapper mapper) {
    this.mapper = mapper;
  }

  public List<String> permissions(String user) {
    if (mapper.memberships(Long.parseLong(user)) == 0) return List.of();
    return mapper.dashboardMemberships(Long.parseLong(user)) > 0
        ? List.of("merchant:read", "merchant:analytics:read")
        : List.of("merchant:read");
  }
}
