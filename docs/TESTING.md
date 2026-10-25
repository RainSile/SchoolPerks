# 构建与测试

## 前端

在前端分支执行：

```sh
cd frontend
npm ci
npm run typecheck
npm run build
```

可选scripts/*-browser-check.mjs使用Playwright和Chrome/Chromium。通过CAMPUS_PLAYWRIGHT_MODULE与CAMPUS_BROWSER_EXECUTABLE配置安装位置。脚本创建自己的临时服务器，以HTTP/WebSocket夹具验证界面合同，不连接真实数据库。夹具配置使用独立Vite缓存，避免并行检查互相改变依赖缓存。

涵盖账号与三端跳转、商家审核、优惠核销、交易、社区／地图、RBAC、助手／营销、看板／审计、样式与字体。style-browser-check另验证静态标题字体加载和带子集外汉字的动态帖子标题使用原生字体。

## 后端

在后端分支执行：

```sh
cd backend
mvn -B verify
mvn -B -Pexternal-sdks verify
```

默认测试不连接MySQL或真实供应商。显式的数据库与外部检查需要匹配的环境开关和私有配置；在运行前阅读对应测试类的EnabledIfEnvironmentVariable与清理策略。不得给共享生产库开启测试或压测。

外部SDK profile检查依赖收敛／兼容性，不意味着拥有真实资源；真实模型、存储、地图、支付仍由单独配置和验收决定。

根目录scripts/verify-stage*-package.ps1验证已有JAR的skeleton启动、文档和不可用能力行为。需要JAVA_HOME指向JDK17和已打包的backend/campus-boot/target JAR；脚本只停止自己创建的临时进程。

## 结果解释

源码构建、UI夹具、真实数据库、外部供应商联调是不同层次。一个检查返回200或界面显示成功，不等于真实支付、短信、存储及AI都已配置。公开仓库不附带个人环境验收日志、演示账号或数据库快照。

<!-- campus-release-20261026-start -->
## 2026-10-26 更新

新增助手流式/Markdown、明确写允许、未知结果和幂等重放、安全上下文及账号隔离验证。最新脱敏报告见BUSINESS-TEST-RESULTS.md与ASSISTANT-TEST-RESULTS.md；脚本支持独立阶段证据目录，汇总不混入更早的Surefire报告。
<!-- campus-release-20261026-end -->
