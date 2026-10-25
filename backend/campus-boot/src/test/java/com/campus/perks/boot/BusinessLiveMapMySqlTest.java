package com.campus.perks.boot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;

/** Explicit map-only selection; real configured AMap SDK/proxy, synthetic input coordinates. */
@EnabledIfEnvironmentVariables({
  @EnabledIfEnvironmentVariable(named = "CAMPUS_MYSQL_TEST", matches = "true"),
  @EnabledIfEnvironmentVariable(named = "CAMPUS_BUSINESS_EVIDENCE_DIR", matches = ".+"),
  @EnabledIfEnvironmentVariable(named = "CAMPUS_LIVE_BROWSER_TEST", matches = "true"),
  @EnabledIfEnvironmentVariable(named = "CAMPUS_LIVE_MAP_TEST", matches = "true")
})
class BusinessLiveMapMySqlTest extends BusinessLiveJourneyMySqlTest {
  @Test
  void businessRealMapSdkAndProxy() throws Exception {
    runBrowserJourney(true);
  }
}
