package com.campus.perks.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Actual registered business operations, not a hand-maintained estimate. No business calls. */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("skeleton")
@EnabledIfEnvironmentVariable(named = "CAMPUS_BUSINESS_EVIDENCE_DIR", matches = ".+")
class BusinessEndpointInventoryTest {
  @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mapping;
  @Autowired ObjectMapper json;
  @Autowired MockMvc mvc;

  @Test
  void inventoriesEveryRegisteredBusinessOperationAndItsDocumentation() throws Exception {
    var operations = new ArrayList<Map<String, String>>();
    mapping.getHandlerMethods().forEach((info, handler) -> {
      if (!handler.getBeanType().getName().startsWith("com.campus.perks.")) return;
      boolean excluded = handler.getBeanType().getName().contains(".assistant.");
      for (String path : info.getPatternValues()) {
        for (var method : info.getMethodsCondition().getMethods()) {
          Operation operation = handler.getMethodAnnotation(Operation.class);
          assertThat(operation).as(method + " " + path + " OpenAPI operation").isNotNull();
          assertThat(operation.summary()).as(method + " " + path + " summary").isNotBlank();
          Map<String, String> row = new LinkedHashMap<>();
          row.put("method", method.name());
          row.put("path", path);
          row.put("handler", handler.getBeanType().getName() + "#" + handler.getMethod().getName());
          row.put("summary", operation.summary());
          row.put("scope", excluded ? "EXCLUDED_AI" : "BUSINESS");
          operations.add(row);
        }
      }
    });
    operations.sort(Comparator.comparing(row -> row.get("path") + " " + row.get("method")));
    assertThat(operations).isNotEmpty();
    Path directory = Path.of(System.getenv("CAMPUS_BUSINESS_EVIDENCE_DIR"));
    Files.createDirectories(directory);
    json.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("api-inventory.json").toFile(), operations);
  }

  @Test
  void checksAnonymousDenialForEveryBearerDocumentedBusinessOperation() throws Exception {
    var results = new ArrayList<Map<String, Object>>();
    var failures = new ArrayList<String>();
    for (var entry : mapping.getHandlerMethods().entrySet()) {
      var handler = entry.getValue();
      if (!handler.getBeanType().getName().startsWith("com.campus.perks.")
          || handler.getBeanType().getName().contains(".assistant.")) continue;
      boolean secured = java.util.stream.Stream.concat(
          java.util.Arrays.stream(handler.getBeanType().getAnnotationsByType(SecurityRequirement.class)),
          java.util.Arrays.stream(handler.getMethod().getAnnotationsByType(SecurityRequirement.class)))
          .anyMatch(annotation -> annotation.name().equals("bearerAuth"));
      if (!secured) continue;
      for (String path : entry.getKey().getPatternValues()) {
        for (var method : entry.getKey().getMethodsCondition().getMethods()) {
          String actualPath = path.replaceAll("\\{[^}]+}", "1");
          int status = mvc.perform(request(HttpMethod.valueOf(method.name()), actualPath)
              .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus();
          results.add(Map.of("method", method.name(), "path", path, "status", status, "expected", 401));
          if (status != 401) failures.add(method + " " + path + " expected 401, actual " + status);
        }
      }
    }
    Path directory = Path.of(System.getenv("CAMPUS_BUSINESS_EVIDENCE_DIR"));
    Files.createDirectories(directory);
    json.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("anonymous-sweep.json").toFile(), results);
    assertThat(results).isNotEmpty();
    assertThat(failures).as("Anonymous access to documented private business operations").isEmpty();
  }
}
