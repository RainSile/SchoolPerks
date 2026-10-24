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
