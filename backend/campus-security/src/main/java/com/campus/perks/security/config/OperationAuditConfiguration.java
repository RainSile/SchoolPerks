package com.campus.perks.security.config;

import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.security.port.OperationAuditPort;
import com.campus.perks.security.service.AuditedOperation;
import java.lang.reflect.Method;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.support.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration(proxyBeanMethods = false)
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@ConditionalOnProperty(
    name = {"campus.auth.enabled", "campus.audit.operations-enabled"},
    havingValue = "true")
public class OperationAuditConfiguration {
  private static AuditedOperation annotation(Method method, Class<?> type) {
    return AnnotatedElementUtils.findMergedAnnotation(
        AopUtils.getMostSpecificMethod(method, type), AuditedOperation.class);
  }

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  static org.springframework.aop.Advisor operationAuditAdvisor(
      ObjectProvider<OperationAuditPort> sinks, ObjectProvider<Clock> clocks) {
    var pointcut =
        new StaticMethodMatcherPointcut() {
          public boolean matches(Method method, Class<?> type) {
            return annotation(method, type) != null;
          }
        };
    MethodInterceptor interceptor =
        invocation -> {
          var a = annotation(invocation.getMethod(), invocation.getThis().getClass());
          var auth = SecurityContextHolder.getContext().getAuthentication();
          String actor =
              auth != null && auth.isAuthenticated() && auth.getName().matches("[1-9][0-9]{0,17}")
                  ? auth.getName()
                  : null;
          String resource = "COLLECTION";
          if (a.resourceArgument() >= 0
              && a.resourceArgument() < invocation.getArguments().length) {
            var value = invocation.getArguments()[a.resourceArgument()];
            if (value instanceof String id && id.matches("[A-Za-z0-9:_-]{1,64}")) resource = id;
          }
          String outcome = "SUCCESS", code = null;
          int status = 200;
          long started = System.nanoTime();
          try {
            Object result = invocation.proceed();
            if (result instanceof ResponseEntity<?> response)
              status = response.getStatusCode().value();
            if (status >= 400) outcome = status == 401 || status == 403 ? "DENIED" : "FAILURE";
            return result;
          } catch (Throwable failure) {
            if (failure instanceof ApplicationException application) {
              status = application.status().value();
              code = application.code();
            } else if (failure instanceof AccessDeniedException) {
              status = 403;
              code = "FORBIDDEN";
            } else {
              status = 500;
              code = "INTERNAL_ERROR";
            }
            outcome = status == 401 || status == 403 ? "DENIED" : "FAILURE";
            throw failure;
          } finally {
            if (actor != null) {
              String id = UUID.randomUUID().toString();
              try {
                var sink = sinks.getIfAvailable();
                if (sink != null)
                  sink.record(
                      new OperationAuditPort.Event(
                          id,
                          actor,
                          a.action(),
                          a.resourceType(),
                          resource,
                          outcome,
                          status,
                          code != null && code.matches("[A-Z0-9_]{1,64}") ? code : null,
                          Math.max(0, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)),
                          clocks.getObject().instant()));
              } catch (RuntimeException unavailable) {
                org.slf4j.LoggerFactory.getLogger(OperationAuditConfiguration.class)
                    .warn("Operational audit could not be persisted; operationId={}", id);
              }
            }
          }
        };
    var advisor = new DefaultPointcutAdvisor(pointcut, interceptor);
    advisor.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
    return advisor;
  }
}
