package com.campus.perks.user.service;

import com.campus.perks.common.api.*;
import com.campus.perks.user.dto.RbacRequests.*;
import com.campus.perks.user.vo.RbacViews.*;
import java.util.List;

public interface RbacService {
  PageResponse<Role> roles(PageQuery q);

  List<Permission> permissions();

  Role role(String id);

  Role create(RoleInput r);

  Role update(String id, RoleUpdate r);

  void delete(String id, int version);

  PageResponse<Account> accounts(PageQuery q);

  Account account(String id);

  Account updateAccount(String id, AccountUpdate r);

  Account grants(String id, Grants r);
}
