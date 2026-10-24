# SchoolPerks 后端

Java 17 + Spring Boot 3 的校园周边优惠平台后端，采用 `campus-*` Maven 多模块组织，提供 REST API、OpenAPI/Knife4j 和三端账号权限校验。

## 仓库分支

- [campus-backend](https://github.com/RainSile/SchoolPerks/tree/campus-backend)：本后端工程，位于 `backend/`。
- [campus-frontend](https://github.com/RainSile/SchoolPerks/tree/campus-frontend)：Vue 前端，位于 `frontend/`。
- [campus-doc](https://github.com/RainSile/SchoolPerks/tree/campus-doc)：经筛选的工程技术文档。

## 环境与构建

需要 JDK 17、Maven 3.6.3+、MySQL 8（运行数据库业务时）。项目检查 JDK 版本，依赖固定在父 POM 和各模块 POM；不要单独替换 Knife4j/springdoc 或 LangChain4j/MinIO 的传递依赖组合。

从仓库根目录执行：

```sh
cd backend
mvn -B verify
mvn -B -DskipTests package
java -jar campus-boot/target/campus-boot-0.1.0-SNAPSHOT.jar
```

默认是 `skeleton` profile：不连接数据库，不启用外部供应商，也不代表完整业务已可使用。默认监听 `127.0.0.1:8080`；可通过 `SERVER_ADDRESS`、`SERVER_PORT` 配置。接口文档 `/doc.html`、OpenAPI `/v3/api-docs`，由 `API_DOCS_ENABLED` 控制。

## 启用数据库业务

1. 自行建立专用 MySQL 数据库及账号。仓库根目录 `scripts/create-database.sql` 仅创建数据库，不导入个人演示数据。
2. 用当前进程环境变量或私有配置设置 `SPRING_PROFILES_ACTIVE=local`、`DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD`。
3. 启用认证前，分别准备并持久保存三个独立的32字节随机密钥的 Base64 值：`JWT_SECRET_BASE64`、`OTP_HASH_SECRET_BASE64`、`IDENTITY_KEY_BASE64`，再设置 `JWT_ENABLED=true`、`AUTH_ENABLED=true`。不要使用占位值、提交密钥或在每次重启时更换密钥。
4. 配置允许的前端来源 `CORS_ALLOWED_ORIGINS`。Flyway 在启动时执行 `campus-boot/src/main/resources/db/migration/` 中的迁移。已经执行过的迁移不能修改；后续追加新版本。
5. 业务 profile 启动示例：

```sh
java -jar campus-boot/target/campus-boot-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

`.env.example` 是变量清单，Spring Boot 不会自动读取它。通过 IDE、shell 或部署系统注入真实值，私有配置留在 Git 之外。仓库不提供默认管理员、真实账号密码或供应商密钥。

## 模块与业务

- `campus-common`、`campus-utils`、`campus-security`：基础类型、工具、JWT、会话、权限、CORS及审计契约。
- `campus-user`、`campus-merchant`、`campus-file`：密码／验证码认证、校园认证、商家入驻审核、门店成员及私有文件。
- `campus-coupon`、`campus-notification`：优惠发布审核、领券、短时核销凭据、站内通知。
- `campus-trade`：套餐、订单、秒杀、拼团、退款及支付宝沙箱。
- `campus-community`、`campus-search`：内容审核、评论／点赞／举报、位置查询、搜索投影。
- `campus-assistant`、`campus-analytics`、`campus-admin`：私有助手和营销草稿、统计看板、RBAC与平台审核。
- `campus-integration`：外部服务端口和可替换适配器，不让业务依赖供应商 SDK 类型。

Java 包按功能划分，每个业务模块包含自己的 controller、dto、vo、entity、mapper、service 与 serviceImpl。

## 可选服务

Redis、MongoDB、RabbitMQ、Elasticsearch + 同完整版本 IK、地图、AI/Embedding/Pinecone、MinIO和支付宝沙箱均需显式配置。默认关闭的能力不应假报成功；缺资源时返回明确的不可用状态。

`mvn -B -Pexternal-sdks verify` 会加入约定的外部 SDK 依赖和兼容检查；是否访问真实服务仍由对应环境开关决定。真实数据库／外部服务测试是显式选择的测试，不应对不受控的库或服务执行。变量以 `.env.example` 和对应 Properties 类为准。

## 运行与数据边界

公开注册创建普通用户；商家申请和平台权限按审核／授权流程管理。账号所属端和门店资源范围由后端验证，前端路由不能替代授权。生产环境应使用 HTTPS、正确的反向代理和独立凭据，并按需关闭在线 API 文档。不要上传数据库导出、用户文件、支付密钥或本机联调记录。
