package com.campus.perks.integration.port;

import java.util.List;
import java.util.Map;

public interface AiModelPort {
  record ToolCall(String id, String name, String arguments) {}

  record Message(String role, String text, List<ToolCall> calls, String callId, String toolName) {
    public static Message text(String role, String text) {
      return new Message(role, text, List.of(), null, null);
    }
  }

  record Tool(
      String name, String description, Map<String, String> parameters, List<String> required) {}

  record Response(String text, List<ToolCall> calls) {}

  Response chat(List<Message> messages, List<Tool> tools);

  float[] embed(String text);
}
