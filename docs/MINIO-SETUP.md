# MinIO 轻量部署与业务接入

日期：2026-10-27。本记录取代早期“内存不足，跳过MinIO”的当前状态；历史测试报告保留当时结论。

## 部署结果

现有云服务器已部署实际MinIO，独立目录 `/opt/campus-perks-storage`，Compose项目 `campus-perks-storage`。Redis、MongoDB、RabbitMQ、Elasticsearch的启动时间和重启次数前后相同，未调整原有服务。

| 项目 | 设置 / 最终观察 |
| --- | --- |
| 服务 | 官方 `RELEASE.2025-10-15T17-29-55Z` |
| 源码提交 | `9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a` |
| 构建 | 开发机Go1.27.3交叉编译linux/amd64；不在小服务器编译 |
| 镜像 | `campus-minio:RELEASE.2025-10-15T17-29-55Z`；基础镜像固定摘要 |
| 资源限制 | 256MiB内存；内存加交换总额384MiB；0.35CPU；128PID |
| 节流 | Go内存软目标160MiB；两个并发请求；最低扫描速度 |
| 最终观察 | 容器105.7MiB，宿主机可用523MiB；非负载容量保证 |
| 健康 | ready；OOMKilled=false；重启0次 |
| 网络 | 仅云端127.0.0.1:9000，控制台关闭，不开放安全组端口 |
| 存储桶 | `campus-perks-files`，私有，1GiB硬配额 |
| 清理 | 测试对象剩余0；数据目录约184KiB，含内部元数据 |

服务二进制实测SHA-256：`7af346b148aca97f8d4725e2c77c48fb2d2c655faab219a5a6a2581c0e6b07f8c84a`。这是本次构建摘要，不承诺其他工具链生成同样摘要。

MinIO社区仓库已归档，采用固定的官方安全修补发布并保持私有访问，不使用浮动latest或旧预编译标签。本部署为单机单盘，适合毕业设计小规模文件，未验收高可用和大规模并发。来源：[官方仓库](https://github.com/minio/minio)、[固定安全发布](https://github.com/minio/minio/releases/tag/RELEASE.2025-10-15T17-29-55Z)。

## 业务接入与权限

补齐真正的 `ObjectStoragePort`，复用已有REST/Knife4j接口与Vue上传组件，未新增默认管理员或演示业务数据。

1. 服务端生成对象路径与短时PUT签名，绑定Content-Type和Content-Length。客户端不能指定owner、桶或对象key，浏览器不获得账号密钥。
2. 浏览器直传后，完成接口复制至独立封存路径，再检查封存副本的实际大小、MIME及文件头；仅读最多16字节用于文件头判断。无效副本尝试删除，不转READY。
3. 旧上传链接重复写入pending对象，不会改变已封存副本；重复完成保持幂等。
4. 私有下载先校验业务权限，再生成默认2分钟短签，响应使用private/no-store和附件头。
5. 商家申请只引用本人READY资质；有 `merchant:review` 的审核员通过审核业务接口查看。普通管理员身份不能直接绕过通用文件归属检查。
6. 社区作者可读取自己草稿的图片；其他用户经公开帖子接口取得图片短签。删除帖子后拒绝签发新链接；已签发的链接仍可使用至签名到期，不是即时撤销。

文件接口保持 `GET /api/v1/storage-capabilities`、`POST /api/v1/uploads`、`PUT /api/v1/uploads/{id}/completion`、`POST /api/v1/uploads/{id}/download-tickets` 等既有合同。

桶匿名策略关闭。root仅供初始化，应用账号只具有专属桶定位/列表，以及配置前缀下的对象读写、删除和必要分段操作。前缀沿用 `private/merchant-qualifications`，其中也保存社区图片，不能只凭名称改掉它。pending对象和未完成分段配置一天清理；生命周期异步执行，不承诺到点立即删除。1GiB配额不含日志、镜像、内部元数据，仍需监测总磁盘。

## 配置与连接

已将应用账号、端点加入本机既有DPAPI加密配置；root密钥单独保存，没有写入仓库。默认提交配置仍是 `MINIO_ENABLED=false`。私有基础设施转发脚本增加 `19000 → 云端9000`。

本机运行需保持SSH私有转发，使后端和浏览器能访问 `http://127.0.0.1:19000`。本次验收专属转发结束后关闭，云端MinIO保持运行。`MINIO_ENDPOINT` 供服务端访问，`MINIO_PUBLIC_ENDPOINT` 决定浏览器签名地址，目前两者相同；生成签名后不能修改主机、端口或路径。127.0.0.1不能供其他电脑访问，未来公开部署需要浏览器可达的HTTPS入口与精确CORS，当前不开放明文公网9000。

上传默认10MiB，可通过 `FILE_MAX_BYTES` 配置。连接/读取/写入/整次调用超时可配置，校验范围为正值且不超过一分钟。CORS仅允许本机5050/5173的localhost、127.0.0.1来源。

构建、初始化、SSH转发、维护及回退见[部署说明](https://github.com/RainSile/SchoolPerks/blob/campus-backend/deploy/minio/README.md)，应用变量见[环境模板](https://github.com/RainSile/SchoolPerks/blob/campus-backend/deploy/minio/backend.env.example)。无需购买对象存储API，使用本次自建服务的应用账号。

## 验证记录

2026-10-27发布前复验：30项通过、失败0、错误0、跳过0（真实存储5项、商家回归14项、适配器4项、基础合同7项），Java17 Maven verify及打包通过，56表摘要差异0。证据阶段为 `.local/business-tests/minio-prepublish-20261027-205513/`。浏览器脚本已整理到后端分支的 `scripts/minio-live-browser-check.mjs`，不依赖检出前端源码；该位置调整后重新通过以上30项。

| 检查 | 结果与边界 |
| --- | --- |
| 适配器单元测试 | 4项通过：签名绑定、大小拒绝、封存副本及有界读取、异常脱敏 |
| 真实存储专项 | 5项通过，真实Spring业务/授权MySQL/云端MinIO，不mock存储 |
| 商家既有回归 | 14项通过；保留该类显式存储mock，与真实专项分开记录 |
| 浏览器 | Chrome真实回环页面跨来源PUT/GET，PNG字节一致；不是完整Vue端到端验收 |
| 构建 | Java17 `external-sdks verify` 通过，含基础合同、适配器及SDK共同加载 |
| 初始化工具 | 公开bootstrap源码在固定MinIO模块依赖下离线编译通过 |
| 数据 | 各轮真实测试事务回滚；56表行数及内容摘要差异0；仅清理捕获的精确对象key |

5项真实用例覆盖：资质上传、封存、旧链接重传、重复完成、审核员读取；其他学生/商家/管理员通用私有访问拒绝和匿名S3拒绝；错误类型/长度、业务大小上限和签名过期拒绝；无效PNG拒绝及副本清理；浏览器CORS上传/读取；社区图片随草稿、审核公开、删帖控制访问，通用私有接口仍拒绝他人。一个JUnit用例包含多条断言，不将这些分支虚增成独立用例数。

初期三轮浏览器虚拟页面被Chrome本地网络策略拦截，改为真实临时回环页面后通过，没有关闭浏览器安全或放宽CORS。真实专项最终证据在忽略目录 `.local/business-tests/minio-live-r05/`，部署/资源/构建证据在 `.local/minio-deploy/`。不直接上传私有日志、XML、密钥或签名链接。

复验先注入授权MySQL和应用存储配置、建立转发，准备Node/Playwright/Chrome，并设置 `CAMPUS_PLAYWRIGHT_MODULE`、`CAMPUS_BROWSER_EXECUTABLE` 指向安装位置：

```powershell
./scripts/test-business-backend.ps1 -Phase minio-repeat-01 -UseObjectStorageConfiguration -TestSelection 'MinioObjectStorageTest,MinioBusinessMySqlTest,MerchantStageTwoMySqlTest'
```

使用新Phase，避免覆盖证据。浏览器夹具临时绑定空闲127.0.0.1:5173并在完成后关闭；被占用则失败，不停止已有进程。默认业务回归显式关闭MinIO，只有开关允许真实存储测试。

Maven版本不升级：MinIO8.6.0移到普通integration依赖，确保实际JAR包含SDK；OkHttp5.1.0、Kotlin2.2.0沿用现有锁定组合。本轮未启动常驻前后端，未接入真实短信或支付宝沙箱，不代表整体终验完成。
