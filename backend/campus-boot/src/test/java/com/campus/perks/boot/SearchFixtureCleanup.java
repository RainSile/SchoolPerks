package com.campus.perks.boot;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Projection consumers commit independently of test business transactions; remove only these
 * fixture keys.
 */
final class SearchFixtureCleanup {
  static void resources(JdbcTemplate jdbc, String type, Collection<Long> ids) {
    var tx =
        new TransactionTemplate(
            new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    for (long id : ids) {
      String aggregate = "SEARCH:" + type + ":" + id;
      var events =
          jdbc.queryForList(
              "SELECT id FROM campus_outbox_event WHERE aggregate_id=? AND"
                  + " event_type='SEARCH_RESOURCE_CHANGED'",
              String.class,
              aggregate);
      tx.executeWithoutResult(
          s -> {
            for (String event : events)
              jdbc.update(
                  "DELETE FROM campus_consumed_event WHERE consumer_name='campus-search' AND"
                      + " event_id=?",
                  event);
            jdbc.update(
                "DELETE FROM campus_search_document WHERE resource_type=? AND resource_id=?",
                type,
                id);
          });
      jdbc.update(
          "DELETE FROM campus_outbox_event WHERE aggregate_id=? AND"
              + " event_type='SEARCH_RESOURCE_CHANGED'",
          aggregate);
    }
  }
}
