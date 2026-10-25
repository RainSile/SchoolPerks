# 依赖版本与兼容性记录

## 2026-10-26 助手增量

Maven版本保持现有锁定组合，LangChain4j 1.22.0现有OpenAI适配依赖已包含OpenAiStreamingChatModel，无新增流式SDK或版本浮动。Java17编译、打包、真实provider流式回调及多步函数调用已验证。MVC使用现有SseEmitter，无需引入第二套WebFlux服务。

前端精确锁定markdown-it 15.0.3、DOMPurify 3.4.17（含自身类型声明），package-lock.json同步；Node22.14、Vue类型检查和Vite生产构建通过。禁用Markdown原生HTML与图片规则，并在v-html前使用HTML元素/属性白名单清理；安全链接、代码块、表格及分片文本通过浏览器验证。此检查不等同于全面供应链审计。

官方接口说明：[LangChain4j流式与取消](https://docs.langchain4j.dev/tutorials/response-streaming/)、[markdown-it 15.0.3](https://www.npmjs.com/package/markdown-it/v/15.0.3)、[DOMPurify 3.4.17](https://www.npmjs.com/package/dompurify/v/3.4.17)。以下保留早期阶段依赖演进，当前POM及锁文件为准。

2026-10-23第七阶段：analytics新增内部campus-security及Boot管理的spring-jdbc，AOP复用现有Spring DefaultPointcutAdvisor（[官方Advisor说明](https://docs.spring.io/spring-framework/reference/core/aop/schema.html)），未增加AspectJ或新第三方版本。前端按需使用既有ECharts 6.1.0，zrender/图表引擎分包；Maven Enforcer依赖收敛、external-sdks完整回归与发布包均通过。LangChain4j/MinIO/Pinecone及npm锁文件版本不变。

2026-10-23第六阶段：营销草稿、交互预算及公开来源元数据全部复用既有依赖，未修改POM或前端锁文件版本。Java17 + external-sdks完整验证及实际LangChain4j聊天/独立Embedding/Pinecone Documents元数据验收通过。协议可追加普通文档字段，无须修改索引schema，参见[Pinecone官方Documents说明](https://www.pinecone.io/learn/pinecone-documents-api/)。阶段结果见STAGE6.md。

核对日期：2026-10-21。实际版本由 `backend/pom.xml`、子 POM 与 `frontend/package-lock.json` 固定。版本是此次实际解析、编译和基础运行测试通过的组合，不表示未来任意升级可以兼容。

## 后端

| 依赖 | 固定版本或管理方式 | 说明 |
| --- | --- | --- |
| JDK | 17 | Enforcer 限定 17，避免误用其他 JDK |
| Spring Boot | 3.5.16 | 父 POM 管理 Spring、Security、Jackson、MySQL、Flyway 等 |
| MyBatis-Plus BOM | 3.5.17 | Boot3 starter；分页单独引入 mybatis-plus-jsqlparser |
| springdoc | 2.8.17 | Boot 3.5 对应 2.8.x；Swagger annotations 跟随它 |
| Knife4j UI | 4.5.0 | 官方 `com.github.xiaoymin:knife4j-openapi3-ui` |
| LangChain4j BOM | 1.22.0 | langchain4j 与 open-ai 1.22.0；pinecone 1.22.0-beta32 |
| MinIO | 8.6.0 | 仅 external-sdks profile 加入 |
| OkHttp BOM | 5.1.0 | 与 MinIO POM 对齐，统一 Pinecone 的 logging-interceptor，并引入 okhttp-jvm |
| Kotlin runtime | 2.2.0 | 与 OkHttp JVM 5.1.0 POM 对齐，覆盖 Boot 的 1.9.25 |
| JWT | Spring Security OAuth2 Resource Server / JOSE | 跟随 Boot，不另加 JJWT 重复栈 |
| WebSocket | spring-boot-starter-websocket，Boot 3.5.16管理 | 不另行固定Spring组件版本；第三阶段真实连接验证通过 |
| ZXing core | 3.5.4 | 第三阶段生成核销SVG二维码，重新解码验收；不引入桌面图片库 |

LangChain4j 核心/供应商/集成模块不保证同号。Pinecone 在此 BOM 中是 beta32，这是实际发布的坐标，不能机械改成纯 `1.22.0`。未引入 LangChain4j Boot starter，因为本阶段没有模型配置，不需要它自动创建 Bean；将来使用 starter 必须选 Boot 3 变体并跟随 BOM，禁止引入 `spring-boot4-starter`。

当前 Maven 使用本机既有 Aliyun Central 镜像成功下载。工程没有修改用户 settings.xml，没有加入私服或 SNAPSHOT 仓库。正式包与对应 POM 均已解析，SDK 同时类加载验证在 Java 17 下执行。

## 已复现并修复的问题

### Knife4j 增强插件

官方 `knife4j-openapi3-jakarta-spring-boot-starter:4.5.0` 的增强 customizer 在 springdoc 2.8.17 下调用 `SpringDocConfigProperties.getGroupConfigs()` 时触发 `NoSuchMethodError`。仅编译无法发现，实际请求 `/v3/api-docs` 才暴露。

现在直接组合 **Knife4j 官方 UI + springdoc webmvc starter**，不加载增强 customizer。`/doc.html` 与 `/v3/api-docs` 已有运行测试。接口仍可通过 Knife4j 调试，但不提供该 starter 的增强排序、扩展配置等功能。不要未经测试换回 starter，也不要混用第三方 fork 坐标。

### MinIO 与 OkHttp

MinIO 8.6.0 的 POM 使用 OkHttp 5.1.0，Pinecone 客户端传递引入 4.12.0 的 logging-interceptor。通过 OkHttp BOM 将其统一到 5.1.0。另外 OkHttp 5 的 `okhttp` 包是多平台元数据，Maven 需要显式 `okhttp-jvm:5.1.0` 才有 JVM 类。未加入时 MinioClient.builder() 实测触发 `NoClassDefFoundError: okhttp3/Callback`。

OkHttp JVM 5.1.0 需要 Kotlin 2.2.0，父 POM 覆盖 Boot 默认的 Kotlin 1.9.25。external-sdks profile 引入 okhttp-jvm；测试构建无网络 MinIO client、实例化 HTTP 日志拦截器并核对 Kotlin runtime，防止只下载 JAR 却缺运行类。

## 前端

固定 Vue 3.5.44、Vue Router 4.6.4、Pinia 3.0.4、Vite 6.4.4、Vue 插件 5.2.4、TypeScript 5.9.3、vue-tsc 3.3.13、Element Plus 2.14.7、ECharts 6.1.0。此次 Node 22.14.0 已通过安装、类型检查及生产构建。使用 npm ci 重现锁文件，不能随手改成 latest 或删除锁文件。

## 官方核对入口

- [Spring Boot 3.5 环境要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [springdoc Boot3 兼容矩阵](https://springdoc.org/v2/)
- [Knife4j 官方说明](https://doc.xiaominfo.com/guide/useful.html)
- [MyBatis-Plus 安装说明](https://baomidou.com/getting-started/)
- [LangChain4j 入门与 Java 要求](https://docs.langchain4j.dev/get-started/)
- [LangChain4j Spring Boot 集成](https://docs.langchain4j.dev/tutorials/spring-boot-integration/)
- [LangChain4j Pinecone 集成](https://docs.langchain4j.dev/integrations/embedding-stores/pinecone/)
- [LangChain4j BOM POM](https://repo.maven.apache.org/maven2/dev/langchain4j/langchain4j-bom/1.22.0/langchain4j-bom-1.22.0.pom)
- [MinIO Java SDK](https://github.com/minio/minio-java)
- [MinIO 8.6.0 POM](https://repo.maven.apache.org/maven2/io/minio/minio/8.6.0/minio-8.6.0.pom)

## 升级验收

2026-10-23清单修复后，LangChain4j core/open-ai 1.22.0已进入普通依赖以支持真实模型调用；MinIO 8.6.0和langchain4j-pinecone 1.22.0-beta32仍为external-sdks兼容预检。新增直接客户端采用Boot 3.5.16管理的lettuce-core 6.6.0.RELEASE、mongodb-driver-sync 5.5.2、amqp-client 5.25.0，未引入会自动连接默认服务的starter；MongoAutoConfiguration显式排除，只有私有开关启用自有客户端。

当前用户Pinecone索引使用2026-07 Documents API，旧Java向量SDK不支持该数据面。本项目通过JDK HttpClient和端口实现固定版本REST协议，不升级/降级既有SDK来掩盖接口不兼容；独立Embedding维度与命名向量字段配置校验，并已真实写入、查询、删除。依赖收敛、类加载、真实资源及完整业务验证见VALIDATION.md。本文后面的“仅SDK预检”描述属于当时阶段历史。

执行 `mvn verify`、`mvn -Pexternal-sdks verify`、`mvn -Pexternal-sdks dependency:tree`，重点检查 springdoc/Swagger、Jackson、OkHttp/Okio/Kotlin、gRPC/Protobuf/Netty 是否冲突。父 POM 已对 springdoc/Swagger/LangChain4j/OkHttp/Kotlin 添加 dependencyConvergence 检查。真实资源可用后再补充连接、检索、签名上传测试；当前验证不证明远程服务版本或索引配置正确。


第一阶段新增 campus-user → campus-security 与 Spring JDBC 依赖，均沿用现有父工程管理，没有新增第三方版本或修改锁文件。浏览器检查使用运行时提供的 Playwright 1.62.1，不是应用依赖；测试脚本与 Mock 不进入前端 bundle。


第二阶段新增内部 campus-file 模块，并为 merchant/admin/file 复用 security、integration 与父工程管理的 Spring JDBC/事务依赖；未修改任何第三方版本或前端锁文件。再次执行 external-sdks + 真实 MySQL 全套验证通过。MinIO 仍只有端口和不可用实现，不会因构建 SDK profile 而自动连接；真实适配器需遵守 STAGE2.md 的封存和私有签名合同。

第三阶段新增内部campus-notification、Boot管理的WebSocket starter和ZXing core 3.5.4（父POM集中版本）；沿用Java17和原有SDK版本，没有修改前端锁文件或引入Redis/MQ自动配置。已执行external-sdks全套兼容回归，真实Tomcat WebSocket/JWT/session检查及二维码重新解码通过。

新增依赖核对：[Spring Boot 3.5 WebSocket说明](https://docs.spring.io/spring-boot/3.5/reference/messaging/websockets.html)、[Spring WebSocket服务端与Origin配置](https://docs.spring.io/spring-framework/reference/web/websocket/server.html)、[ZXing官方发布](https://github.com/zxing/zxing/releases/tag/zxing-3.5.4)。浏览器测试按[Playwright官方WebSocketRoute接口](https://playwright.dev/docs/api/class-websocketroute)提供仅测试的Mock，生产代码使用浏览器原生WebSocket/可选BarcodeDetector。

Google Java Format 1.24.0仅作为.local内的独立开发格式工具，未加入Maven应用依赖或构建插件；Python生成脚本同样不参与项目构建/运行。源码以整理后的Java/TS/Vue为准，不能回放历史生成器。

## 第四阶段支付宝 SDK 与重复类处理

支付宝官方Java v2 SDK固定为 `com.alipay.sdk:alipay-sdk-java:4.40.934.ALL`，父POM统一属性，不使用动态版本。该版本来自[官方v2说明](https://github.com/alipay/alipay-sdk-java-all/blob/master/v2/README.md)并通过Maven实际下载/编译；当前使用的page.pay/query/close/refund以及RSA2验签在Java17下测试。它是普通integration依赖，默认不开启远端客户端；MinIO/LangChain4j/Pinecone仍在external-sdks profile，原版本没有改动。

SDK传递依赖曾同时带入 `bcprov-jdk15on:1.62`、`commons-logging:1.1.1`、`dom4j:dom4j:1.6.1`。与已有BC jdk18on和Spring JCL存在同名类，不能只看Maven dependencyConvergence同坐标检查就认定兼容。已排除这三项，明确使用 `bcprov-jdk18on:1.81`（沿用原SDK组合已解析的版本）、`org.dom4j:dom4j:2.1.4`、Spring自带spring-jcl；保留官方SDK的fastjson `1.2.83_noneautotype`，没有开启AutoType。

OkHttp继续由5.1.0 BOM统一。因为支付宝SDK普通构建也需要HTTP运行类，`okhttp-jvm:5.1.0`从仅external-sdks提升为普通依赖，Kotlin2.2.0保持不变。默认构建的本地支付SDK测试实际构造OkHttpClient，不依靠profile偶然补类。

`AlipaySandboxAdapterTest`用临时2048位RSA密钥实测签名URL、RSA2回调验签、篡改/app/seller拒绝、生产网关拒绝，以及BC/JCL/dom4j关键class资源各只有一份。external-sdks全套再验证与MinIO/AI/Pinecone共同加载；所有测试均不调用远端服务。固定版本不代表已完成全面供应链漏洞审计，后续升级仍需整套回归。

核对来源：[支付宝官方配置与沙箱网关](https://github.com/alipay/alipay-sdk-java-all/blob/master/v2/src/main/java/com/alipay/api/AlipayConfig.java)、[官方RSA2签名工具](https://github.com/alipay/alipay-sdk-java-all/blob/master/v2/src/main/java/com/alipay/api/internal/util/AlipaySignature.java)、[Bouncy Castle发布](https://github.com/bcgit/bc-java/discussions/2098)、[dom4j发布](https://github.com/dom4j/dom4j/releases/tag/version-2.1.4)。

## 第五阶段依赖边界

community/search复用内部security/integration和Boot管理的Spring JDBC，integration显式引用Boot管理的spring-tx以支持搜索事件的MANDATORY事务约束；未新增第三方版本或修改npm锁文件。ES适配器使用Java17内置HttpClient和现有Jackson，没有引入Spring Data Elasticsearch、ES SDK、MongoDB或地图starter。原Knife4j UI/springdoc、MinIO/LangChain4j/Pinecone/OkHttp组合保持不变。

external-sdks完整103项测试和dependency:tree均通过。新增3项ES本地HTTP协议测试核对版本/IK/映射拒绝、别名、外部版本墓碑和查询白名单；这不等价于真实ES节点集成测试。选择ES实际部署版本后须配置完整8.x.y版本及同版本IK，不能把协议测试用8.17.3当成部署推荐；接入步骤和官方依据见 [SEARCH-SETUP.md](SEARCH-SETUP.md)。MongoDB/地图尚未接入，不编造相应Maven依赖兼容结论。
