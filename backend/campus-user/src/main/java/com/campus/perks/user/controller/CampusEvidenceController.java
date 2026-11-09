package com.campus.perks.user.controller;
import com.campus.perks.integration.port.FileAccessPort;
import com.campus.perks.user.serviceImpl.CampusReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/users/me/campus-verifications")
@Tag(name="校园认证照片") @SecurityRequirement(name="bearerAuth")
@ConditionalOnProperty(name="campus.auth.enabled",havingValue="true")
public class CampusEvidenceController {
  private final CampusReviewService service;
  private final com.campus.perks.user.config.CampusEvidenceProperties config;
  public CampusEvidenceController(CampusReviewService service,com.campus.perks.user.config.CampusEvidenceProperties config){this.service=service;this.config=config;}
  public record Capabilities(boolean photoRequired,long retentionDays) {}
  @GetMapping("/capabilities") @Operation(summary="查询学生证照片要求和审核后保留天数")
  public Capabilities capabilities(){return new Capabilities(true,config.retention().toDays());}
  @PostMapping("/{id}/student-card-download-tickets") @Operation(summary="本人查看学生证首页",description="仅申请人；短签私有访问，清理后410")
  public ResponseEntity<FileAccessPort.Download> image(@AuthenticationPrincipal Jwt jwt,@PathVariable String id){
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.ownStudentCard(jwt.getSubject(),id));
  }
}
