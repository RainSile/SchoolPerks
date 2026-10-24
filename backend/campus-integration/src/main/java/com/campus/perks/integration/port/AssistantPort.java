package com.campus.perks.integration.port;

/** 不将 LangChain4j 类型暴露给业务；调用者负责会话所有权验证。 */
public interface AssistantPort {
  String reply(String userId, String conversationId, String message);
}
