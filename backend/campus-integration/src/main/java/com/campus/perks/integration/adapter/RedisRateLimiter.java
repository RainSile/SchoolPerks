package com.campus.perks.integration.adapter;

import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.config.InfrastructureProperties;
import com.campus.perks.integration.port.RateLimitPort;
import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "campus.infrastructure.redis.enabled", havingValue = "true")
@EnableConfigurationProperties(InfrastructureProperties.class)
public class RedisRateLimiter implements RateLimitPort, AutoCloseable {
  private final RedisClient client;
  private StatefulRedisConnection<String, String> connection;

  public RedisRateLimiter(InfrastructureProperties p) {
    var uri = RedisURI.create(p.redis().uri());
    uri.setTimeout(p.redis().timeout());
    client = RedisClient.create(uri);
    client.setOptions(
        ClientOptions.builder()
            .autoReconnect(true)
            .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
            .build());
  }

  private synchronized StatefulRedisConnection<String, String> connection() {
    if (connection == null || !connection.isOpen()) connection = client.connect();
    return connection;
  }

  public boolean acquire(String key, int maximum, Duration window) {
    if (!key.matches("[a-zA-Z0-9:_-]{1,160}")
        || maximum < 1
        || window.isNegative()
        || window.isZero()) throw new IllegalArgumentException("Invalid rate limit");
    try {
      Long count =
          connection()
              .sync()
              .eval(
                  "local n=redis.call('INCR',KEYS[1]); if n==1 then"
                      + " redis.call('PEXPIRE',KEYS[1],ARGV[1]); end; return n",
                  ScriptOutputType.INTEGER,
                  new String[] {"campus:limit:" + key},
                  Long.toString(window.toMillis()));
      return count != null && count <= maximum;
    } catch (Exception e) {
      throw new ApplicationException(
          HttpStatus.SERVICE_UNAVAILABLE, "RATE_LIMIT_UNAVAILABLE", "限流服务暂不可用");
    }
  }

  @jakarta.annotation.PreDestroy
  public synchronized void close() {
    if (connection != null) connection.close();
    client.shutdown();
  }
}
