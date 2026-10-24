package com.campus.perks.merchant.service;

import com.campus.perks.common.api.*;
import com.campus.perks.integration.port.*;
import com.campus.perks.merchant.dto.*;
import com.campus.perks.merchant.vo.*;

public interface MerchantWorkflowService {
  MerchantReviewPort.Application submit(CreateMerchantApplicationRequest request);

  PageResponse<MerchantReviewPort.Application> applications(PageQuery query);

  MerchantReviewPort.Application application(String id);

  PageResponse<MerchantWorkspace> merchants(PageQuery query);

  MerchantWorkspace workspace(String id);

  MerchantWorkspace update(String id, UpdateMerchantRequest request);

  PageResponse<MemberView> members(String id, PageQuery query);

  MemberView member(String id, String userId);

  MemberView addMember(String id, CreateMemberRequest request);

  void removeMember(String id, String userId);

  FileAccessPort.Download qualification(String id);
}
