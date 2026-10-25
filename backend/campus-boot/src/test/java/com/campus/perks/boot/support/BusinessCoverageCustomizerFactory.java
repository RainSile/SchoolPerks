package com.campus.perks.boot.support;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;
import org.springframework.http.server.PathContainer;
import org.springframework.core.Ordered;

/** Opt-in test evidence only. Never records bodies, identities, credentials or raw URLs. */
public final class BusinessCoverageCustomizerFactory implements ContextCustomizerFactory {
  @Override
  public ContextCustomizer createContextCustomizer(
      Class<?> testClass, List<ContextConfigurationAttributes> attributes) {
    return System.getenv("CAMPUS_BUSINESS_EVIDENCE_DIR") == null ? null : new AuditCustomizer();
  }

  private record AuditCustomizer() implements ContextCustomizer {
    @Override
    public void customizeContext(
        ConfigurableApplicationContext context, MergedContextConfiguration configuration) {
      RootBeanDefinition definition = new RootBeanDefinition(AuditFilter.class);
      definition.setInstanceSupplier(() -> new AuditFilter(context));
      ((BeanDefinitionRegistry) context.getBeanFactory())
          .registerBeanDefinition("businessTestEvidenceFilter", definition);
    }
  }

  public static final class AuditFilter implements Filter, Ordered {
    private static final Object LOCK = new Object();
    private final ConfigurableApplicationContext context;

    AuditFilter(ConfigurableApplicationContext context) {
      this.context = context;
    }

    @Override
    public int getOrder() {
      return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
      try {
        chain.doFilter(request, response);
      } finally {
        if (request instanceof HttpServletRequest http
            && response instanceof HttpServletResponse result
            && http.getRequestURI().startsWith("/api/v1/")) {
          Object matched = http.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
          String route = matched == null ? declaredRoute(http) : matched.toString();
          if (!route.contains("assistant") && !route.contains("marketing")) {
            String row = http.getMethod() + "\t" + route + "\t" + result.getStatus() + "\t"
                + (matched != null) + "\t" + BusinessTestIdentityExtension.current() + "\n";
            synchronized (LOCK) {
              Path directory = Path.of(System.getenv("CAMPUS_BUSINESS_EVIDENCE_DIR"));
              Files.createDirectories(directory);
              Files.writeString(directory.resolve("backend-requests.tsv"), row,
                  StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
          }
        }
      }
    }

    private String declaredRoute(HttpServletRequest request) {
      if (!context.containsBean("requestMappingHandlerMapping")) return "UNMAPPED";
      var mapping = context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
      var path = PathContainer.parsePath(request.getRequestURI());
      return mapping.getHandlerMethods().keySet().stream()
          .filter(info -> info.getMethodsCondition().getMethods().isEmpty()
              || info.getMethodsCondition().getMethods().stream()
                  .anyMatch(method -> method.name().equals(request.getMethod())))
          .flatMap(info -> info.getPatternValues().stream())
          .filter(pattern -> PathPatternParser.defaultInstance.parse(pattern).matches(path))
          .sorted((a, b) -> Integer.compare(a.indexOf('{') < 0 ? 0 : 1, b.indexOf('{') < 0 ? 0 : 1))
          .findFirst().orElse("UNMAPPED");
    }
  }
}
