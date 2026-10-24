package com.campus.perks.community.service;

import com.campus.perks.community.entity.CommunityRows.Comment;
import java.time.Instant;
import java.util.List;

/**
 * Interaction storage boundary. A MongoDB implementation must preserve authorization, idempotency
 * and moderation semantics; never dual-write synchronously across stores.
 */
public interface CommunityInteractions {
  Comment comment(long id, boolean lock);

  Comment requestedComment(long post, long actor, String key);

  long addComment(long post, long actor, String key, String body, Instant now);

  List<Comment> comments(long post, int size, long offset);

  long commentCount(long post);

  List<Comment> ownComments(long actor, int size, long offset);

  long ownCommentCount(long actor);

  List<Comment> commentQueue(String status, int size, long offset);

  long commentQueueCount(String status);

  void commentStatus(long id, String status, Instant now);

  long likeCount(long post);

  boolean liked(long post, long actor);

  void like(long post, long actor, Instant now);

  void unlike(long post, long actor);
}
