# 配置与运行

前端使用Node.js22.12+、npm；后端使用JDK17、Maven3.6.3+。构建命令见各代码分支README。默认前端5173、后端8080，均可配置。前后端必须对应API代理、WebSocket和CORS设置。

## 后端profile

- skeleton：默认，不创建数据源，不连接数据库，未启用的业务能力明确不可用。
- local：由环境变量提供MySQL连接；Flyway执行classpath:db/migration迁移。
- prod：使用受控生产配置，关闭不需要的调试入口；私有配置、密钥不进入Git。

backend/.env.example只列环境变量，Spring Boot不会自动加载.env。通过当前进程、IDE或部署系统注入。主要变量：

| 用途 | 变量 |
| --- | --- |
| 服务监听 | SERVER_ADDRESS、SERVER_PORT |
| 数据库 | DB_HOST、DB_PORT、DB_NAME、DB_USERNAME、DB_PASSWORD |
| 文档及CORS | API_DOCS_ENABLED、CORS_ALLOWED_ORIGINS |
| 认证 | AUTH_ENABLED、JWT_ENABLED、JWT_SECRET_BASE64、OTP_HASH_SECRET_BASE64、IDENTITY_KEY_BASE64 |
| 短信模拟 | SMS_SIMULATED_ENABLED、SMS_SIMULATED_CODE、SMS_CODE_LENGTH |
| 对象存储 | MINIO_ENDPOINT、MINIO_ACCESS_KEY、MINIO_SECRET_KEY、MINIO_BUCKET |
| 检索与向量 | SEARCH_BACKEND、ELASTICSEARCH_*、PINECONE_* |
| 模型 | AI_ENABLED、AI_BASE_URL、AI_API_KEY、AI_CHAT_MODEL、AI_EMBEDDING_* |
| 可选能力 | REDIS_*、MONGO_*、RABBITMQ_*、AMAP_*、ASSISTANT_*、MARKETING_* |

变量完整清单与默认值以源码Properties类及示例文件为准。认证所需三个随机密钥分别生成、持久保存，不使用共享占位值。供应商URI不能携带用户名密码、查询参数或重定向捷径；不要通过关闭TLS验证解决证书问题。

## 外部服务

所有凭据仅在后端环境提供。Redis用于限频等可选能力，MongoDB用于私有助手历史，RabbitMQ用于事件适配。Elasticsearch+IK使用相同完整版本；索引和Pinecone资源须先手工准备，应用不会擅自删除、重建云资源。

聊天模型与Embedding模型可分开配置；Embedding维度须与向量索引一致。Pinecone使用BYOV向量索引和当前适配器的Documents数据面合同。MinIO保存私有对象，只通过短时凭据访问。地图JS公开Key与后端安全码分开管理。支付宝仅支持显式配置的沙箱，不把待支付订单当成已付款。

真实短信供应商通过SmsSenderPort替换。模拟短信必须显式在local启用，并通过变量设置验证码和长度；不得在生产开启，Java/Vue中不固定验证码。

## 数据库与初始化

使用专用MySQL8库，先以受控账号执行后端分支scripts/create-database.sql，或自行创建与DB_NAME一致的库。启动local后由Flyway管理表结构；V1–V11为已有迁移，不修改已执行文件、不使用clean或baseline掩盖不匹配。

仓库不含默认管理员及真实演示账号。账号、校园、门店等业务数据须按受控流程准备。不要导入未知数据导出，也不要对共享生产数据库运行测试。

<!-- campus-release-20261026-start -->
## 2026-10-26 更新

助手默认关闭；配置模型、Redis、MongoDB并显式ASSISTANT_ENABLED后使用。ASSISTANT_MAX_TOOL_ROUNDS默认16、ASSISTANT_MAX_TOOL_CALLS默认32，另有无工具总结；工作线程/队列/输出长度通过campus.assistant-stream配置。所有凭据由私有环境提供。
<!-- campus-release-20261026-end -->
