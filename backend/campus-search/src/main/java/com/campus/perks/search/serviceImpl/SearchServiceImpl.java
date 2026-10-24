package com.campus.perks.search.serviceImpl;

import com.campus.perks.common.api.PageResponse;
import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.port.*;
import com.campus.perks.search.dto.SearchQuery;
import com.campus.perks.search.entity.SearchSource;
import com.campus.perks.search.mapper.SearchMapper;
import com.campus.perks.search.service.SearchProperties;
import com.campus.perks.search.service.SearchService;
import com.campus.perks.search.vo.SearchHit;
import com.campus.perks.search.vo.SearchViews.*;
import com.campus.perks.security.service.CurrentActor;
import java.time.Clock;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class SearchServiceImpl implements SearchService {
  private final SearchMapper db;
  private final SearchProperties config;
  private final SearchEnginePort engine;
  private final SearchProjectionService projection;
  private final AuditPort audit;
  private final AccountDirectoryPort accounts;
  private final Clock clock;

  public SearchServiceImpl(
      SearchMapper db,
      SearchProperties config,
      SearchEnginePort engine,
      SearchProjectionService projection,
      AuditPort audit,
      AccountDirectoryPort accounts,
      Clock clock) {
    this.db = db;
    this.config = config;
    this.engine = engine;
    this.projection = projection;
    this.audit = audit;
    this.accounts = accounts;
    this.clock = clock;
  }

  private ApplicationException error(HttpStatus status, String code, String message) {
    return new ApplicationException(status, code, message);
  }

  private List<SearchHit.Segment> segments(String text, String keyword) {
    var result = new ArrayList<SearchHit.Segment>();
    var matcher =
        Pattern.compile(Pattern.quote(keyword), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
            .matcher(text);
    int from = 0;
    while (matcher.find()) {
      if (matcher.start() > from)
        result.add(new SearchHit.Segment(text.substring(from, matcher.start()), false));
      result.add(new SearchHit.Segment(matcher.group(), true));
      from = matcher.end();
    }
    if (from < text.length()) result.add(new SearchHit.Segment(text.substring(from), false));
    return result;
  }

  private SearchHit hit(SearchSource s, String keyword) {
    var matcher =
        Pattern.compile(Pattern.quote(keyword), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
            .matcher(s.body());
    int from = matcher.find() ? Math.max(0, matcher.start() - config.excerptLength() / 4) : 0;
    int to = Math.min(s.body().length(), from + config.excerptLength());
    if (from > 0 && Character.isLowSurrogate(s.body().charAt(from))) from--;
    if (to < s.body().length() && to > 0 && Character.isHighSurrogate(s.body().charAt(to - 1)))
      to--;
    String excerpt = s.body().substring(from, to);
    return new SearchHit(
        s.id(),
        s.type(),
        s.schoolId(),
        s.merchantId(),
        s.title(),
        excerpt,
        segments(s.title(), keyword),
        segments(excerpt, keyword));
  }

  public Results query(SearchQuery q) {
    long offset = (long) (q.getPage() - 1) * q.getSize();
    if (offset + q.getSize() > config.maxWindow())
      throw error(
          HttpStatus.UNPROCESSABLE_ENTITY, "SEARCH_WINDOW_EXCEEDED", "搜索页数超过配置窗口，请缩小关键词或筛选范围");
    String keyword = q.getKeyword().strip();
    if ("MYSQL".equals(config.backend())) {
      long count = db.count(keyword, q.getResourceType(), q.getSchoolId());
      return new Results(
          "MYSQL",
          db.query(keyword, q.getResourceType(), q.getSchoolId(), q.getSize(), offset).stream()
              .map(s -> hit(s, keyword))
              .toList(),
          Math.min(count, config.maxWindow()),
          q.getPage(),
          q.getSize(),
          count > config.maxWindow());
    }
    var candidates =
        engine.candidates(keyword, q.getResourceType(), q.getSchoolId(), config.maxWindow());
    var canonical = new HashMap<String, SearchSource>();
    for (var source : db.sources(candidates))
      canonical.put(source.type() + ":" + source.id(), source);
    var valid = new ArrayList<SearchSource>();
    var seen = new HashSet<String>();
    for (var key : candidates) {
      String id = key.type() + ":" + key.id();
      var source = canonical.get(id);
      if (source != null
          && seen.add(id)
          && key.sourceHash().equals(projection.hash(source))
          && (q.getResourceType() == null || q.getResourceType().equals(source.type()))
          && (q.getSchoolId() == null || q.getSchoolId().equals(source.schoolId())))
        valid.add(source);
    }
    return new Results(
        "ELASTICSEARCH",
        valid.stream().skip(offset).limit(q.getSize()).map(s -> hit(s, keyword)).toList(),
        valid.size(),
        q.getPage(),
        q.getSize(),
        candidates.size() >= config.maxWindow());
  }

  @Transactional
  public Rebuild createRebuild() {
    CurrentActor.require("search:manage");
    accounts.lockActiveUser(CurrentActor.id());
    if (db.activeRebuild() != null)
      throw error(HttpStatus.CONFLICT, "SEARCH_REBUILD_ACTIVE", "已有进行中的索引重建");
    String id = UUID.randomUUID().toString();
    try {
      db.rebuild(id, Long.parseLong(CurrentActor.id()), clock.instant());
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      throw error(HttpStatus.CONFLICT, "SEARCH_REBUILD_ACTIVE", "已有进行中的索引重建");
    }
    audit.record(
        CurrentActor.id(),
        "SEARCH_REBUILD_REQUESTED",
        "SEARCH_REBUILD",
        id,
        Map.of("backend", config.backend()));
    return db.rebuild(id, false);
  }

  public Rebuild rebuild(String id) {
    CurrentActor.require("search:manage");
    var r = db.rebuild(id, false);
    if (r == null) throw error(HttpStatus.NOT_FOUND, "SEARCH_REBUILD_NOT_FOUND", "索引重建任务不存在");
    return r;
  }

  public PageResponse<Rebuild> rebuilds(com.campus.perks.common.api.PageQuery q) {
    CurrentActor.require("search:manage");
    return new PageResponse<>(
        db.rebuilds(q.getSize(), (long) (q.getPage() - 1) * q.getSize()),
        db.rebuildCount(),
        q.getPage(),
        q.getSize());
  }

  @Transactional
  public Rebuild retry(String id) {
    CurrentActor.require("search:manage");
    accounts.lockActiveUser(CurrentActor.id());
    var r = db.rebuild(id, true);
    if (r == null) throw error(HttpStatus.NOT_FOUND, "SEARCH_REBUILD_NOT_FOUND", "索引重建任务不存在");
    if (!"FAILED".equals(r.status()) || db.activeRebuild() != null)
      throw error(HttpStatus.CONFLICT, "SEARCH_REBUILD_NOT_RETRYABLE", "任务未失败或已有进行中的重建");
    try {
      db.retry(id);
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      throw error(HttpStatus.CONFLICT, "SEARCH_REBUILD_ACTIVE", "已有进行中的重建");
    }
    audit.record(CurrentActor.id(), "SEARCH_REBUILD_RETRIED", "SEARCH_REBUILD", id, Map.of());
    return db.rebuild(id, true);
  }
}
