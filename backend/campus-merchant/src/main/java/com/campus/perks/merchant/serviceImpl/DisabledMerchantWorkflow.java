package com.campus.perks.merchant.serviceImpl;

import com.campus.perks.common.api.*;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.contract.ReviewRecord;
import com.campus.perks.integration.port.*;
import com.campus.perks.merchant.dto.*;
import com.campus.perks.merchant.service.MerchantWorkflowService;
import com.campus.perks.merchant.vo.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledMerchantWorkflow
    implements MerchantWorkflowService, MerchantReviewPort, MerchantAccessPort {
  private ApplicationException unavailable() {
    return new ApplicationException(
        HttpStatus.SERVICE_UNAVAILABLE, "MERCHANT_SERVICE_UNAVAILABLE", "商家服务尚未启用");
  }

  public Application submit(CreateMerchantApplicationRequest r) {
    throw unavailable();
  }

  public PageResponse<Application> applications(PageQuery q) {
    throw unavailable();
  }

  public Application application(String id) {
    throw unavailable();
  }

  public PageResponse<MerchantWorkspace> merchants(PageQuery q) {
    throw unavailable();
  }

  public MerchantWorkspace workspace(String id) {
    throw unavailable();
  }

  public MerchantWorkspace update(String id, UpdateMerchantRequest r) {
    throw unavailable();
  }

  public PageResponse<MemberView> members(String id, PageQuery q) {
    throw unavailable();
  }

  public MemberView member(String id, String user) {
    throw unavailable();
  }

  public MemberView addMember(String id, CreateMemberRequest r) {
    throw unavailable();
  }

  public void removeMember(String id, String user) {
    throw unavailable();
  }

  public FileAccessPort.Download qualification(String id) {
    throw unavailable();
  }

  public PageResponse<Application> applications(int page, int size, String status) {
    throw unavailable();
  }

  public Application inspect(String id) {
    throw unavailable();
  }

  public ReviewRecord decide(String id, String decision, String reason) {
    throw unavailable();
  }

  public PageResponse<ReviewRecord> history(int page, int size) {
    throw unavailable();
  }

  public ReviewRecord review(String id) {
    throw unavailable();
  }

  public FileAccessPort.Download reviewQualification(String id) {
    throw unavailable();
  }

  public StatusChange changeStatus(String id, String status, int version, String reason) {
    throw unavailable();
  }

  public MerchantState merchant(String id) {
    throw unavailable();
  }

  public StatusChange statusChange(String merchant, String change) {
    throw unavailable();
  }

  public void requirePromotionPublisher(String id) {
    throw unavailable();
  }

  public boolean isMerchantMember(String merchant, String user) {
    throw unavailable();
  }

  public Scope requireActiveMerchant(String id) {
    throw unavailable();
  }

  public Scope requireCouponOperator(String id) {
    throw unavailable();
  }

  public Scope requireMerchantMember(String id) {
    throw unavailable();
  }

  public Scope requireMerchantAnalyticsReader(String id) {
    throw unavailable();
  }
}
