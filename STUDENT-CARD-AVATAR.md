# 学生证照片认证、头像与云端业务图片

用户已确认：仅业务图片进入 MinIO；学生证在审核完成后保留30天，待审材料不按此期限删除。首页装饰、Logo、页脚图片继续使用原静态资源。

## 业务链路

新校园认证必须选择学校、填写本人姓名和学号，并拍摄或选择学生证首页（JPEG/PNG）。浏览器向后端申请专属上传签名，直接上传 MinIO，再确认封存；提交申请只能引用本人 READY 的 STUDENT_CARD 文件。上传本身不改变认证状态。具有 campus:review 的审核员可查看身份信息及私有照片，再通过或驳回；禁止自审、重复审批与跨用户取图。

照片每次申请独立引用，已用于旧申请的照片不能重复绑定新申请。驳回后请重新上传。历史已通过认证保留原结果；没有照片的旧待审申请不能直接通过，需驳回后重新提交。身份证明文本沿用加密存储。

头像在个人资料中选择：四款默认头像（票券、咖啡、书本、星星）或上传本人 JPEG/PNG 自定义头像。默认2MiB上限可配置，上传后还需点击使用头像。头像出现在三端导航、本人资料、社区作者和评论。用户仅能绑定本人的 USER_AVATAR 文件，学生证或社区图片不能作为头像引用；普通私有文件接口继续拒绝他人访问。已绑定头像通过独立公开头像资源读取，不暴露手机号、认证材料或权限。

## 实际存储位置

存储在原中间件服务器上的私有桶 **campus-perks-files**，应用服务器通过现有配置化连接访问。下面是桶内对象目录，不是公开文件目录：

| 类别 | 桶内对象路径 |
| --- | --- |
| 学生证临时上传 | private/student-cards/pending/{随机ID} |
| 学生证封存照片 | private/student-cards/sealed/{随机ID} |
| 默认头像PNG | private/avatars/default/{key}.png |
| 自定义头像 | private/avatars/custom/pending/{ID} 和 private/avatars/custom/sealed/{ID} |
| 社区图片 | private/community-images/pending/{ID} 和 private/community-images/sealed/{ID} |
| 原商家资质及旧文件 | 原 private/merchant-qualifications/... 继续兼容，不强制搬动旧对象 |

MinIO宿主机的数据卷根目录为 **/opt/campus-perks-storage/data**，服务目录为 /opt/campus-perks-storage。使用 S3/MinIO 客户端按“桶+对象路径”访问，不直接编辑数据卷内的内部文件。桶不启用匿名读取，应用按业务权限签发短期链接；文件地址由配置生成，数据库不存固定主机的外链。

## 照片保留与删除

自动任务只选 APPROVED/REJECTED、reviewed_at已满配置期限、尚未清理的学生证。锁定并再次检查后删除封存与临时对象，再标记文件DELETED及申请evidenceDeletedAt，保留审核结果、学校身份和审计记录。默认每小时分批处理，首次启动延迟一分钟；因此是审核30天后进入下一轮清理，不承诺精确到秒。删除失败会重试，不将失败记为成功；存储删除与数据库不是分布式事务，极端数据库/审计故障时通过幂等重试收敛。

清理后本人和审核员的照片接口返回410；已签发的照片链接也因对象实际删除而无法读取。任务不会清理待审认证、头像或社区图片。MinIO版本控制已核实关闭，避免仅产生删除标记而保留旧版本。各新目录pending临时上传由保留原规则的MinIO生命周期按1天清理；封存但未形成审核申请的对象不属于“审核后30天”任务。

## 接口与权限

所有接口带OpenAPI/Knife4j注解，遵循现有REST资源方式：

| 方法与路径（/api/v1） | 权限与行为 |
| --- | --- |
| POST /uploads、PUT /uploads/{id}/completion | 登录本人；purpose新增STUDENT_CARD、USER_AVATAR，图片限定JPEG/PNG，原类型不受放宽 |
| GET /users/me/campus-verifications/capabilities | 学生端本人；返回photoRequired及配置retentionDays |
| POST /users/me/campus-verifications | 学生本人；新增必填studentCardFileId，其他原字段保留 |
| POST /users/me/campus-verifications/{id}/student-card-download-tickets | 申请本人；他人404，清理后410 |
| POST /admin/campus-verifications/{id}/student-card-download-tickets | ADMIN且campus:review；短签、no-store、访问审计 |
| POST /admin/campus-verifications/{id}/reviews | 原审核权限、自审及事务限制保持，通过前必须存在就绪学生证照片 |
| GET /default-avatars | 公开默认头像目录，只返回配置项 |
| GET /default-avatars/{key}/image | 公开读取指定默认头像的短时链接；不存在的key404 |
| PUT /users/me/avatar | 本人；fileId/defaultKey二选一，不接受目标用户编号或任意URL |
| GET /users/{数字id}/avatar | 公开当前头像；只能读取该用户当前绑定头像或默认图，不能指定私有文件ID |

本人资料新增avatar元数据，社区帖子和评论新增author={id,nickname,avatarUrl}，不包含认证信息。用户填写内容不能决定MinIO对象路径、桶或文件所有人。

## 配置与迁移

V15__student_card_evidence_and_user_avatars.sql为增量迁移：增加认证照片引用及删除标记、头像关系表，扩展文件purpose/status约束。V1–V15保持不可变；后续从V16起。上线前备份数据库；保留旧版本，禁止通过清库或回滚数据目录恢复。

| 环境变量 | 默认值 |
| --- | --- |
| FILE_STUDENT_CARD_PREFIX | private/student-cards |
| FILE_AVATAR_PREFIX | private/avatars/custom |
| FILE_COMMUNITY_IMAGE_PREFIX | private/community-images |
| AVATAR_DEFAULT_PREFIX | private/avatars/default |
| AVATAR_DEFAULT_KEY | campus-blue |
| AVATAR_MAX_BYTES | 2097152 |
| AVATAR_DOWNLOAD_TTL | 5m |
| CAMPUS_EVIDENCE_RETENTION | 30d |
| CAMPUS_EVIDENCE_CLEANUP_ENABLED | true |
| CAMPUS_EVIDENCE_CLEANUP_BATCH_SIZE | 20 |
| CAMPUS_EVIDENCE_CLEANUP_INTERVAL_MS | 3600000 |

默认头像目录和标签在application.yml的campus.avatars.defaults配置，不在业务代码中写服务器地址。更换主机修改现有MINIO_ENDPOINT、MINIO_PUBLIC_ENDPOINT、桶及对应前缀即可；不要把密钥放入前端。

后端分支deploy/minio/media/main.go使用现有固定MinIO源码go.mod/go.sum编译，增加所需前缀权限并保留既有规则，保存IAM/生命周期备份，不创建/重置用户或重启服务。维护时注入原受限目录中的MinIO管理员环境，运行该工具并设置DEFAULT_AVATAR_DIRECTORY为部署的默认头像PNG目录。deploy/minio/default-avatars/提供本项目自行绘制的SVG与256px PNG，文件key须与头像目录配置对应；工具不会上传SVG或用户材料。普通应用账号只读默认头像目录，其他新业务前缀沿用限定读写/删除权限。

## 验证记录与边界

- 136项相关用例（126项实际MySQL/MockMvc，10项单元/基础合同）通过，0失败/错误/跳过；V15首次迁移符合新增空表、Flyway记录和新增nullable列的预期，此后58表摘要一致。
- 8项真实MinIO专项全部通过，包括7项服务/存储检查、1项真实Vue→后端→MySQL→MinIO旅程；使用合成像素图片和独立测试账号，未上传真实学生证。验证私有读取、越权拒绝、实际对象删除、默认/自定义头像字节、移动端拍照入口、申请/人工审核/刷新状态。
- 十组前端回归与类型检查/正式构建单独记录；HTTP夹具不当作真实存储验收。图库直接PUT不携带平台JWT。测试对象和账号按精确ID清理，最终58表摘要一致；未发送短信、未付款、未调用模型。
- MinIO四款默认头像及新目录权限已实际配置，原私有配置、其他容器启动时间和重启次数未变。应用上线与公网验收以最后追加的部署记录为准。
