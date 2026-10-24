package com.campus.perks.community.service;

import com.campus.perks.common.api.*;
import com.campus.perks.community.dto.*;
import com.campus.perks.community.dto.CommunityRequests.*;
import com.campus.perks.community.vo.*;
import com.campus.perks.community.vo.CommunityViews.*;

public interface PostService {
  default com.campus.perks.common.exception.ApplicationException unavailable() {
    return new com.campus.perks.common.exception.ApplicationException(
        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
        "COMMUNITY_UNAVAILABLE",
        "社区业务需要启用数据库与认证");
  }

  default PageResponse<PostSummary> query(PostQuery q) {
    throw unavailable();
  }

  default PostSummary detail(String id) {
    throw unavailable();
  }

  default PageResponse<PostSummary> own(PageQuery q) {
    throw unavailable();
  }

  default PostSummary managed(String id) {
    throw unavailable();
  }

  default PostSummary create(CreatePost input) {
    throw unavailable();
  }

  default PostSummary update(String id, UpdatePost input) {
    throw unavailable();
  }

  default void delete(String id) {
    throw unavailable();
  }

  default Review submit(String id, Version input) {
    throw unavailable();
  }

  default Review review(String id) {
    throw unavailable();
  }

  default PageResponse<Review> reviews(ModerationQuery q) {
    throw unavailable();
  }

  default Review decide(String id, Decision input) {
    throw unavailable();
  }

  default PostSummary hide(String id, HideInput input) {
    throw unavailable();
  }

  default java.util.List<ImageTicket> images(String id) {
    throw unavailable();
  }

  default PageResponse<Comment> comments(String id, PageQuery q) {
    throw unavailable();
  }

  default Result<Comment> comment(String id, CommentInput input, String key) {
    throw unavailable();
  }

  default Comment comment(String id) {
    throw unavailable();
  }

  default PageResponse<Comment> ownComments(PageQuery q) {
    throw unavailable();
  }

  default void deleteComment(String id) {
    throw unavailable();
  }

  default PageResponse<Comment> commentQueue(ModerationQuery q) {
    throw unavailable();
  }

  default Comment moderateComment(String id, CommentDecision input) {
    throw unavailable();
  }

  default Like like(String id, boolean liked) {
    throw unavailable();
  }

  default Like liked(String id) {
    throw unavailable();
  }

  default Result<Report> report(String id, ReportInput input) {
    throw unavailable();
  }

  default Report report(String id) {
    throw unavailable();
  }

  default PageResponse<Report> reports(ModerationQuery q) {
    throw unavailable();
  }

  default Report resolve(String id, Resolution input) {
    throw unavailable();
  }
}
