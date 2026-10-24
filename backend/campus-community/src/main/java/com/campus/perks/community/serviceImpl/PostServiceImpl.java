package com.campus.perks.community.serviceImpl;

import com.campus.perks.common.api.*;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.community.dto.*;
import com.campus.perks.community.dto.CommunityRequests.*;
import com.campus.perks.community.entity.CommunityRows;
import com.campus.perks.community.entity.CommunityRows.Post;
import com.campus.perks.community.mapper.CommunityMapper;
import com.campus.perks.community.service.*;
import com.campus.perks.community.vo.*;
import com.campus.perks.community.vo.CommunityViews.*;
import com.campus.perks.integration.contract.SearchChangeEvents;
import com.campus.perks.integration.port.*;
import com.campus.perks.security.service.CurrentActor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.Normalizer;
import java.time.Clock;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
@EnableConfigurationProperties(CommunityProperties.class)
@Transactional(isolation = Isolation.READ_COMMITTED)
public class PostServiceImpl implements PostService {
  private final CommunityMapper db;
  private final CommunityInteractions interactions;
  private final AccountDirectoryPort accounts;
  private final MerchantAccessPort merchants;
  private final FileAccessPort files;
  private final AuditPort audit;
  private final SearchChangeEvents events;
  private final ObjectMapper json;
  private final Clock clock;
  private final CommunityProperties config;

  public PostServiceImpl(
      CommunityMapper db,
      CommunityInteractions interactions,
      AccountDirectoryPort accounts,
      MerchantAccessPort merchants,
      FileAccessPort files,
      AuditPort audit,
      SearchChangeEvents events,
      ObjectMapper json,
      Clock clock,
      CommunityProperties config) {
    this.db = db;
    this.interactions = interactions;
    this.accounts = accounts;
    this.merchants = merchants;
    this.files = files;
    this.audit = audit;
    this.events = events;
    this.json = json;
    this.clock = clock;
    this.config = config;
  }

  private ApplicationException error(HttpStatus status, String code, String text) {
    return new ApplicationException(status, code, text);
  }

  private ApplicationException missing() {
    return error(HttpStatus.NOT_FOUND, "COMMUNITY_RESOURCE_NOT_FOUND", "社区资源不存在或无权访问");
  }

  private ApplicationException conflict() {
    return error(HttpStatus.CONFLICT, "COMMUNITY_STATE_CONFLICT", "内容状态或版本已变化，请刷新");
  }

  private long id(String value) {
    if (value == null || !value.matches("[1-9][0-9]{0,17}")) throw missing();
    return Long.parseLong(value);
  }

  private long actor() {
    return id(CurrentActor.id());
  }

  private long offset(PageQuery q) {
    return (long) (q.getPage() - 1) * q.getSize();
  }

  private String normalize(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).strip();
  }

  private void text(String value, int max) {
    if (value == null
        || value.isBlank()
        || value.length() > max
        || value.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t'))
      throw error(
          HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_COMMUNITY_CONTENT", "文字内容为空、过长或含无效控制字符");
    String normalized = normalize(value);
    if (config.blockedTerms().stream()
        .filter(t -> !t.isBlank())
        .anyMatch(t -> normalized.contains(normalize(t))))
      throw error(HttpStatus.UNPROCESSABLE_ENTITY, "CONTENT_POLICY_REJECTED", "内容包含配置的禁止词，请修改后提交");
  }

  private void content(Content c, long author) {
    text(c.title(), 120);
    text(c.body(), config.maxBodyLength());
    if (c.imageIds().size() > config.maxImages()
        || new HashSet<>(c.imageIds()).size() != c.imageIds().size())
      throw error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_POST_IMAGES", "图片数量或引用无效");
    for (String image : c.imageIds())
      files.requireReadyOwned(image, "" + author, "COMMUNITY_IMAGE");
  }

  private Post post(long id, boolean lock) {
    var row = db.post(id, lock);
    if (row == null) throw missing();
    return row;
  }

  private Post own(String id, boolean lock) {
    var row = lock ? lockPost(id, actor(), false) : post(id(id), false);
    if (row.authorId() != actor()) throw missing();
    return row;
  }

  /** Accounts are locked in numeric order, then an optional merchant, then the post. */
  private Post lockPost(String key, long actor, boolean visible) {
    var route = post(id(key), false);
    var ids = new TreeSet<Long>();
    ids.add(actor);
    ids.add(route.authorId());
    for (long user : ids) {
      if (user == actor) accounts.lockActiveUser("" + user);
      else accounts.lockExistingUser("" + user);
    }
    if (visible && route.merchantId() != null)
      merchants.requireActiveMerchant("" + route.merchantId());
    var row = post(route.id(), true);
    if (visible && db.visibleCurrent(row.id()) == null) throw missing();
    return row;
  }

  private Post publicPost(String key) {
    var row = db.visible(id(key));
    if (row == null) throw missing();
    return row;
  }

  private void record(String action, String type, long id) {
    audit.record(CurrentActor.id(), action, type, "" + id, Map.of());
  }

  private PostSummary view(Post p) {
    return view(p, false);
  }

  private PostSummary ownView(Post p) {
    return view(p, true);
  }

  private PostSummary view(Post p, boolean own) {
    return new PostSummary(
        "" + p.id(),
        "" + p.schoolId(),
        p.merchantId() == null ? null : p.merchantId().toString(),
        p.title(),
        p.body(),
        p.status(),
        p.version(),
        db.images(p.id()),
        interactions.likeCount(p.id()),
        interactions.commentCount(p.id()),
        p.createdAt(),
        p.publishedAt(),
        own && Set.of("HIDDEN", "REJECTED").contains(p.status())
            ? db.latestReason(p.id(), p.status())
            : null);
  }

  private Review view(CommunityRows.Review r) {
    try {
      return new Review(
          r.id(),
          "" + r.postId(),
          r.contentVersion(),
          json.readTree(r.snapshot()),
          r.status(),
          r.reason(),
          r.createdAt(),
          r.reviewedAt());
    } catch (Exception e) {
      throw new IllegalStateException("Invalid community snapshot");
    }
  }

  private Comment view(CommunityRows.Comment c) {
    return new Comment(
        "" + c.id(), "" + c.postId(), c.body(), c.status(), c.version(), c.createdAt());
  }

  private Report view(CommunityRows.Report r) {
    return new Report(
        "" + r.id(),
        "" + r.postId(),
        r.reason(),
        r.status(),
        r.version(),
        r.resolution(),
        r.createdAt());
  }

  public PageResponse<PostSummary> query(PostQuery q) {
    String keyword =
        q.getKeyword() == null || q.getKeyword().isBlank() ? null : q.getKeyword().strip();
    return new PageResponse<>(
        db.catalog(keyword, q.getSchoolId(), q.getSize(), offset(q)).stream()
            .map(this::view)
            .toList(),
        db.catalogCount(keyword, q.getSchoolId()),
        q.getPage(),
        q.getSize());
  }

  public PostSummary detail(String id) {
    return view(publicPost(id));
  }

  public PostSummary managed(String id) {
    return ownView(own(id, false));
  }

  public PageResponse<PostSummary> own(PageQuery q) {
    return new PageResponse<>(
        db.own(actor(), q.getSize(), offset(q)).stream().map(this::ownView).toList(),
        db.ownCount(actor()),
        q.getPage(),
        q.getSize());
  }

  public PostSummary create(CreatePost input) {
    long actor = actor();
    accounts.lockActiveUser("" + actor);
    accounts.requireSchool(input.schoolId());
    if (input.merchantId() != null) {
      var store = merchants.requireActiveMerchant(input.merchantId());
      if (!store.schoolId().equals(input.schoolId()))
        throw error(HttpStatus.UNPROCESSABLE_ENTITY, "POST_SCHOOL_MISMATCH", "门店与校园不匹配");
    }
    content(input.content(), actor);
    long id =
        db.create(
            actor,
            id(input.schoolId()),
            input.merchantId() == null ? null : id(input.merchantId()),
            input.content().title().strip(),
            input.content().body().strip(),
            clock.instant());
    db.images(id, input.content().imageIds());
    record("POST_CREATED", "POST", id);
    return view(post(id, true));
  }

  public PostSummary update(String id, UpdatePost input) {
    var p = own(id, true);
    if (p.version() != input.version()
        || !Set.of("DRAFT", "REJECTED", "HIDDEN", "PUBLISHED").contains(p.status()))
      throw conflict();
    content(input.content(), p.authorId());
    db.edit(
        p.id(), input.content().title().strip(), input.content().body().strip(), clock.instant());
    db.images(p.id(), input.content().imageIds());
    events.changed("POST", id);
    record("POST_EDITED", "POST", p.id());
    return view(post(p.id(), true));
  }

  public void delete(String id) {
    var p = own(id, true);
    if ("DELETED".equals(p.status())) return;
    db.withdrawReviews(p.id(), actor(), clock.instant());
    db.status(p.id(), "DELETED", clock.instant());
    events.changed("POST", id);
    record("POST_DELETED", "POST", p.id());
  }

  public Review submit(String key, Version input) {
    var p = own(key, true);
    if (p.version() != input.version() || !Set.of("DRAFT", "REJECTED").contains(p.status()))
      throw conflict();
    content(new Content(p.title(), p.body(), db.images(p.id())), p.authorId());
    String snapshot;
    try {
      snapshot = json.writeValueAsString(view(p));
    } catch (Exception e) {
      throw new IllegalStateException("Community serialization failed");
    }
    String request = UUID.randomUUID().toString();
    db.submit(request, p, snapshot, clock.instant());
    db.status(p.id(), "PENDING", clock.instant());
    record("POST_REVIEW_REQUESTED", "POST", p.id());
    return view(db.review(request, true));
  }

  private CommunityRows.Review reviewRow(String key, boolean lock) {
    var r = db.review(key, lock);
    if (r == null) throw missing();
    return r;
  }

  public Review review(String key) {
    var r = reviewRow(key, false);
    var p = post(r.postId(), false);
    if (p.authorId() != actor()) CurrentActor.require("community:moderate");
    return view(r);
  }

  public PageResponse<Review> reviews(ModerationQuery q) {
    CurrentActor.require("community:moderate");
    return new PageResponse<>(
        db.reviews(q.getStatus(), q.getSize(), offset(q)).stream().map(this::view).toList(),
        db.reviewCount(q.getStatus()),
        q.getPage(),
        q.getSize());
  }

  public Review decide(String key, Decision input) {
    CurrentActor.require("community:moderate");
    var route = reviewRow(key, false);
    var p = lockPost("" + route.postId(), actor(), false);
    if (p.authorId() == actor()
        || p.merchantId() != null
            && merchants.isMerchantMember("" + p.merchantId(), CurrentActor.id()))
      throw error(HttpStatus.FORBIDDEN, "SELF_REVIEW_FORBIDDEN", "不能审核自己或关联门店的帖子");
    var request = reviewRow(key, true);
    if (!"PENDING".equals(request.status())
        || !"PENDING".equals(p.status())
        || p.version() != request.contentVersion() + 1) throw conflict();
    boolean approve = input.decision() == ReviewDecision.APPROVED;
    if (approve) {
      accounts.lockActiveUser("" + p.authorId());
      content(new Content(p.title(), p.body(), db.images(p.id())), p.authorId());
    }
    db.decide(key, input.decision().name(), actor(), input.reason().strip(), clock.instant());
    db.status(p.id(), approve ? "PUBLISHED" : "REJECTED", clock.instant());
    moderation("POST", p.id(), p.status(), approve ? "PUBLISHED" : "REJECTED", input.reason());
    events.changed("POST", "" + p.id());
    return view(reviewRow(key, true));
  }

  private void moderation(String type, long id, String previous, String status, String reason) {
    db.moderation(
        UUID.randomUUID().toString(),
        type,
        id,
        actor(),
        previous,
        status,
        reason.strip(),
        clock.instant());
    record("COMMUNITY_MODERATED", type, id);
  }

  public PostSummary hide(String key, HideInput input) {
    CurrentActor.require("community:moderate");
    var p = lockPost(key, actor(), false);
    if (p.version() != input.version() || !"PUBLISHED".equals(p.status())) throw conflict();
    db.status(p.id(), "HIDDEN", clock.instant());
    moderation("POST", p.id(), p.status(), "HIDDEN", input.reason());
    events.changed("POST", key);
    return view(post(p.id(), true));
  }

  public List<ImageTicket> images(String key) {
    var p = post(id(key), false);
    boolean privileged = p.authorId() == actor();
    if (!privileged) {
      var auth =
          org.springframework.security.core.context.SecurityContextHolder.getContext()
              .getAuthentication();
      privileged =
          auth.getAuthorities().stream()
              .anyMatch(a -> a.getAuthority().equals("community:moderate"));
    }
    if (!privileged) publicPost(key);
    var result = new ArrayList<ImageTicket>();
    for (String image : db.images(p.id())) {
      var link = files.signOwnedDownload(image, "" + p.authorId());
      result.add(new ImageTicket(image, link.url(), link.expiresAt()));
    }
    record("POST_IMAGES_VIEWED", "POST", p.id());
    return result;
  }

  public PageResponse<Comment> comments(String key, PageQuery q) {
    var p = publicPost(key);
    return new PageResponse<>(
        interactions.comments(p.id(), q.getSize(), offset(q)).stream().map(this::view).toList(),
        interactions.commentCount(p.id()),
        q.getPage(),
        q.getSize());
  }

  private void requestKey(String key) {
    if (key == null || !key.matches("[A-Za-z0-9_-]{8,64}"))
      throw error(
          HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key需为8至64位字母、数字、下划线或连字符");
  }

  public Result<Comment> comment(String key, CommentInput input, String requestKey) {
    requestKey(requestKey);
    var p = lockPost(key, actor(), true);
    text(input.body(), config.maxCommentLength());
    var old = interactions.requestedComment(p.id(), actor(), requestKey);
    if (old != null) {
      if (!old.body().equals(input.body().strip()))
        throw error(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "请求键已用于其他评论内容");
      return new Result<>(false, view(old));
    }
    long id =
        interactions.addComment(p.id(), actor(), requestKey, input.body().strip(), clock.instant());
    record("COMMENT_CREATED", "POST_COMMENT", id);
    return new Result<>(true, view(interactions.comment(id, true)));
  }

  private CommunityRows.Comment commentRow(String key, boolean lock) {
    var c = interactions.comment(id(key), lock);
    if (c == null) throw missing();
    return c;
  }

  public Comment comment(String key) {
    var c = commentRow(key, false);
    if (c.authorId() != actor()) {
      var auth =
          org.springframework.security.core.context.SecurityContextHolder.getContext()
              .getAuthentication();
      boolean moderator =
          auth.getAuthorities().stream()
              .anyMatch(a -> a.getAuthority().equals("community:moderate"));
      if (!moderator) {
        publicPost("" + c.postId());
        if (!"APPROVED".equals(c.status())) throw missing();
      }
    }
    return view(c);
  }

  public PageResponse<Comment> ownComments(PageQuery q) {
    return new PageResponse<>(
        interactions.ownComments(actor(), q.getSize(), offset(q)).stream().map(this::view).toList(),
        interactions.ownCommentCount(actor()),
        q.getPage(),
        q.getSize());
  }

  public void deleteComment(String key) {
    var route = commentRow(key, false);
    if (route.authorId() != actor()) throw missing();
    lockPost("" + route.postId(), actor(), false);
    var c = commentRow(key, true);
    if ("DELETED".equals(c.status())) return;
    interactions.commentStatus(c.id(), "DELETED", clock.instant());
    record("COMMENT_DELETED", "POST_COMMENT", c.id());
  }

  public PageResponse<Comment> commentQueue(ModerationQuery q) {
    CurrentActor.require("community:moderate");
    return new PageResponse<>(
        interactions.commentQueue(q.getStatus(), q.getSize(), offset(q)).stream()
            .map(this::view)
            .toList(),
        interactions.commentQueueCount(q.getStatus()),
        q.getPage(),
        q.getSize());
  }

  public Comment moderateComment(String key, CommentDecision input) {
    CurrentActor.require("community:moderate");
    var route = commentRow(key, false);
    var users =
        new TreeSet<Long>(
            List.of(actor(), route.authorId(), post(route.postId(), false).authorId()));
    for (long user : users) {
      if (user == actor()) accounts.lockActiveUser("" + user);
      else accounts.lockExistingUser("" + user);
    }
    var p = post(route.postId(), true);
    var c = commentRow(key, true);
    if (c.version() != input.version()
        || "DELETED".equals(c.status())
        || input.status() == CommentStatus.APPROVED
            && (!Set.of("PENDING", "REJECTED").contains(c.status())
                || !"PUBLISHED".equals(p.status()))) throw conflict();
    if (input.status() == CommentStatus.APPROVED) {
      if (c.authorId() == actor())
        throw error(HttpStatus.FORBIDDEN, "SELF_REVIEW_FORBIDDEN", "不能审核自己的评论");
      accounts.lockActiveUser("" + c.authorId());
      text(c.body(), config.maxCommentLength());
    }
    interactions.commentStatus(c.id(), input.status().name(), clock.instant());
    moderation("POST_COMMENT", c.id(), c.status(), input.status().name(), input.reason());
    return view(commentRow(key, true));
  }

  public Like liked(String key) {
    publicPost(key);
    return new Like(key, interactions.liked(id(key), actor()), interactions.likeCount(id(key)));
  }

  public Like like(String key, boolean liked) {
    var p = lockPost(key, actor(), true);
    boolean old = interactions.liked(p.id(), actor());
    if (liked && !old) interactions.like(p.id(), actor(), clock.instant());
    else if (!liked && old) interactions.unlike(p.id(), actor());
    if (old != liked) record(liked ? "POST_LIKED" : "POST_UNLIKED", "POST", p.id());
    return new Like(key, liked, interactions.likeCount(p.id()));
  }

  public Result<Report> report(String key, ReportInput input) {
    var p = lockPost(key, actor(), true);
    var old = db.ownReport(p.id(), actor());
    if (old != null) return new Result<>(false, view(old));
    long id = db.report(p.id(), actor(), input.reason().strip(), clock.instant());
    record("POST_REPORTED", "POST_REPORT", id);
    return new Result<>(true, view(db.report(id, true)));
  }

  public Report report(String key) {
    var r = db.report(id(key), false);
    if (r == null) throw missing();
    if (r.reporterId() != actor()) CurrentActor.require("community:moderate");
    return view(r);
  }

  public PageResponse<Report> reports(ModerationQuery q) {
    CurrentActor.require("community:moderate");
    return new PageResponse<>(
        db.reports(q.getStatus(), q.getSize(), offset(q)).stream().map(this::view).toList(),
        db.reportCount(q.getStatus()),
        q.getPage(),
        q.getSize());
  }

  public Report resolve(String key, Resolution input) {
    CurrentActor.require("community:moderate");
    accounts.lockActiveUser(CurrentActor.id());
    var r = db.report(id(key), true);
    if (r == null) throw missing();
    if (r.version() != input.version() || !"OPEN".equals(r.status())) throw conflict();
    db.resolve(r.id(), input.status().name(), actor(), input.reason().strip(), clock.instant());
    record("REPORT_RESOLVED", "POST_REPORT", r.id());
    return view(db.report(r.id(), true));
  }
}
