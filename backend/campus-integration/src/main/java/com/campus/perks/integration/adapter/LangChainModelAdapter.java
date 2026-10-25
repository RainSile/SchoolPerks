package com.campus.perks.integration.adapter;

import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.config.IntegrationProperties;
import com.campus.perks.integration.port.AiModelPort;
import dev.langchain4j.agent.tool.*;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.request.*;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.openai.*;
import java.net.URI;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "campus.integrations.ai.enabled", havingValue = "true")
public class LangChainModelAdapter implements AiModelPort {
  private final OpenAiChatModel chat;
  private final OpenAiStreamingChatModel streaming;
  private final OpenAiEmbeddingModel embeddings;
  private final IntegrationProperties.Ai p;

  public LangChainModelAdapter(IntegrationProperties config) {
    p = config.ai();
    validate(p.baseUrl());
    validate(p.embeddingBaseUrl());
    if (p.apiKey() == null
        || p.apiKey().isBlank()
        || p.embeddingApiKey() == null
        || p.embeddingApiKey().isBlank()
        || p.chatModel().isBlank()
        || p.embeddingModel().isBlank()
        || p.embeddingDimensions() < 1
        || p.maxTokens() < 1
        || p.maxEmbeddingChars() < 1)
      throw new IllegalStateException("AI configuration incomplete");
    chat =
        OpenAiChatModel.builder()
            .baseUrl(p.baseUrl())
            .apiKey(p.apiKey())
            .modelName(p.chatModel())
            .timeout(p.timeout())
            .maxRetries(0)
            .maxTokens(p.maxTokens())
            .logRequests(false)
            .logResponses(false)
            .customParameters(Map.of("thinking", Map.of("type", "disabled")))
            .build();
    streaming =
        OpenAiStreamingChatModel.builder()
            .baseUrl(p.baseUrl())
            .apiKey(p.apiKey())
            .modelName(p.chatModel())
            .timeout(p.timeout())
            .maxTokens(p.maxTokens())
            .logRequests(false)
            .logResponses(false)
            .customParameters(Map.of("thinking", Map.of("type", "disabled")))
            .build();
    embeddings =
        OpenAiEmbeddingModel.builder()
            .baseUrl(p.embeddingBaseUrl())
            .apiKey(p.embeddingApiKey())
            .modelName(p.embeddingModel())
            .dimensions(p.embeddingDimensions())
            .encodingFormat("float")
            .timeout(p.timeout())
            .maxRetries(0)
            .logRequests(false)
            .logResponses(false)
            .build();
  }

  private static void validate(String url) {
    var u = URI.create(url);
    if (!"https".equals(u.getScheme())
        || u.getHost() == null
        || u.getUserInfo() != null
        || u.getQuery() != null
        || u.getFragment() != null)
      throw new IllegalStateException("AI provider requires a valid HTTPS base URL");
  }

  private ToolExecutionRequest call(ToolCall c) {
    return ToolExecutionRequest.builder()
        .id(c.id())
        .name(c.name())
        .arguments(c.arguments())
        .build();
  }

  public Response chat(List<Message> input, List<Tool> tools) {
    try {
      return response(chat.chat(request(input, tools)));
    } catch (Exception e) {
      throw providerError(e);
    }
  }

  private ChatRequest request(List<Message> input, List<Tool> tools) {
    List<ChatMessage> messages = new ArrayList<>();
    for (var m : input)
      switch (m.role()) {
        case "system" -> messages.add(SystemMessage.from(m.text()));
        case "user" -> messages.add(UserMessage.from(m.text()));
        case "assistant" ->
            messages.add(
                m.calls().isEmpty()
                    ? AiMessage.from(m.text())
                    : AiMessage.from(m.text(), m.calls().stream().map(this::call).toList()));
        case "tool" ->
            messages.add(ToolExecutionResultMessage.from(m.callId(), m.toolName(), m.text()));
        default -> throw new IllegalArgumentException("Invalid message role");
      }
    var specs =
        tools.stream()
            .map(
                t -> {
                  var schema = JsonObjectSchema.builder().additionalProperties(false);
                  t.parameters().forEach(schema::addStringProperty);
                  schema.required(t.required());
                  return ToolSpecification.builder()
                      .name(t.name())
                      .description(t.description())
                      .parameters(schema.build())
                      .build();
                })
            .toList();
    return ChatRequest.builder().messages(messages).toolSpecifications(specs).build();
  }

  private Response response(dev.langchain4j.model.chat.response.ChatResponse result) {
    var reply = result.aiMessage();
    return new Response(
        Objects.toString(reply.text(), ""),
        Optional.ofNullable(reply.toolExecutionRequests()).orElse(List.of()).stream()
            .map(c -> new ToolCall(c.id(), c.name(), c.arguments()))
            .toList());
  }

  @Override
  public Response stream(
      List<Message> input, List<Tool> tools, java.util.function.Consumer<String> consumer) {
    var chunks = new java.util.concurrent.ArrayBlockingQueue<String>(256);
    var done =
        new java.util.concurrent.CompletableFuture<
            dev.langchain4j.model.chat.response.ChatResponse>();
    var handle =
        new java.util.concurrent.atomic.AtomicReference<
            dev.langchain4j.model.chat.response.StreamingHandle>();
    var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
    try {
      streaming.chat(
          request(input, tools),
          new dev.langchain4j.model.chat.response.StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(
                dev.langchain4j.model.chat.response.PartialResponse part,
                dev.langchain4j.model.chat.response.PartialResponseContext context) {
              handle.set(context.streamingHandle());
              if (cancelled.get() || !chunks.offer(part.text())) {
                context.streamingHandle().cancel();
                done.completeExceptionally(new IllegalStateException("Stream stopped"));
              }
            }

            @Override
            public void onCompleteResponse(
                dev.langchain4j.model.chat.response.ChatResponse result) {
              done.complete(result);
            }

            @Override
            public void onError(Throwable error) {
              done.completeExceptionally(error);
            }
          });
      long deadline = System.nanoTime() + p.timeout().toNanos();
      while (!done.isDone() || !chunks.isEmpty()) {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        if (System.nanoTime() > deadline) throw new java.util.concurrent.TimeoutException();
        var part = chunks.poll(250, java.util.concurrent.TimeUnit.MILLISECONDS);
        consumer.accept(part == null ? "" : part);
      }
      return response(done.get());
    } catch (ApplicationException error) {
      throw error;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new ApplicationException(
          HttpStatus.SERVICE_UNAVAILABLE, "ASSISTANT_CANCELLED", "输出已停止，请查询业务实际结果");
    } catch (Exception error) {
      throw providerError(error);
    } finally {
      cancelled.set(true);
      if (!done.isDone() && handle.get() != null) handle.get().cancel();
    }
  }

  private ApplicationException providerError(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      String message = Objects.toString(cause.getMessage(), "").toLowerCase(Locale.ROOT);
      if (message.contains("insufficient_quota")
          || message.contains("insufficient balance")
          || message.contains("balance is insufficient")
          || message.contains("arrearage"))
        return new ApplicationException(
            HttpStatus.TOO_MANY_REQUESTS, "AI_PROVIDER_QUOTA_EXHAUSTED", "大模型服务余额或额度不足，请补充后重试");
    }
    return new ApplicationException(
        HttpStatus.SERVICE_UNAVAILABLE, "AI_PROVIDER_UNAVAILABLE", "模型服务暂不可用，请稍后重试");
  }

  public float[] embed(String text) {
    try {
      var v =
          embeddings
              .embed(text.substring(0, Math.min(text.length(), p.maxEmbeddingChars())))
              .content()
              .vector();
      if (v.length != p.embeddingDimensions()) throw new IllegalStateException();
      for (float n : v) if (!Float.isFinite(n)) throw new IllegalStateException();
      return v;
    } catch (Exception e) {
      throw new ApplicationException(
          HttpStatus.SERVICE_UNAVAILABLE, "EMBEDDING_UNAVAILABLE", "向量模型暂不可用或维度不匹配");
    }
  }
}
