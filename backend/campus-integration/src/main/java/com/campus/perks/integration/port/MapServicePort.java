package com.campus.perks.integration.port;

import java.util.Map;

public interface MapServicePort {
  String jsKey();

  record Reply(String contentType, byte[] body) {}

  Reply proxy(String path, Map<String, String> query);
}
