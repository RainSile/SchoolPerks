package com.campus.perks.boot.config;

import com.campus.perks.common.config.BusinessProperties;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

@Configuration
@EnableConfigurationProperties(BusinessProperties.class)
public class CoreConfiguration {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
