package com.campus.perks.assistant.serviceImpl;

import com.fasterxml.jackson.databind.*;
import io.swagger.v3.oas.annotations.Operation;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Catalogue comes from registered business controllers, not model-generated paths or URLs. */
@Component
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "true")
public class AssistantApiCatalog {
  public record Api(
      String id,
      String method,
      String path,
      String summary,
      String portal,
      String permission,
      Set<String> pathParameters,
      Map<String, Object> query,
      Map<String, Object> body,
      boolean mutation) {}

  private final ObjectProvider<RequestMappingHandlerMapping> mappings;
  private final ObjectMapper json;
  private volatile List<Api> cache;

  public AssistantApiCatalog(
      @Qualifier("requestMappingHandlerMapping")
          ObjectProvider<RequestMappingHandlerMapping> mappings,
      ObjectMapper json) {
    this.mappings = mappings;
    this.json = json;
  }

  public List<Api> all() {
    if (cache != null) return cache;
    synchronized (this) {
      if (cache != null) return cache;
      List<Api> values = new ArrayList<>();
      mappings
          .getObject()
          .getHandlerMethods()
          .forEach(
              (mapping, handler) -> {
                String type = handler.getBeanType().getName();
                if (!type.startsWith("com.campus.perks.") || type.endsWith("AssistantController"))
                  return;
                Operation operation = handler.getMethodAnnotation(Operation.class);
                if (operation == null) return;
                for (String path : mapping.getPatternValues())
                  for (var method : mapping.getMethodsCondition().getMethods()) {
                    if (!path.startsWith("/api/v1/")
                        || path.startsWith("/api/v1/assistant/")
                        || path.startsWith("/api/v1/auth/")
                        || path.contains("/maps/_AMapService")
                        || path.contains("payment-notifications")
                        || path.contains("notification-stream")
                        || type.endsWith("AuthenticationController")
                            && !path.equals("/api/v1/schools")) continue;
                    Set<String> parameters = new TreeSet<>();
                    var matcher = Pattern.compile("\\{([^}]+)}").matcher(path);
                    while (matcher.find()) parameters.add(matcher.group(1));
                    Map<String, Object> query = new TreeMap<>(), body = new LinkedHashMap<>();
                    for (var parameter : handler.getMethodParameters()) {
                      if (parameter.hasParameterAnnotation(RequestBody.class))
                        body.putAll(
                            schema(json.constructType(parameter.getGenericParameterType()), 0));
                      else if (parameter.hasParameterAnnotation(RequestParam.class)) {
                        var annotation = parameter.getParameterAnnotation(RequestParam.class);
                        String name =
                            !annotation.name().isBlank()
                                ? annotation.name()
                                : !annotation.value().isBlank()
                                    ? annotation.value()
                                    : parameter.getParameterName();
                        if (name != null)
                          query.put(
                              name,
                              schema(json.constructType(parameter.getGenericParameterType()), 0));
                      } else if (parameter.hasParameterAnnotation(
                          org.springdoc.core.annotations.ParameterObject.class)) {
                        query.putAll(
                            properties(json.constructType(parameter.getGenericParameterType()), 0));
                      }
                    }
                    String verb = method.name();
                    if (AssistantApiPolicy.portal(path, verb).equals("UNCLASSIFIED")) continue;
                    String permission = AssistantApiPolicy.permission(path);
                    var guard =
                        handler.getMethodAnnotation(
                            org.springframework.security.access.prepost.PreAuthorize.class);
                    if (guard == null)
                      guard =
                          org.springframework.core.annotation.AnnotatedElementUtils
                              .findMergedAnnotation(
                                  handler.getBeanType(),
                                  org.springframework.security.access.prepost.PreAuthorize.class);
                    if (guard != null) {
                      var authority =
                          Pattern.compile("^hasAuthority\\('([^']+)'\\)$").matcher(guard.value());
                      if (authority.matches()) permission = authority.group(1);
                    }
                    values.add(
                        new Api(
                            identifier(verb + " " + path),
                            verb,
                            path,
                            operation.summary(),
                            AssistantApiPolicy.portal(path, verb),
                            permission,
                            Set.copyOf(parameters),
                            query,
                            body,
                            !verb.equals("GET")));
                  }
              });
      cache =
          values.stream()
              .sorted(Comparator.comparing(Api::path).thenComparing(Api::method))
              .toList();
      return cache;
    }
  }

  public List<Api> available() {
    return all().stream()
        .filter(a -> AssistantApiPolicy.allowed(a.portal(), a.permission()))
        .toList();
  }

  public Api require(String id) {
    var api =
        all().stream()
            .filter(a -> a.id().equals(id))
            .findFirst()
            .orElseThrow(
                () ->
                    new com.campus.perks.common.exception.ApplicationException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "TOOL_NOT_ALLOWED",
                        "未知业务接口"));
    if (!AssistantApiPolicy.allowed(api.portal(), api.permission()))
      throw new com.campus.perks.common.exception.ApplicationException(
          org.springframework.http.HttpStatus.FORBIDDEN, "TOOL_NOT_ALLOWED", "当前账号不能调用此业务接口");
    return api;
  }

  private Map<String, Object> properties(JavaType type, int depth) {
    Map<String, Object> result = new TreeMap<>();
    for (var property : json.getSerializationConfig().introspect(type).findProperties())
      result.put(property.getName(), schema(property.getPrimaryType(), depth + 1));
    return result;
  }

  private Map<String, Object> schema(JavaType type, int depth) {
    if (type == null || depth > 4) return Map.of("type", "object");
    Class<?> raw = type.getRawClass();
    if (raw.isEnum())
      return Map.of(
          "type",
          "string",
          "enum",
          Arrays.stream(raw.getEnumConstants()).map(Object::toString).toList());
    if (raw == String.class || raw.getName().startsWith("java.time."))
      return Map.of("type", "string");
    if (raw == boolean.class || raw == Boolean.class) return Map.of("type", "boolean");
    if (raw.isPrimitive() || Number.class.isAssignableFrom(raw)) return Map.of("type", "number");
    if (type.isCollectionLikeType() || type.isArrayType())
      return Map.of("type", "array", "items", schema(type.getContentType(), depth + 1));
    if (type.isMapLikeType() || JsonNode.class.isAssignableFrom(raw))
      return Map.of("type", "object");
    return Map.of(
        "type", "object", "properties", properties(type, depth), "additionalProperties", false);
  }

  private static String identifier(String value) {
    try {
      return "api_"
          + HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(value.getBytes(StandardCharsets.UTF_8)))
              .substring(0, 24);
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }
}
