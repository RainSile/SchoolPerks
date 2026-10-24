package com.campus.perks.user.serviceImpl;

import com.campus.perks.common.api.*;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.contract.ReviewRecord;
import com.campus.perks.integration.port.*;
import com.campus.perks.security.service.CurrentActor;
import com.campus.perks.user.mapper.CampusReviewMapper;
import com.campus.perks.user.service.AuthSecrets;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class CampusReviewService implements CampusReviewPort {
  private final CampusReviewMapper mapper;
  private final AuthSecrets secrets;
  private final ObjectMapper json;
  private final AccountDirectoryPort accounts;
  private final AuditPort audit;
  private final Clock clock;

  public CampusReviewService(
      CampusReviewMapper mapper,
      AuthSecrets secrets,
      ObjectMapper json,
      AccountDirectoryPort accounts,
      AuditPort audit,
      Clock clock) {
    this.mapper = mapper;
    this.secrets = secrets;
    this.json = json;
    this.accounts = accounts;
    this.audit = audit;
    this.clock = clock;
  }

  private ApplicationException error(HttpStatus status, String code, String detail) {
    return new ApplicationException(status, code, detail);
  }

  public PageResponse<Entry> applications(int page, int size, String status) {
    CurrentActor.require("campus:review");
    return new PageResponse<>(
        mapper.queue(status, size, (long) (page - 1) * size), mapper.count(status), page, size);
  }

  @Transactional
  public Identity identity(String id) {
    CurrentActor.require("campus:review");
    var row = mapper.find(id);
    if (row == null) throw error(HttpStatus.NOT_FOUND, "VERIFICATION_NOT_FOUND", "认证申请不存在");
    try {
      var value =
          json.readTree(secrets.decryptIdentity(row.userId().toString(), mapper.identity(id)));
      audit.record(
          CurrentActor.id(), "CAMPUS_IDENTITY_VIEWED", "CAMPUS_VERIFICATION", id, Map.of());
      return new Identity(value.get("realName").asText(), value.get("studentNumber").asText());
    } catch (JsonProcessingException e) {
      throw error(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_UNAVAILABLE", "身份资料暂时无法读取");
    }
  }

  @Transactional
  public ReviewRecord decide(String id, String decision, String reason) {
    CurrentActor.require("campus:review");
    var initial = mapper.find(id);
    if (initial == null) throw error(HttpStatus.NOT_FOUND, "VERIFICATION_NOT_FOUND", "认证申请不存在");
    if (initial.userId().toString().equals(CurrentActor.id()))
      throw error(HttpStatus.FORBIDDEN, "SELF_REVIEW_FORBIDDEN", "不能审核本人的校园认证");
    java.util.stream.Stream.of(CurrentActor.id(), initial.userId().toString())
        .sorted(java.util.Comparator.comparingLong(Long::parseLong))
        .forEach(
            user -> {
              if (user.equals(CurrentActor.id()) || "APPROVED".equals(decision))
                accounts.lockActiveUser(user);
              else accounts.lockExistingUser(user);
            });
    var row = mapper.lock(id);
    if (!"PENDING".equals(row.status()))
      throw error(HttpStatus.CONFLICT, "ALREADY_REVIEWED", "认证申请已经审核");
    if ("APPROVED".equals(decision)) mapper.approveSchool(row.userId(), row.schoolId());
    else if (!"REJECTED".equals(decision))
      throw error(HttpStatus.BAD_REQUEST, "INVALID_DECISION", "审核决定不合法");
    var record =
        new ReviewRecord(
            UUID.randomUUID().toString(),
            id,
            CurrentActor.id(),
            decision,
            reason.strip(),
            clock.instant());
    mapper.decide(id, decision, reason.strip(), clock.instant());
    mapper.insertReview(record);
    audit.record(
        CurrentActor.id(),
        "CAMPUS_VERIFICATION_REVIEWED",
        "CAMPUS_VERIFICATION",
        id,
        Map.of("decision", decision));
    return record;
  }

  public ReviewRecord review(String id) {
    CurrentActor.require("campus:review");
    var row = mapper.review(id);
    if (row == null) throw error(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "审核记录不存在");
    return row;
  }
}
