package com.campus.perks.user.serviceImpl;

import com.campus.perks.common.exception.ApplicationException;
import com.campus.perks.integration.port.*;
import com.campus.perks.security.service.CurrentActor;
import com.campus.perks.user.config.AvatarProperties;
import com.campus.perks.user.dto.AvatarRequest;
import com.campus.perks.user.entity.AvatarEntity;
import com.campus.perks.user.mapper.*;
import com.campus.perks.user.vo.AvatarView;
import java.net.URI;
import java.time.Clock;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name="campus.auth.enabled", havingValue="true")
public class AvatarService implements PublicUserPort {
  private final AvatarMapper avatars;
  private final UserMapper users;
  private final FileAccessPort files;
  private final ObjectStoragePort storage;
  private final AccountDirectoryPort accounts;
  private final AuditPort audit;
  private final AvatarProperties config;
  private final Clock clock;
  public AvatarService(AvatarMapper avatars, UserMapper users, FileAccessPort files,
      ObjectStoragePort storage, AccountDirectoryPort accounts, AuditPort audit, AvatarProperties config, Clock clock) {
    this.avatars=avatars;this.users=users;this.files=files;this.storage=storage;
    this.accounts=accounts;this.audit=audit;this.config=config;this.clock=clock;
  }
  private long id(String value) {
    if(value==null || !value.matches("[1-9][0-9]{0,17}"))throw missing();
    return Long.parseLong(value);
  }
  private ApplicationException missing(){return new ApplicationException(HttpStatus.NOT_FOUND,"AVATAR_NOT_FOUND","头像不存在");}
  public AvatarView selected(String uid) {
    var row=avatars.find(id(uid));
    return new AvatarView(row==null?null:row.fileId(),row==null?config.defaultKey():row.defaultKey(),
      "/api/v1/users/"+uid+"/avatar"+(row==null?"":"?v="+row.updatedAt().toEpochMilli()));
  }
  public Profile profile(String uid) {
    var user=users.find(id(uid));
    if(user==null || !"ACTIVE".equals(user.status()))return new Profile(uid,"已停用用户","/api/v1/default-avatars/"+config.defaultKey()+"/image");
    return new Profile(uid,user.nickname(),selected(uid).imageUrl());
  }
  public record DefaultAvatar(String key,String label,String imageUrl) {}
  public List<DefaultAvatar> defaults() {
    return config.defaults().entrySet().stream().sorted(Map.Entry.comparingByKey())
      .map(e->new DefaultAvatar(e.getKey(),e.getValue(),"/api/v1/default-avatars/"+e.getKey()+"/image")).toList();
  }
  public URI defaultImage(String key) {
    if(!config.defaults().containsKey(key))throw missing();
    if(!storage.available())throw new ApplicationException(HttpStatus.SERVICE_UNAVAILABLE,"STORAGE_UNAVAILABLE","头像存储暂不可用");
    return storage.createDownloadUrl(config.objectPrefix()+"/"+key+".png",config.downloadTtl());
  }
  public URI image(String uid) {
    var user=users.find(id(uid));
    if(user==null || !"ACTIVE".equals(user.status()))throw missing();
    var row=avatars.find(user.id());
    if(row==null || row.fileId()==null)return defaultImage(row==null?config.defaultKey():row.defaultKey());
    files.requireReadyOwned(row.fileId(),uid,"USER_AVATAR");
    return files.signOwnedDownload(row.fileId(),uid).url();
  }
  @Transactional
  public AvatarView update(AvatarRequest request) {
    String uid=CurrentActor.id();accounts.lockActiveUser(uid);
    if(!request.isValidSelection())throw new ApplicationException(HttpStatus.BAD_REQUEST,"INVALID_AVATAR","请选择一个头像来源");
    if(request.fileId()!=null)files.requireReadyOwned(request.fileId(),uid,"USER_AVATAR");
    else if(!config.defaults().containsKey(request.defaultKey()))throw missing();
    avatars.save(new AvatarEntity(id(uid),request.fileId(),request.defaultKey(),clock.instant()));
    audit.record(uid,"USER_AVATAR_CHANGED","USER",uid,Map.of("source",request.fileId()==null?"DEFAULT":"UPLOAD"));
    return selected(uid);
  }
}
