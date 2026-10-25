package com.campus.perks.merchant.serviceImpl;

import com.campus.perks.common.api.*;
import com.campus.perks.common.enums.ReviewStatus;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.contract.ReviewRecord;
import com.campus.perks.integration.port.*;
import com.campus.perks.merchant.config.MerchantProperties;
import com.campus.perks.merchant.dto.*;
import com.campus.perks.merchant.entity.*;
import com.campus.perks.merchant.mapper.*;
import com.campus.perks.merchant.service.MerchantWorkflowService;
import com.campus.perks.merchant.vo.*;
import com.campus.perks.security.service.CurrentActor;
import java.time.Clock;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class MerchantWorkflowServiceImpl
    implements MerchantWorkflowService, MerchantReviewPort, MerchantAccessPort {
  private static final String PURPOSE = "MERCHANT_QUALIFICATION";
  private final MerchantWorkflowMapper workflow;
  private final MerchantMapper merchants;
  private final AccountDirectoryPort accounts;
  private final FileAccessPort files;
  private final AuditPort audit;
  private final MerchantProperties properties;
  private final Clock clock;
  private final com.campus.perks.integration.contract.SearchChangeEvents searchEvents;

  public MerchantWorkflowServiceImpl(
      MerchantWorkflowMapper workflow,
      MerchantMapper merchants,
      AccountDirectoryPort accounts,
      FileAccessPort files,
      AuditPort audit,
      MerchantProperties properties,
      Clock clock,
      com.campus.perks.integration.contract.SearchChangeEvents searchEvents) {
    this.workflow = workflow;
    this.merchants = merchants;
    this.accounts = accounts;
    this.files = files;
    this.audit = audit;
    this.properties = properties;
    this.clock = clock;
    this.searchEvents = searchEvents;
  }

  private ApplicationException error(HttpStatus status, String code, String detail) {
    return new ApplicationException(status, code, detail);
  }

  private long id(String id) {
    if (id == null || !id.matches("[1-9][0-9]{0,17}"))
      throw error(HttpStatus.NOT_FOUND, "MERCHANT_NOT_FOUND", "门店不存在");
    return Long.parseLong(id);
  }

  private MerchantEntity store(String key, boolean lock) {
    long id = id(key);
    var m = lock ? merchants.lock(id) : merchants.selectById(id);
    if (m == null) throw error(HttpStatus.NOT_FOUND, "MERCHANT_NOT_FOUND", "门店不存在");
    return m;
  }

  private String member(MerchantEntity m) {
    if (CurrentActor.isBusinessAdministrator()) return "OWNER";
    var role = workflow.role(m.getId(), Long.parseLong(CurrentActor.id()));
    if (role == null) throw error(HttpStatus.NOT_FOUND, "MERCHANT_NOT_FOUND", "门店不存在");
    return role;
  }

  private void owner(MerchantEntity m) {
    if (CurrentActor.isBusinessAdministrator()) return;
    if (!"OWNER".equals(member(m)) || !m.getOwnerId().toString().equals(CurrentActor.id()))
      throw error(HttpStatus.FORBIDDEN, "OWNER_REQUIRED", "此操作仅限店主");
  }

  private void approved(MerchantEntity m) {
    if (m.getStatus() != ReviewStatus.APPROVED)
      throw error(HttpStatus.CONFLICT, "MERCHANT_NOT_APPROVED", "门店尚未通过审核或已暂停");
  }

  private MerchantWorkspace workspace(MerchantEntity m, String role) {
    return new MerchantWorkspace(
        m.getId().toString(),
        m.getName(),
        m.getSchoolId().toString(),
        m.getCategory(),
        m.getAddress(),
        m.getStatus().name(),
        m.getVersion(),
        m.getDescription(),
        m.getBusinessHours(),
        role);
  }

  private Application view(MerchantApplicationRow a) {
    return new Application(
        a.id(),
        a.applicantId().toString(),
        a.schoolId().toString(),
        a.schoolName(),
        a.name(),
        a.category(),
        a.address(),
        a.longitude(),
        a.latitude(),
        a.description(),
        a.qualificationFileId(),
        a.status(),
        a.merchantId() == null ? null : a.merchantId().toString(),
        a.reviewReason(),
        a.submittedAt(),
        a.reviewedAt());
  }

  @Transactional
  public Application submit(CreateMerchantApplicationRequest r) {
    String actor = CurrentActor.id();
    accounts.lockActiveUser(actor);
    accounts.requireSchool(r.schoolId());
    if (!properties.categories().contains(r.category())
        || (r.longitude() == null) != (r.latitude() == null))
      throw error(HttpStatus.BAD_REQUEST, "INVALID_MERCHANT", "分类或坐标不符合要求");
    if (workflow.pending(Long.parseLong(actor)) > 0)
      throw error(HttpStatus.CONFLICT, "APPLICATION_EXISTS", "已有待审核入驻申请");
    files.requireReadyOwned(r.qualificationFileId(), actor, PURPOSE);
    String key = UUID.randomUUID().toString();
    var now = clock.instant();
    workflow.insert(
        new MerchantApplicationRow(
            key,
            Long.parseLong(actor),
            Long.parseLong(r.schoolId()),
            null,
            r.name().strip(),
            r.category(),
            r.address().strip(),
            r.longitude(),
            r.latitude(),
            r.description(),
            r.qualificationFileId(),
            "PENDING",
            null,
            null,
            now,
            null));
    audit.record(actor, "MERCHANT_APPLICATION_SUBMITTED", "MERCHANT_APPLICATION", key, Map.of());
    return view(workflow.find(key));
  }

  public PageResponse<Application> applications(PageQuery q) {
    long actor = Long.parseLong(CurrentActor.id());
    return new PageResponse<>(
        workflow.ownList(actor, q.getSize(), (long) (q.getPage() - 1) * q.getSize()).stream()
            .map(this::view)
            .toList(),
        workflow.ownCount(actor),
        q.getPage(),
        q.getSize());
  }

  public Application application(String key) {
    var a = workflow.find(key);
    if (a == null || !a.applicantId().toString().equals(CurrentActor.id()))
      throw error(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "申请不存在");
    return view(a);
  }

  public PageResponse<MerchantWorkspace> merchants(PageQuery q) {
    long actor = Long.parseLong(CurrentActor.id());
    return new PageResponse<>(
        workflow.ownMerchantIds(actor, q.getSize(), (long) (q.getPage() - 1) * q.getSize()).stream()
            .map(x -> workspace(x.toString()))
            .toList(),
        workflow.ownMerchantCount(actor),
        q.getPage(),
        q.getSize());
  }

  public MerchantWorkspace workspace(String key) {
    var m = store(key, false);
    return workspace(m, member(m));
  }

  @Transactional
  public MerchantWorkspace update(String key, UpdateMerchantRequest r) {
    accounts.lockActiveUser(CurrentActor.id());
    var m = store(key, true);
    owner(m);
    approved(m);
    if (workflow.update(m.getId(), r.version(), r.description(), r.businessHours()) != 1)
      throw error(HttpStatus.CONFLICT, "VERSION_CONFLICT", "门店资料已变更，请刷新后重试");
    audit.record(
        CurrentActor.id(), "MERCHANT_UPDATED", "MERCHANT", key, Map.of("version", r.version() + 1));
    searchEvents.changed("MERCHANT", key);
    return workspace(store(key, false), "OWNER");
  }

  public PageResponse<MemberView> members(String key, PageQuery q) {
    var m = store(key, false);
    owner(m);
    return new PageResponse<>(
        workflow.members(m.getId(), q.getSize(), (long) (q.getPage() - 1) * q.getSize()),
        workflow.memberCount(m.getId()),
        q.getPage(),
        q.getSize());
  }

  public MemberView member(String key, String user) {
    var m = store(key, false);
    owner(m);
    var result = workflow.member(m.getId(), id(user));
    if (result == null) throw error(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "成员不存在");
    return result;
  }

  @Transactional
  public MemberView addMember(String key, CreateMemberRequest r) {
    owner(store(key, false));
    // Account locks precede the merchant lock, including foreign-key audit actors.
    java.util.stream.Stream.of(CurrentActor.id(), r.userId())
        .distinct()
        .sorted(java.util.Comparator.comparingLong(Long::parseLong))
        .forEach(accounts::lockActiveUser);
    var m = store(key, true);
    owner(m);
    approved(m);
    if (workflow.role(m.getId(), Long.parseLong(r.userId())) != null)
      throw error(HttpStatus.CONFLICT, "MEMBER_EXISTS", "该账号已是本店成员");
    if (workflow.memberCount(m.getId()) >= properties.maxMembers())
      throw error(HttpStatus.CONFLICT, "MEMBER_LIMIT", "门店成员数量已达上限");
    accounts.promoteMerchant(r.userId());
    workflow.addMember(m.getId(), Long.parseLong(r.userId()), r.role(), clock.instant());
    audit.record(
        CurrentActor.id(),
        "MERCHANT_MEMBER_ADDED",
        "MERCHANT",
        key,
        Map.of("userId", r.userId(), "role", r.role()));
    return workflow.member(m.getId(), Long.parseLong(r.userId()));
  }

  @Transactional
  public void removeMember(String key, String user) {
    accounts.lockActiveUser(CurrentActor.id());
    var m = store(key, true);
    owner(m);
    approved(m);
    if (m.getOwnerId().toString().equals(user))
      throw error(HttpStatus.CONFLICT, "OWNER_IMMUTABLE", "不能移除店主");
    long target = id(user);
    if (workflow.removeMember(m.getId(), target) == 0)
      throw error(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "成员不存在");
    audit.record(
        CurrentActor.id(), "MERCHANT_MEMBER_REMOVED", "MERCHANT", key, Map.of("userId", user));
  }

  @Transactional
  public FileAccessPort.Download qualification(String key) {
    var m = store(key, false);
    owner(m);
    if (m.getQualificationFileId() == null)
      throw error(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "门店尚未关联资质");
    var link = files.signOwnedDownload(m.getQualificationFileId(), m.getOwnerId().toString());
    audit.record(CurrentActor.id(), "PRIVATE_FILE_DOWNLOAD_ISSUED", "MERCHANT", key, Map.of());
    return link;
  }

  public PageResponse<Application> applications(int page, int size, String status) {
    CurrentActor.require("merchant:review");
    return new PageResponse<>(
        workflow.queue(status, size, (long) (page - 1) * size).stream().map(this::view).toList(),
        workflow.queueCount(status),
        page,
        size);
  }

  public Application inspect(String key) {
    CurrentActor.require("merchant:review");
    var a = workflow.find(key);
    if (a == null) throw error(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "申请不存在");
    return view(a);
  }

  @Transactional
  public ReviewRecord decide(String key, String decision, String reason) {
    CurrentActor.require("merchant:review");
    var initial = workflow.find(key);
    if (initial == null) throw error(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "申请不存在");
    if (initial.applicantId().toString().equals(CurrentActor.id()))
      throw error(HttpStatus.FORBIDDEN, "SELF_REVIEW_FORBIDDEN", "不能审核自己的入驻申请");
    java.util.stream.Stream.of(CurrentActor.id(), initial.applicantId().toString())
        .sorted(java.util.Comparator.comparingLong(Long::parseLong))
        .forEach(
            user -> {
              if (user.equals(CurrentActor.id()) || "APPROVED".equals(decision))
                accounts.lockActiveUser(user);
              else accounts.lockExistingUser(user);
            });
    var a = workflow.lock(key);
    if (!"PENDING".equals(a.status()))
      throw error(HttpStatus.CONFLICT, "ALREADY_REVIEWED", "该申请已经审核");
    Long merchant = null;
    if ("APPROVED".equals(decision)) {
      accounts.promoteMerchant(a.applicantId().toString());
      files.requireReadyOwned(a.qualificationFileId(), a.applicantId().toString(), PURPOSE);
      var m = new MerchantEntity();
      m.setOwnerId(a.applicantId());
      m.setSchoolId(a.schoolId());
      m.setName(a.name());
      m.setCategory(a.category());
      m.setAddress(a.address());
      m.setLongitude(a.longitude());
      m.setLatitude(a.latitude());
      m.setDescription(a.description());
      m.setBusinessHours("");
      m.setQualificationFileId(a.qualificationFileId());
      m.setStatus(ReviewStatus.APPROVED);
      m.setVersion(0);
      merchants.insert(m);
      merchant = m.getId();
      workflow.addMember(merchant, a.applicantId(), "OWNER", clock.instant());
      searchEvents.changed("MERCHANT", merchant.toString());
    } else if (!"REJECTED".equals(decision))
      throw error(HttpStatus.BAD_REQUEST, "INVALID_DECISION", "审核决定不合法");
    var review =
        new ReviewRecord(
            UUID.randomUUID().toString(),
            key,
            CurrentActor.id(),
            decision,
            reason.strip(),
            clock.instant());
    workflow.decide(key, decision, merchant, reason.strip(), clock.instant());
    workflow.review(review);
    audit.record(
        CurrentActor.id(),
        "MERCHANT_APPLICATION_REVIEWED",
        "MERCHANT_APPLICATION",
        key,
        Map.of("decision", decision));
    return review;
  }

  public PageResponse<ReviewRecord> history(int page, int size) {
    CurrentActor.require("merchant:review");
    return new PageResponse<>(
        workflow.history(size, (long) (page - 1) * size), workflow.historyCount(), page, size);
  }

  public ReviewRecord review(String key) {
    CurrentActor.require("merchant:review");
    var r = workflow.reviewById(key);
    if (r == null) throw error(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "审核记录不存在");
    return r;
  }

  @Transactional
  public FileAccessPort.Download reviewQualification(String key) {
    var a = inspect(key);
    var link = files.signOwnedDownload(a.qualificationFileId(), a.applicantId());
    audit.record(
        CurrentActor.id(), "PRIVATE_FILE_DOWNLOAD_ISSUED", "MERCHANT_APPLICATION", key, Map.of());
    return link;
  }

  @Transactional
  public StatusChange changeStatus(String key, String status, int version, String reason) {
    CurrentActor.require("merchant:review");
    accounts.lockActiveUser(CurrentActor.id());
    var m = store(key, true);
    if (m.getOwnerId().toString().equals(CurrentActor.id()))
      throw error(HttpStatus.FORBIDDEN, "SELF_REVIEW_FORBIDDEN", "不能变更本人门店的审核状态");
    String previous = m.getStatus().name();
    if (!("APPROVED".equals(previous) && "SUSPENDED".equals(status)
        || "SUSPENDED".equals(previous) && "APPROVED".equals(status)))
      throw error(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", "门店状态变更不合法");
    if ("APPROVED".equals(status)) {
      if (m.getQualificationFileId() == null)
        throw error(HttpStatus.CONFLICT, "QUALIFICATION_REQUIRED", "门店资质尚未完成核验");
      files.requireReadyOwned(m.getQualificationFileId(), m.getOwnerId().toString(), PURPOSE);
    }
    if (workflow.status(m.getId(), version, status) != 1)
      throw error(HttpStatus.CONFLICT, "VERSION_CONFLICT", "门店状态已变更，请刷新后重试");
    String id = UUID.randomUUID().toString();
    var now = clock.instant();
    workflow.statusHistory(
        id,
        m.getId(),
        Long.parseLong(CurrentActor.id()),
        previous,
        status,
        reason.strip(),
        version + 1,
        now);
    audit.record(
        CurrentActor.id(),
        "MERCHANT_STATUS_CHANGED",
        "MERCHANT",
        key,
        Map.of("previous", previous, "status", status, "version", version + 1));
    searchEvents.changed("MERCHANT", key);
    return new StatusChange(
        id, key, previous, status, version + 1, CurrentActor.id(), reason.strip(), now);
  }

  public MerchantState merchant(String id) {
    CurrentActor.require("merchant:review");
    var m = store(id, false);
    return new MerchantState(id, m.getName(), m.getStatus().name(), m.getVersion());
  }

  public StatusChange statusChange(String key, String changeId) {
    CurrentActor.require("merchant:review");
    var result = workflow.statusChange(changeId, id(key));
    if (result == null) throw error(HttpStatus.NOT_FOUND, "STATUS_CHANGE_NOT_FOUND", "状态变更记录不存在");
    return result;
  }

  public boolean isMerchantMember(String merchant, String user) {
    return workflow.role(id(merchant), Long.parseLong(user)) != null;
  }

  private Scope scope(MerchantEntity m, String role) {
    return new Scope(m.getId().toString(), m.getSchoolId().toString(), m.getName(), role);
  }

  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public Scope requireActiveMerchant(String key) {
    var m = store(key, true);
    approved(m);
    return scope(m, null);
  }

  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public Scope requireCouponOperator(String key) {
    var m = store(key, true);
    String role = member(m);
    approved(m);
    if (!Set.of("OWNER", "MANAGER", "CASHIER").contains(role))
      throw error(HttpStatus.FORBIDDEN, "REDEMPTION_PERMISSION_REQUIRED", "无权核销本店优惠券");
    return scope(m, role);
  }

  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public Scope requireMerchantMember(String key) {
    var m = store(key, true);
    return scope(m, member(m));
  }

  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public Scope requireMerchantAnalyticsReader(String key) {
    var m = store(key, true);
    String role = member(m);
    if (!Set.of("OWNER", "MANAGER").contains(role)
        || !Set.of(ReviewStatus.APPROVED, ReviewStatus.SUSPENDED).contains(m.getStatus()))
      throw error(HttpStatus.FORBIDDEN, "MERCHANT_ANALYTICS_FORBIDDEN", "仅已通过或暂停门店的店主、店长可以查看统计");
    return scope(m, role);
  }

  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public void requirePromotionPublisher(String key) {
    var m = store(key, true);
    String role = member(m);
    approved(m);
    if (!Set.of("OWNER", "MANAGER").contains(role))
      throw error(HttpStatus.FORBIDDEN, "PROMOTION_PERMISSION_REQUIRED", "无权发布本店优惠");
    if (m.getQualificationFileId() == null)
      throw error(HttpStatus.CONFLICT, "QUALIFICATION_REQUIRED", "门店资质尚未完成核验");
    files.requireReadyOwned(m.getQualificationFileId(), m.getOwnerId().toString(), PURPOSE);
  }
}
