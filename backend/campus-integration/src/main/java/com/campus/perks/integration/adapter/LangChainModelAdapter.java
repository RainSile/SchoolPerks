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
      var reply =
          chat.chat(ChatRequest.builder().messages(messages).toolSpecifications(specs).build())
              .aiMessage();
      return new Response(
          Objects.toString(reply.text(), ""),
          Optional.ofNullable(reply.toolExecutionRequests()).orElse(List.of()).stream()
              .map(c -> new ToolCall(c.id(), c.name(), c.arguments()))
              .toList());
    } catch (Exception e) {
      throw new ApplicationException(
          HttpStatus.SERVICE_UNAVAILABLE, "AI_PROVIDER_UNAVAILABLE", "模型服务暂不可用，请稍后重试");
    }
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
