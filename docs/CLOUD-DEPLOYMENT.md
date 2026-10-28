# 前后端云端部署与迁移

2026-10-28 后续部署已完成：最新前后端与V13已上线，云端启用真实PNVS并关闭模拟。健康检查、16项公网接口检查、桌面/手机页面和云端只读核验连通性通过，本次部署新增短信0次；此前手机收件2/3仍待核实。


更新：2026-10-28。前端和后端部署在应用服务器；新建 MySQL 并导入原本机业务库。Redis、MongoDB、RabbitMQ、Elasticsearch、MinIO 保留在原中间件服务器，使用加密连接访问。模型、Pinecone 与地图继续使用供应商 API。

## 运行结构

```text
浏览器 / 支付宝异步通知
  └─ HTTPS :443 → Caddy
       ├─ /                 Vue 静态文件及 SPA 回退
       ├─ /api/*            127.0.0.1:8080 Spring Boot
       └─ /<bucket>/*       127.0.0.1:19000 → 原服务器 MinIO

Spring Boot → 本机 MySQL 127.0.0.1:3306
            → 加密隧道的本机端口 → 原服务器各中间件
            → HTTPS 供应商 API
```

前端使用相对 `/api/v1`，无需编入服务器 IP。只向公网开放网站 TCP 80/443 与受访问控制的 SSH；MySQL、Java 端口和中间件转发端口均绑定回环地址。MinIO 桶仍为私有，浏览器上传／下载需要带签名的临时 URL。

## 公开模板与私有配置

后端分支的 `deploy/app/` 提供 Caddy、systemd、启动脚本、MySQL 参数及环境变量示例。实际值只保存在服务器受限目录，不属于发布源码：

| 配置 | 用途与迁移时修改项 |
| --- | --- |
| `private/runtime.env` | `PUBLIC_HOST`、Java 路径与内存、静态文件路径、`BACKEND_UPSTREAM`、`MINIO_UPSTREAM`、ACME 参数 |
| `private/backend.env` | 数据库、身份密钥、供应商 API、业务开关、连接池、外部服务地址及凭据 |
| `private/infra-tunnel.env` | 中间件 SSH 主机、端口、专用密钥、固定主机公钥及各端口映射 |
| `config/mysql.cnf` | 本机 MySQL 的资源限制、路径和监听端口 |

`run-backend.sh` 根据 `PUBLIC_HOST` 推导 HTTPS CORS 来源、MinIO 浏览器签名地址和支付宝通知地址；也允许显式设置 `CORS_ALLOWED_ORIGINS`、`MINIO_PUBLIC_ENDPOINT`、`ALIPAY_SANDBOX_NOTIFY_URL` 覆盖。更换应用主机或中间件主机时修改这些配置即可，不需要修改 Java/Vue 源码。

`backend.env.example` 的 `false` 是安全的示例默认值，**不是当前已部署服务的实际开关**。当前云端按已有私有配置启用所需服务，交易定时任务、通知派发、搜索重建已经启用；公网固定短信模拟保持关闭。后续 PNVS 短信实现与真实专项已完成，但须另行部署包含 V13 的新包并注入私有变量，见 [接入说明](ALIYUN-PNVS-SETUP.md)。`LOGGING_LEVEL_SDK=OFF` 关闭支付宝 SDK 原始签名请求日志，保留应用自身经过处理的错误码和任务状态。

私有目录权限 `0700`，配置文件 `0600`；systemd 由系统管理器读取后将环境传给专用服务账号。目录约定为 `/opt/campus-perks-app`；需要更换安装目录时，统一调整 unit、MySQL 路径和环境文件中的部署路径，而不是改业务代码。SSH 配置采用固定主机公钥和独立受限密钥，不关闭主机校验。

## 本次运行资源

| 项目 | 已使用配置 |
| --- | --- |
| Java | Temurin JRE 17.0.21+9，应用仍按 Java 17 构建 |
| MySQL | 8.0.46，glibc 2.17 构建，192 MiB InnoDB 缓冲池，最大 25 个连接 |
| Caddy | 2.11.7，HTTP/1.1、HTTP/2，自动 HTTPS |
| Java 内存 | 初始堆 128 MiB、最大堆 512 MiB、服务上限 896 MiB |
| 其他上限 | MySQL 512 MiB，Caddy 192 MiB，隧道 64 MiB |
| 主机 | 约 1.8 GiB 物理内存，增加 6 GiB swap；本次稳定检查 swap 使用为 0 |

下载地址、固定版本与校验值在 `deploy/app/runtime-lock.json`。Java/Caddy 使用官方 SHA-256 校验，MySQL 先校验官方 MD5 后记录下载归档的 SHA-256；后者是本次实际文件摘要，不能冒充额外独立的供应商签名。未改动项目 Maven/npm 依赖版本。

## 新环境安装顺序

1. 准备系统运行库、SSH 客户端与支持文件 capability 的文件系统。按 lock 文件下载并检查运行时，分别放入 `runtime/java`、`runtime/mysql`、`runtime/bin/caddy`。本次主机是旧版 CentOS 7，因此采用 glibc 2.17 兼容运行时；迁移到新环境应优先使用仍受支持的系统。
2. 创建无交互登录的 `campus-app`、`campus-web`、`campus-mysql` 账号，以及 `config`、`private`、`releases`、各 `data` 和日志目录。为不同服务分别授予其所需写目录；应用安装目录允许专用账号穿越，私有目录不可被普通账号列出。
3. 从公开 `.example` 建立私有配置，使用随机独立数据库密码和稳定身份密钥，不把示例占位值当成可用配置。应用数据库账号仅授权业务库；数据库 root 凭据与应用凭据分开保存。
4. 仅对全新空数据目录初始化 MySQL，启动回环地址上的实例后立即设置 root 密码，再创建业务库和受限应用账号。**已有数据目录不能重新初始化或覆盖导入**。导入原库的步骤见下一节。
5. 通过可信渠道核实中间件主机公钥；安装只允许所需转发端口、禁用交互命令的专用 SSH 公钥。将其私钥保存在应用服务器私有目录，启动 `campus-infra-tunnel`。如后续改为私网/VPN/API 入口，直接替换各服务的连接配置并停用不再需要的隧道。
6. 构建后端 JAR 和前端静态文件，放入独立版本目录，核对摘要后将 `current` 指向该版本。前端构建使用项目锁文件，后端使用现有 `external-sdks` 配置，不复制本机私有目录。
7. 将公开 service 模板和启动脚本安装到配置目录，四个 unit 注册至 systemd。Caddy 二进制只授予绑定低端口所需的 `cap_net_bind_service`，以 `campus-web` 运行。更换二进制后重新设置并验证该 capability。启动并启用 MySQL、隧道、后端、网站服务。
8. 保持入口 80/443 可达，等待受信任 HTTPS 证书签发，验证主页和 API；再启用私有支付宝沙箱配置，使用实际沙箱钱包验收。不得使用忽略证书错误作为通过依据。

对使用模板目录的实例，常用运维命令：

```sh
systemctl daemon-reload
systemctl enable campus-mysql campus-infra-tunnel campus-backend campus-web
systemctl start campus-mysql campus-infra-tunnel campus-backend campus-web
systemctl is-active campus-mysql campus-infra-tunnel campus-backend campus-web
curl --fail http://127.0.0.1:8080/actuator/health
systemctl reload campus-web
```

只修改运行配置时，重启受影响的后端或隧道；Caddy 配置支持 reload。发布新 JAR／前端时保留前一个版本，切换 `current` 后检查健康与业务冒烟；失败时回退版本指针。不得用删除数据库或存储数据目录的方式回滚。

## 数据库迁移与原资源保护

导出本机 `campus_perks_db` 时使用事务一致性快照，导出文件留在私有目录，不写入 Git。迁移前后核对基表行数及逐行内容摘要；本次在新库业务测试前，56 张基表的行数／内容摘要全部一致，9 个视图已导入。本机源库导出前后摘要一致，未在本机库写入测试订单。

视图定义的 `DEFINER` 必须对应新服务器实际存在的受限账号；导入前审核这一映射，不能把旧账号直接当成新主机权限主体。若导入失败，先定位目标库的部分导入状态和错误，不得盲目重置原库。Flyway 已执行的迁移保持原样。

本次验收会在云端新库增加隔离商家、账号和真实沙箱流水，因此不能要求“验收后云端全库摘要仍与原库完全相同”。原中间件实例未迁移，原 MinIO 文件仍在原服务器；只新增受限转发访问。备份应覆盖数据库、身份密钥、Caddy 持久证书状态与业务对象存储；备份内容需与公开源码分开保管。

## HTTPS 与支付通知

`PUBLIC_HOST` 可为公网 IP 或域名。本次使用 ACME `shortlived` 配置取得受信任 IP 证书，Caddy 持久数据目录负责续期；80/443、证书目录权限和磁盘可用性必须持续满足。`default_sni` 使没有发送 IP SNI 的客户端也可正常连接；不能用临时自签名证书代替沙箱通知验收。

通知地址为 `https://<PUBLIC_HOST>/api/v1/payment-notifications/alipay-sandbox`。收到通知后仍由适配器验证签名、应用／商家、金额与状态，再以幂等方式更新订单。前端回跳或买家口头反馈不能作为付款依据。真实结果见 [支付宝云端验收](ALIPAY-CLOUD-ACCEPTANCE.md)。

Caddy 对 `/api/*` 关闭代理缓冲，保留 SSE 输出；对 MinIO 转发保留浏览器 Host 与原桶路径，保证 SigV4 校验。公网诊断／Swagger 入口受阻断，管理业务 API 仍必须通过后端账号与权限校验。

## 迁移后验收清单

- 普通 TLS 校验下的 HTTPS 主页、静态资源与 SPA 深链接；桌面／手机无横向溢出。
- 三端密码登录后的真实路由，以及非本人资源拒绝访问。
- 优惠／套餐查询、商家申请与审核、账号端转换。
- 原服务器私有 MinIO 的真实签名上传、下载、内容一致性、未签名及跨账号拒绝。
- 官方沙箱虚拟付款 → 实际签名通知 → 订单已支付 → 业务退款申请 → 正常 worker 退款成功；重复退款幂等，跨账号退款拒绝。
- 后台任务、资源占用、监听范围、配置权限和证书续期条件；公网无数据库与中间件管理端口。
- 仅推送经过筛选的源码、模板和技术文档，不推送密钥、导出库、学业文稿、测试账号及原始回调内容。
