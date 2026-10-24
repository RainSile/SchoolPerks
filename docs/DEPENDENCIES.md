# 依赖版本与兼容性

版本以[后端父POM](https://github.com/RainSile/SchoolPerks/blob/campus-backend/backend/pom.xml)、各模块POM和[前端锁文件](https://github.com/RainSile/SchoolPerks/blob/campus-frontend/frontend/package-lock.json)为准。以下是本工程固定组合，不表示供应商最新版本，也不表示任意升级后仍兼容。

## 后端

| 依赖 | 版本／管理方式 |
| --- | --- |
| JDK | 17，Maven Enforcer检查 |
| Spring Boot | 3.5.16 |
| MyBatis-Plus BOM | 3.5.17，Boot3 starter及分页组件 |
| springdoc | 2.8.17 |
| Knife4j UI | 4.5.0，knife4j-openapi3-ui |
| LangChain4j BOM | 1.22.0 |
| LangChain4j OpenAI | 1.22.0 |
| LangChain4j Pinecone | 1.22.0-beta32，由对应BOM组合约束 |
| MinIO | 8.6.0 |
| OkHttp BOM | 5.1.0 |
| Kotlin runtime | 2.2.0 |
| ZXing core | 3.5.4 |
| 支付宝SDK | 4.40.934.ALL |
| JWT／JOSE、MySQL、Flyway、WebSocket、Spring JDBC | 由Spring Boot依赖管理 |

## 必须保持的组合

- Spring Boot3使用Jakarta接口，不能混入Boot2 starter、javax Servlet/Validation或Springfox。
- Knife4j只使用UI资源模块，避免通过旧starter重新引入不匹配的springdoc。Swagger注解、OpenAPI与UI需要一起核对。
- LangChain4j采用BOM；稳定模块和beta供应商模块版本可能不同，不要按名称把所有artifact强改成同一个后缀。
- MinIO与Pinecone相关传递依赖通过OkHttp BOM统一。OkHttp JVM5.1.0需要匹配Kotlin2.2.0，不能直接接受旧的Kotlin1.9管理值。
- 不为同一外部能力无依据增加多个重复SDK。外部SDK在external-sdks profile中加入，业务通过端口调用，不把供应商SDK类型散入controller/service。

## 前端

| 依赖 | 版本 |
| --- | --- |
| Node.js | 至少22.12.0 |
| Vue | 3.5.44 |
| Vue Router | 4.6.4 |
| Pinia | 3.0.4 |
| Vite | 6.4.4 |
| TypeScript | 5.9.3 |
| Element Plus | 2.14.7 |
| ECharts | 6.1.0 |
| Vue TSC | 3.3.13 |

使用npm ci消费锁文件。图表引擎和渲染器按需分包；标题字体为自托管静态WOFF子集，不增加运行时npm依赖。字体许可、来源和派生命名见前端public/fonts。

## 修改后的检查

```sh
mvn -B verify
mvn -B -Pexternal-sdks verify
mvn -B -Pexternal-sdks dependency:tree
```

前端执行npm ci、npm run build，并按变更选择浏览器合同检查。依赖解析／类加载、数据库测试与真实供应商联调是不同验证层次；不能把profile构建通过写成真实服务已接通。升级应先对照供应商官方文档，再验证本工程的完整组合。
