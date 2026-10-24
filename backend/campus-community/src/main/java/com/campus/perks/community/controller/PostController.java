package com.campus.perks.community.controller;

import com.campus.perks.common.api.*;
import com.campus.perks.community.dto.*;
import com.campus.perks.community.dto.CommunityRequests.*;
import com.campus.perks.community.service.PostService;
import com.campus.perks.community.vo.CommunityViews.*;
import com.campus.perks.community.vo.PostSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "种草社区")
public class PostController {
  private final PostService service;

  public PostController(PostService service) {
    this.service = service;
  }

  private <T> ResponseEntity<T> created(T value, String path) {
    return ResponseEntity.created(URI.create("/api/v1" + path))
        .header("Cache-Control", "no-store")
        .body(value);
  }

  @GetMapping("/posts")
  @Operation(summary = "已审核且公开的帖子分页")
  public PageResponse<PostSummary> query(@Valid @ParameterObject PostQuery query) {
    return service.query(query);
  }

  @GetMapping("/posts/{id}")
  @Operation(summary = "公开帖子详情；隐藏、未审核或作者停用时404")
  public PostSummary detail(@PathVariable String id) {
    return service.detail(id);
  }

  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/posts")
  @Operation(summary = "创建本人图文草稿；图片必须为本人READY社区图片")
  public ResponseEntity<PostSummary> create(@Valid @RequestBody CreatePost input) {
    var p = service.create(input);
    return created(p, "/users/me/posts/" + p.id());
  }

  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/users/me/posts")
  @Operation(summary = "本人帖子草稿及审核状态分页")
  public PageResponse<PostSummary> own(@Valid @ParameterObject PageQuery q) {
    return service.own(q);
  }

  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/users/me/posts/{id}")
  @Operation(summary = "本人帖子详情，包括未公开内容")
  public PostSummary managed(@PathVariable String id) {
    return service.managed(id);
  }

  @SecurityRequirement(name = "bearerAuth")
  @PatchMapping("/posts/{id}")
  @Operation(summary = "修改本人帖子内容并退回草稿；送审中不可改，旧版本409")
  public PostSummary update(@PathVariable String id, @Valid @RequestBody UpdatePost input) {
    return service.update(id, input);
  }

  @SecurityRequirement(name = "bearerAuth")
  @DeleteMapping("/posts/{id}")
  @Operation(summary = "本人软删除帖子；同时撤回待审核请求，重复删除幂等")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/posts/{id}/review-requests")
  @Operation(summary = "本人提交不可变内容审核快照")
  public ResponseEntity<Review> submit(@PathVariable String id, @Valid @RequestBody Version input) {
    var r = service.submit(id, input);
    return created(r, "/post-review-requests/" + r.id());
  }

  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/post-review-requests/{id}")
  @Operation(summary = "作者或审核员查看帖子审核快照/原因")
  public Review review(@PathVariable String id) {
    return service.review(id);
  }

  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/admin/post-review-requests")
  @Operation(summary = "community:moderate 帖子内容审核队列")
  public PageResponse<Review> reviews(@Valid @ParameterObject ModerationQuery q) {
    return service.reviews(q);
  }

  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/admin/post-review-requests/{id}/reviews")
  @Operation(summary = "审核通过公开/驳回；禁止自审和关联门店自审")
  public ResponseEntity<Review> decide(
      @PathVariable String id, @Valid @RequestBody Decision input) {
    return created(service.decide(id, input), "/post-review-requests/" + id);
  }

  @SecurityRequirement(name = "bearerAuth")
  @PatchMapping("/admin/posts/{id}")
  @Operation(summary = "community:moderate 隐藏违规帖子，必须说明原因")
  public PostSummary hide(@PathVariable String id, @Valid @RequestBody HideInput input) {
    return service.hide(id, input);
  }

  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/posts/{id}/image-download-tickets")
  @Operation(summary = "公开帖登录用户/作者/审核员获取该帖图片短时链接")
  public ResponseEntity<java.util.List<ImageTicket>> images(@PathVariable String id) {
    return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.images(id));
  }

  @GetMapping("/posts/{id}/comments")
  @Operation(summary = "公开帖已审核评论分页，不展示待审/隐藏评论")
  public PageResponse<Comment> comments(
      @PathVariable String id, @Valid @ParameterObject PageQuery q) {
    return service.comments(id, q);
  }

  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/posts/{id}/comments")
  @Operation(summary = "创建本人待审评论；Idempotency-Key必填，重复同内容200")
  public ResponseEntity<Comment> comment(
      @PathVariable String id,
      @Valid @RequestBody CommentInput input,
      @RequestHeader("Idempotency-Key") String key) {
    var r = service.comment(id, input, key);
    return ResponseEntity.status(r.created() ? 201 : 200)
        .location(URI.create("/api/v1/post-comments/" + r.value().id()))
        .body(r.value());
  }

  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/post-comments/{id}")
  @Operation(summary = "本人/审核员查看评论，其他登录用户仅能读公开帖已审评论")
  public Comment comment(@PathVariable String id) {
    return service.comment(id);
  }

  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/users/me/post-comments")
  @Operation(summary = "本人评论与审核状态分页")
  public PageResponse<Comment> ownComments(@Valid @ParameterObject PageQuery q) {
    return service.ownComments(q);
  }

  @SecurityRequirement(name = "bearerAuth")
  @DeleteMapping("/post-comments/{id}")
  @Operation(summary = "本人删除评论，重复删除幂等")
  public ResponseEntity<Void> deleteComment(@PathVariable String id) {
    service.deleteComment(id);
    return ResponseEntity.noContent().build();
  }

  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/admin/post-comments")
  @Operation(summary = "community:moderate 评论审核队列")
  public PageResponse<Comment> commentQueue(@Valid @ParameterObject ModerationQuery q) {
    return service.commentQueue(q);
  }

  @SecurityRequirement(name = "bearerAuth")
  @PatchMapping("/admin/post-comments/{id}")
  @Operation(summary = "审核/隐藏评论；拒绝旧版本、自审通过和删除后恢复")
  public Comment moderateComment(
      @PathVariable String id, @Valid @RequestBody CommentDecision input) {
    return service.moderateComment(id, input);
  }

  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/posts/{id}/likes/me")
  @Operation(summary = "本人点赞状态与公开计数")
  public Like liked(@PathVariable String id) {
    return service.liked(id);
  }

  @SecurityRequirement(name = "bearerAuth")
  @PutMapping("/posts/{id}/likes/me")
  @Operation(summary = "本人点赞，联合唯一键保证重复请求幂等")
  public Like like(@PathVariable String id) {
    return service.like(id, true);
  }

  @SecurityRequirement(name = "bearerAuth")
  @DeleteMapping("/posts/{id}/likes/me")
  @Operation(summary = "取消本人点赞，重复请求幂等")
  public Like unlike(@PathVariable String id) {
    return service.like(id, false);
  }

  @SecurityRequirement(name = "bearerAuth")
  @PostMapping("/posts/{id}/reports")
  @Operation(summary = "举报公开帖子，每人每帖一份举报资源，重复200")
  public ResponseEntity<Report> report(
      @PathVariable String id, @Valid @RequestBody ReportInput input) {
    var r = service.report(id, input);
    return ResponseEntity.status(r.created() ? 201 : 200)
        .location(URI.create("/api/v1/post-reports/" + r.value().id()))
        .body(r.value());
  }

  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/post-reports/{id}")
  @Operation(summary = "仅举报人或审核员可查看举报及处理结果")
  public Report report(@PathVariable String id) {
    return service.report(id);
  }

  @SecurityRequirement(name = "bearerAuth")
  @GetMapping("/admin/post-reports")
  @Operation(summary = "community:moderate 举报处理队列")
  public PageResponse<Report> reports(@Valid @ParameterObject ModerationQuery q) {
    return service.reports(q);
  }

  @SecurityRequirement(name = "bearerAuth")
  @PatchMapping("/admin/post-reports/{id}")
  @Operation(summary = "处理举报并留存结论；隐藏内容需另行明确操作")
  public Report resolve(@PathVariable String id, @Valid @RequestBody Resolution input) {
    return service.resolve(id, input);
  }
}
