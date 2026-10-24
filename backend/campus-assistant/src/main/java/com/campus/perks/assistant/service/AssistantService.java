package com.campus.perks.assistant.service;

import com.campus.perks.assistant.dto.CreateConversationRequest;
import com.campus.perks.assistant.vo.ConversationView;

public interface AssistantService {
  ConversationView create(CreateConversationRequest request);

  java.util.List<com.campus.perks.assistant.vo.AssistantViews.Message> history(String conversation);

  com.campus.perks.assistant.vo.AssistantViews.Message message(String conversation, String message);

  Result send(
      String conversation, String key, com.campus.perks.assistant.dto.SendMessageRequest request);

  com.campus.perks.assistant.vo.AssistantViews.Capabilities capabilities();

  record Result(com.campus.perks.assistant.vo.AssistantViews.Message value, boolean created) {}
}
