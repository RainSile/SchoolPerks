package com.campus.perks.user.serviceImpl;

import com.campus.perks.integration.port.*;
import com.campus.perks.user.config.CampusEvidenceProperties;
import com.campus.perks.user.mapper.CampusReviewMapper;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @ConditionalOnProperty(name="campus.auth.enabled",havingValue="true")
public class CampusEvidenceRetentionService {
  private final CampusReviewMapper mapper;
  private final FileAccessPort files;
  private final AuditPort audit;
  private final Clock clock;
  private final CampusEvidenceProperties config;
  public CampusEvidenceRetentionService(CampusReviewMapper mapper,FileAccessPort files,AuditPort audit,Clock clock,CampusEvidenceProperties config){
    this.mapper=mapper;this.files=files;this.audit=audit;this.clock=clock;this.config=config;
  }
  public List<String> due(){return mapper.expiredEvidence(clock.instant().minus(config.retention()),config.cleanupBatchSize());}
  @Transactional
  public boolean purge(String id) {
    var row=mapper.lock(id);
    if(row==null || !Set.of("APPROVED","REJECTED").contains(row.status()) || row.reviewedAt()==null
        || row.reviewedAt().isAfter(clock.instant().minus(config.retention()))
        || row.studentCardFileId()==null || row.evidenceDeletedAt()!=null)return false;
    files.eraseOwnedStudentCard(row.studentCardFileId(),row.userId().toString());
    mapper.evidenceDeleted(id,clock.instant());
    audit.record(row.userId().toString(),"CAMPUS_EVIDENCE_PURGED","CAMPUS_VERIFICATION",id,Map.of("trigger","RETENTION","retentionDays",config.retention().toDays()));
    return true;
  }
}
