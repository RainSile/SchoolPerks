package com.campus.perks.user.controller;

import com.campus.perks.user.dto.AvatarRequest;
import com.campus.perks.user.serviceImpl.AvatarService;
import com.campus.perks.user.vo.AvatarView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1") @Tag(name="用户头像")
@ConditionalOnProperty(name="campus.auth.enabled",havingValue="true")
public class AvatarController {
  private final AvatarService avatars;
  public AvatarController(AvatarService avatars){this.avatars=avatars;}
  @GetMapping("/default-avatars") @Operation(summary="查询可选默认头像")
  public List<AvatarService.DefaultAvatar> defaults(){return avatars.defaults();}
  @GetMapping("/default-avatars/{key}/image") @Operation(summary="读取默认头像的短时存储链接")
  public ResponseEntity<Void> defaultImage(@PathVariable String key){
    return ResponseEntity.status(HttpStatus.FOUND).cacheControl(CacheControl.noStore()).location(avatars.defaultImage(key)).build();
  }
  @GetMapping("/users/{id:[0-9]+}/avatar") @Operation(summary="读取用户公开头像",description="仅返回当前绑定的头像或默认头像，不接受文件ID，不暴露学生证或私有上传列表")
  public ResponseEntity<Void> image(@PathVariable String id){
    return ResponseEntity.status(HttpStatus.FOUND).cacheControl(CacheControl.noStore()).location(avatars.image(id)).build();
  }
  @PutMapping("/users/me/avatar") @Operation(summary="更换本人头像") @SecurityRequirement(name="bearerAuth")
  public AvatarView update(@Valid @RequestBody AvatarRequest request){return avatars.update(request);}
}
