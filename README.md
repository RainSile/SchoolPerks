# SchoolPerks 前端

Vue 3 + TypeScript + Vite 的校园周边优惠平台界面，包含用户、商家、管理员三端。登录后根据服务端账号类型进入对应端，菜单与资源访问同时受服务端权限校验。

## 仓库分支

- [campus-frontend](https://github.com/RainSile/SchoolPerks/tree/campus-frontend)：本前端工程，位于 `frontend/`。
- [campus-backend](https://github.com/RainSile/SchoolPerks/tree/campus-backend)：Java 后端，位于 `backend/`。
- [campus-doc](https://github.com/RainSile/SchoolPerks/tree/campus-doc)：架构、接口、数据库、配置与前端设计文档。

## 环境与启动

需要 Node.js 22.12.0 或更新版本、npm。版本以 `frontend/package.json` 和 `frontend/package-lock.json` 为准。

从仓库根目录执行：

```sh
cd frontend
npm ci
cp .env.example .env.local
npm run dev
```

进入 `frontend/` 后，Windows PowerShell 可用 `Copy-Item .env.example .env.local`。默认地址为 `http://127.0.0.1:5173`；后端默认 `http://127.0.0.1:8080`。在未提交的 `.env.local` 中设置：

```dotenv
WEB_PORT=5173
CAMPUS_API_PROXY_TARGET=http://127.0.0.1:8080
VITE_API_BASE_URL=/api/v1
```

`CAMPUS_API_PROXY_TARGET` 供开发服务器代理使用，包括 WebSocket；生产环境须由反向代理将 `/api` 转发给后端。不要把数据库密码、AI Key、存储密钥放入 `VITE_*`，这些变量会进入浏览器代码。地图公开 JS Key 由后端能力接口提供，安全码不交给浏览器。

## 构建与校验

```sh
npm run typecheck
npm run build
npm run preview
```

生产文件输出到 `frontend/dist/`，不纳入 Git。预览命令只用于检查构建结果。

`frontend/scripts/*-browser-check.mjs` 是独立 HTTP/WebSocket 夹具检查，不是业务后端。运行这些可选脚本前需自行准备 Playwright 和 Chrome/Chromium，并通过 `CAMPUS_PLAYWRIGHT_MODULE`、`CAMPUS_BROWSER_EXECUTABLE` 指定模块及浏览器位置。脚本结束后关闭自己创建的临时服务。

## 主要目录

- `frontend/src/campus-features/`：账号、商家、优惠、交易、社区、搜索、地图、助手与看板页面。
- `frontend/src/campus-layouts/`：三端布局与权限菜单。
- `frontend/src/campus-shared/`：请求封装、分页、图标、装饰与分层页脚。
- `frontend/src/campus-styles/`：设计变量、控件、字体与页面布局。
- `frontend/public/fonts/`：本地标题字体子集、来源说明及 OFL 许可。

学生端采用蓝白校园布告栏风格，静态标题使用圆润字体，正文、金额和用户发布标题保持清晰的系统无衬线字体。页脚使用独立全幅景物背景（13%）和人物层（24%），顶部有柔和过渡；图片生成提示词保存在各素材旁的 JSON 文件中。字体来源及派生说明见 [字体记录](frontend/public/fonts/README.md)。

所有交易金额、库存、订单状态及账号身份以服务端结果为准。真实供应商不可用时保留不可用提示，不把测试夹具当成已接通的服务。

## 不纳入仓库

本机配置、凭据、日志、缓存、构建产物、个人材料和私人演示数据。示例配置只列变量与非敏感默认值。

## 页面预览

学生端、商家端、管理端与手机布局截图见 [项目首页](https://github.com/RainSile/SchoolPerks/tree/main#页面预览)。截图采用独立演示夹具，不含真实账号或业务数据。

<!-- campus-release-20261026-start -->
## 流式助手与交互验证

助手支持真正的 SSE 增量、Markdown 标题/列表/表格/代码及安全链接、停止输出、账号切换清除、每条消息明确允许业务写操作。HTML 与远程图片禁用，渲染经过 DOMPurify。前端路由仍按真实账号类型进入独立端。

新增依赖精确锁定于 package.json/package-lock.json；11组浏览器检查和生产构建通过。测试夹具与真实供应商验收分别记录，不把模拟响应当作真实支付/短信结果。

[助手业务 API 与权限](https://github.com/RainSile/SchoolPerks/blob/campus-doc/docs/ASSISTANT-BUSINESS-APIS.md) · [业务测试与修复报告](https://github.com/RainSile/SchoolPerks/blob/campus-doc/docs/BUSINESS-TEST-RESULTS.md)
<!-- campus-release-20261026-end -->

<!-- campus-cloud-20261028-start -->
## 云端部署与沙箱回调验收

前后端及独立 MySQL 已部署，原中间件保留并通过配置化加密连接访问。真实 0.01 元沙箱付款、HTTPS 签名回调与正常业务退款已通过，临时验收权限已清理；本机源库 56 表摘要未改变。

[部署与迁移](https://github.com/RainSile/SchoolPerks/blob/campus-doc/docs/CLOUD-DEPLOYMENT.md) · [部署验收与覆盖边界](https://github.com/RainSile/SchoolPerks/blob/campus-doc/docs/CLOUD-ACCEPTANCE.md) · [实际支付及退款](https://github.com/RainSile/SchoolPerks/blob/campus-doc/docs/ALIPAY-CLOUD-ACCEPTANCE.md)

网页收银台账号密码方式的供应商安全组件异常、真实短信待接入仍单独记录。付款由真实沙箱钱包完成，不以接口配置成功代替付款。服务器地址、密钥、账号及数据库备份均不进入源码；业务参数通过私有配置修改。
<!-- campus-cloud-20261028-end -->
