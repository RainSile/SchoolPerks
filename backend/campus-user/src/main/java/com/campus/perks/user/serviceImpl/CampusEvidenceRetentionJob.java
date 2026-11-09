package com.campus.perks.user.serviceImpl;
import com.campus.perks.user.config.CampusEvidenceProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component @ConditionalOnProperty(name="campus.auth.enabled",havingValue="true")
public class CampusEvidenceRetentionJob {
  private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(CampusEvidenceRetentionJob.class);
  private final CampusEvidenceRetentionService service;private final CampusEvidenceProperties config;
  public CampusEvidenceRetentionJob(CampusEvidenceRetentionService service,CampusEvidenceProperties config){this.service=service;this.config=config;}
  @Scheduled(fixedDelayString="${campus.campus-evidence.cleanup-interval-ms:3600000}",initialDelayString="${campus.campus-evidence.cleanup-initial-delay-ms:60000}")
  public void tick(){
    if(!config.cleanupEnabled())return;
    for(String id:service.due())try{service.purge(id);}catch(RuntimeException error){log.warn("Student-card retention cleanup will retry ({})",error.getClass().getSimpleName());}
  }
}
