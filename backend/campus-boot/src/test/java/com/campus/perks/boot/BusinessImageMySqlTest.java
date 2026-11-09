package com.campus.perks.boot;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.campus.perks.integration.port.ObjectStoragePort;
import com.campus.perks.user.serviceImpl.CampusEvidenceRetentionService;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/** Real MySQL/controller authorization; only external object storage is a declared double. */
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables({
  @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="CAMPUS_MYSQL_TEST",matches="true"),
  @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="CAMPUS_BUSINESS_EVIDENCE_DIR",matches=".+")
})
class BusinessImageMySqlTest extends BusinessReadChainsMySqlTest {
  @Autowired CampusEvidenceRetentionService retention;
  @BeforeEach void imageStorage() {
    when(storage.createUploadUrl(anyString(),anyString(),anyLong(),any())).thenReturn(URI.create("https://storage.invalid/upload"));
    when(storage.seal(anyString(),anyString())).thenReturn(new ObjectStoragePort.StoredObject(64,"image/png",
      new byte[]{(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a}));
  }
  ResultActions requestImage(String method,String path,long actor,Object data)throws Exception{
    return call(method,path,token(actor),data);
  }
  String image(long owner,String purpose,boolean complete)throws Exception {
    String id=body(requestImage("POST","/uploads",owner,Map.of("fileName","synthetic.png","contentType","image/png","size",64,"purpose",purpose))
      .andExpect(status().isCreated())).path("id").asText();
    if(complete)requestImage("PUT","/uploads/"+id+"/completion",owner,null).andExpect(status().isOk());
    return id;
  }
  Map<String,Object> application(String image){
    return Map.of("schoolId",""+school,"realName","合成测试同学","studentNumber","SYNTHETIC-2026","studentCardFileId",image);
  }
  String submit(String image)throws Exception {
    return body(requestImage("POST","/users/me/campus-verifications",user,application(image)).andExpect(status().isCreated())).path("id").asText();
  }
  void approve(String id)throws Exception{
    requestImage("POST","/admin/campus-verifications/"+id+"/reviews",admin,Map.of("decision","APPROVED","reason","synthetic test")).andExpect(status().isCreated());
  }
  @Test void imagePurposesUseSeparatePrefixesAndRejectNonImages()throws Exception{
    for(String purpose:List.of("STUDENT_CARD","USER_AVATAR","COMMUNITY_IMAGE")){
      requestImage("POST","/uploads",user,Map.of("fileName","bad.pdf","contentType","application/pdf","size",64,"purpose",purpose))
        .andExpect(status().isBadRequest());
      String id=image(user,purpose,true);
      String key=db.queryForObject("SELECT immutable_key FROM campus_file_object WHERE id=?",String.class,id);
      assertThat(key).startsWith(switch(purpose){case "STUDENT_CARD"->"private/student-cards/sealed/";case "USER_AVATAR"->"private/avatars/custom/sealed/";default->"private/community-images/sealed/";});
    }
    requestImage("POST","/uploads",user,Map.of("fileName","large.png","contentType","image/png","size",2097153,"purpose","USER_AVATAR"))
      .andExpect(status().isBadRequest());
  }
  @Test void imageStudentCardRequiresOwnedReadyCorrectPurposeAndReviewerPermission()throws Exception{
    requestImage("POST","/users/me/campus-verifications",user,Map.of("schoolId",""+school,"realName","synthetic","studentNumber","TEST"))
      .andExpect(status().isBadRequest());
    requestImage("POST","/users/me/campus-verifications",user,application(image(merchant,"STUDENT_CARD",true))).andExpect(status().isNotFound());
    requestImage("POST","/users/me/campus-verifications",user,application(image(user,"USER_AVATAR",true))).andExpect(status().isConflict());
    requestImage("POST","/users/me/campus-verifications",user,application(image(user,"STUDENT_CARD",false))).andExpect(status().isConflict());
    String id=submit(image(user,"STUDENT_CARD",true));
    String route="/admin/campus-verifications/"+id+"/student-card-download-tickets";
    requestImage("POST",route,restrictedAdmin,null).andExpect(status().isForbidden());
    requestImage("POST",route,merchant,null).andExpect(status().isForbidden());
    requestImage("POST","/users/me/campus-verifications/"+id+"/student-card-download-tickets",account("USER"),null).andExpect(status().isNotFound());
    requestImage("POST",route,admin,null).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
    assertThat(db.queryForObject("SELECT school_id FROM campus_user WHERE id=?",Long.class,user)).isNull();
    approve(id);
    requestImage("GET","/users/me",user,null).andExpect(jsonPath("$.campusVerificationStatus").value("APPROVED"));
    assertThat(db.queryForObject("SELECT details FROM campus_audit_event WHERE action='CAMPUS_EVIDENCE_VIEWED' AND resource_id=?",String.class,id)).doesNotContain("合成测试同学","SYNTHETIC-2026","signature");
  }
  @Test void imageRetentionDeletesOnlyReviewedOldEvidenceAndPreservesDecision()throws Exception{
    String photo=image(user,"STUDENT_CARD",true),id=submit(photo),avatar=image(user,"USER_AVATAR",true);
    requestImage("PUT","/users/me/avatar",user,Map.of("fileId",avatar)).andExpect(status().isOk());
    assertThat(retention.purge(id)).isFalse();
    approve(id);
    assertThat(retention.purge(id)).isFalse();
    db.update("UPDATE campus_campus_verification SET reviewed_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 31 DAY) WHERE id=?",id);
    assertThat(retention.due()).contains(id);
    assertThat(retention.purge(id)).isTrue();
    assertThat(retention.purge(id)).isFalse();
    assertThat(db.queryForObject("SELECT status FROM campus_file_object WHERE id=?",String.class,photo)).isEqualTo("DELETED");
    assertThat(db.queryForObject("SELECT status FROM campus_file_object WHERE id=?",String.class,avatar)).isEqualTo("READY");
    assertThat(db.queryForObject("SELECT status FROM campus_campus_verification WHERE id=?",String.class,id)).isEqualTo("APPROVED");
    assertThat(db.queryForObject("SELECT school_id FROM campus_user WHERE id=?",Long.class,user)).isEqualTo(school);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM campus_campus_verification_review WHERE verification_id=?",Integer.class,id)).isEqualTo(1);
    requestImage("POST","/admin/campus-verifications/"+id+"/student-card-download-tickets",admin,null).andExpect(status().isGone());
    requestImage("POST","/users/me/campus-verifications/"+id+"/student-card-download-tickets",user,null).andExpect(status().isGone());
    requestImage("PUT","/uploads/"+photo+"/completion",user,null).andExpect(status().isGone());
    var keys=db.queryForMap("SELECT staging_key,immutable_key FROM campus_file_object WHERE id=?",photo);
    verify(storage).delete(keys.get("staging_key").toString());verify(storage).delete(keys.get("immutable_key").toString());
  }
  @Test void imageRetentionFailureKeepsMetadataForRetry()throws Exception{
    String photo=image(user,"STUDENT_CARD",true),id=submit(photo);approve(id);
    db.update("UPDATE campus_campus_verification SET reviewed_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 31 DAY) WHERE id=?",id);
    doThrow(new IllegalStateException("synthetic-delete-failure")).when(storage).delete(anyString());
    assertThatThrownBy(()->retention.purge(id)).isInstanceOf(IllegalStateException.class);
    assertThat(db.queryForObject("SELECT evidence_deleted_at FROM campus_campus_verification WHERE id=?",java.sql.Timestamp.class,id)).isNull();
    assertThat(db.queryForObject("SELECT status FROM campus_file_object WHERE id=?",String.class,photo)).isEqualTo("READY");
  }
  @Test void imageRejectedEvidenceExpiresButPendingPhotoDoesNot()throws Exception{
    String id=submit(image(user,"STUDENT_CARD",true));
    db.update("UPDATE campus_campus_verification SET submitted_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 90 DAY) WHERE id=?",id);
    assertThat(retention.due()).doesNotContain(id);
    assertThat(retention.purge(id)).isFalse();
    requestImage("POST","/admin/campus-verifications/"+id+"/reviews",admin,Map.of("decision","REJECTED","reason","synthetic rejected")).andExpect(status().isCreated());
    db.update("UPDATE campus_campus_verification SET reviewed_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 31 DAY) WHERE id=?",id);
    assertThat(retention.purge(id)).isTrue();
    assertThat(db.queryForObject("SELECT status FROM campus_campus_verification WHERE id=?",String.class,id)).isEqualTo("REJECTED");
  }
  @Test void imageCommunityUsesCurrentPublicAuthorAvatarWithoutIdentityData()throws Exception{
    requestImage("PUT","/users/me/avatar",user,Map.of("defaultKey","fresh-green")).andExpect(status().isOk());
    String post=body(requestImage("POST","/posts",user,Map.of("schoolId",""+school,"content",Map.of("title","synthetic avatar community","body","synthetic body","imageIds",List.of()))).andExpect(status().isCreated())).path("id").asText();
    String review=body(requestImage("POST","/posts/"+post+"/review-requests",user,Map.of("version",0)).andExpect(status().isCreated())).path("id").asText();
    requestImage("POST","/admin/post-review-requests/"+review+"/reviews",admin,Map.of("decision","APPROVED","reason","synthetic public author")).andExpect(status().isCreated());
    mvc.perform(get("/api/v1/posts/"+post)).andExpect(status().isOk())
      .andExpect(jsonPath("$.author.id").value(""+user))
      .andExpect(jsonPath("$.author.nickname").value("rbac-test"))
      .andExpect(jsonPath("$.author.avatarUrl").value(org.hamcrest.Matchers.startsWith("/api/v1/users/"+user+"/avatar")));
    var comment=body(mvc.perform(post("/api/v1/posts/"+post+"/comments")
      .header("Authorization",token(user)).header("Idempotency-Key",UUID.randomUUID().toString())
      .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("body","synthetic comment"))))
      .andExpect(status().isCreated()));
    assertThat(comment.path("author").size()).isEqualTo(3);
    assertThat(comment.path("author").path("avatarUrl").asText()).startsWith("/api/v1/users/"+user+"/avatar");
    assertThat(comment.path("author").toString()).doesNotContain("schoolId","phone","studentCard","permissions");
  }
  @Test void imageAvatarDefaultsAndOwnCustomFileNeverExposeStudentCard()throws Exception{
    mvc.perform(get("/api/v1/default-avatars")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
    mvc.perform(get("/api/v1/default-avatars/campus-blue/image")).andExpect(status().isFound());
    requestImage("PUT","/users/me/avatar",user,Map.of("defaultKey","unknown")).andExpect(status().isNotFound());
    requestImage("PUT","/users/me/avatar",user,Map.of("fileId",image(merchant,"USER_AVATAR",true))).andExpect(status().isNotFound());
    requestImage("PUT","/users/me/avatar",user,Map.of("fileId",image(user,"STUDENT_CARD",true))).andExpect(status().isConflict());
    String avatar=image(user,"USER_AVATAR",true);
    requestImage("PUT","/users/me/avatar",user,Map.of("fileId",avatar)).andExpect(status().isOk());
    requestImage("GET","/uploads/"+avatar,merchant,null).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/users/"+user+"/avatar")).andExpect(status().isFound());
    requestImage("PUT","/users/me/avatar",user,Map.of("defaultKey","sunshine")).andExpect(status().isOk());
    requestImage("GET","/users/me",user,null).andExpect(jsonPath("$.avatar.defaultKey").value("sunshine"));
    requestImage("PUT","/users/me/avatar",user,Map.of("defaultKey","campus-blue","userId",""+merchant)).andExpect(status().isBadRequest());
  }
}
