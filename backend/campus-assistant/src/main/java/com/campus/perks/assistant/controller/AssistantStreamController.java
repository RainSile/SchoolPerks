package com.campus.perks.assistant.controller;

import com.campus.perks.assistant.dto.SendMessageRequest;
import com.campus.perks.assistant.service.*;
import com.campus.perks.common.exception.ApplicationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.*;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/assistant")
@Tag(name = "悬浮智能助手")
@SecurityRequirement(name = "bearerAuth")
@EnableConfigurationProperties(AssistantStreamProperties.class)
public class AssistantStreamController {
  private final ObjectProvider<AssistantService> services;
  private final AssistantProperties properties;
  private final ThreadPoolExecutor workers;
  private final int maxAnswerChars;

  public AssistantStreamController(
      ObjectProvider<AssistantService> services,
      AssistantProperties properties,
      AssistantStreamProperties stream) {
    this.services = services;
    this.properties = properties;
    maxAnswerChars = stream.maxAnswerChars();
    workers =
        new ThreadPoolExecutor(
            stream.workers(),
            stream.workers(),
            0,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(stream.queueCapacity()),
            r -> {
              var thread = new Thread(r, "campus-assistant-stream");
              thread.setDaemon(true);
              return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());
  }

  @PreDestroy
  public void close() {
    workers.shutdownNow();
  }

  @PostMapping(
      value = "/conversations/{id}/message-streams",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @Operation(summary = "本人会话真实流式提问；started/round/delta/tool/completed/error事件，显式允许本次写操作")
  public ResponseEntity<SseEmitter> stream(
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody SendMessageRequest request) {
    var service = services.getIfAvailable();
    if (service == null)
      throw new ApplicationException(
          HttpStatus.SERVICE_UNAVAILABLE, "ASSISTANT_UNAVAILABLE", "助手尚未启用");
    service.validateStream(id, key, request);
    var emitter = new SseEmitter(properties.leaseTtl().toMillis());
    var ended = new AtomicBoolean();
    var finished = new AtomicBoolean();
    var action =
        new DelegatingSecurityContextRunnable(
            () -> {
              try {
                var size = new java.util.concurrent.atomic.AtomicInteger();
                service.stream(
                    id,
                    key,
                    request,
                    (name, data) -> {
                      if (ended.get() || Thread.currentThread().isInterrupted())
                        throw new CancellationException();
                      if (name.equals("round")) size.set(0);
                      if (name.equals("delta")
                          && size.addAndGet(((Map<?, ?>) data).get("text").toString().length())
                              > maxAnswerChars)
                        throw new ApplicationException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "ASSISTANT_RESPONSE_TOO_LONG",
                            "输出过长，请缩小问题范围并查询业务结果");
                      try {
                        emitter.send(SseEmitter.event().name(name).data(data));
                      } catch (java.io.IOException disconnected) {
                        ended.set(true);
                        throw new CancellationException();
                      }
                    });
              } catch (Exception error) {
                if (!ended.get())
                  try {
                    var safe =
                        error instanceof ApplicationException a
                            ? a
                            : new ApplicationException(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "ASSISTANT_STREAM_INTERRUPTED",
                                "输出已中断，请查询业务结果后重试");
                    emitter.send(
                        SseEmitter.event()
                            .name("error")
                            .data(
                                Map.of(
                                    "status",
                                    safe.status().value(),
                                    "code",
                                    safe.code(),
                                    "detail",
                                    safe.getMessage())));
                  } catch (java.io.IOException ignored) {
                    ended.set(true);
                  }
              } finally {
                finished.set(true);
                emitter.complete();
              }
            });
    var task = new FutureTask<Void>(action, null);
    Runnable cancel =
        () -> {
          ended.set(true);
          if (!finished.get()) task.cancel(true);
        };
    emitter.onCompletion(cancel);
    emitter.onTimeout(cancel);
    emitter.onError(error -> cancel.run());
    try {
      workers.execute(task);
    } catch (RejectedExecutionException busy) {
      throw new ApplicationException(
          HttpStatus.TOO_MANY_REQUESTS, "ASSISTANT_STREAM_BUSY", "当前提问较多，请稍后重试");
    }
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .header("X-Accel-Buffering", "no")
        .body(emitter);
  }
}
