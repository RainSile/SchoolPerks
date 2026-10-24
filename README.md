# SchoolPerks 工程技术文档

本分支只维护公开工程文档。代码分别位于 [前端分支](https://github.com/RainSile/SchoolPerks/tree/campus-frontend) 的 frontend/ 和 [后端分支](https://github.com/RainSile/SchoolPerks/tree/campus-backend) 的 backend/。

| 文档 | 内容 |
| --- | --- |
| [架构](docs/ARCHITECTURE.md) | 模块职责、边界、数据与事件 |
| [接口合同](docs/API-CONTRACTS.md) | REST 资源、状态码、权限和幂等 |
| [数据库](docs/DATABASE.md) | 迁移、表约束和一致性 |
| [依赖](docs/DEPENDENCIES.md) | 项目锁定版本与兼容记录 |
| [配置与运行](docs/CONFIGURATION.md) | 环境变量、profile、数据库和外部能力 |
| [业务规则](docs/BUSINESS-RULES.md) | 三端、优惠、交易、内容和AI边界 |
| [前端设计](docs/FRONTEND-DESIGN.md) | 页面、字体、分层页脚和响应式 |
| [测试](docs/TESTING.md) | 构建、显式集成测试与UI夹具 |
| [搜索接入](docs/SEARCH-SETUP.md) | ES + IK、投影与地图合同 |
| [搜索映射](docs/search-index.json) | 手动创建索引时使用的映射 |

文档按现有源码整理，不代表任何部署实例已配置全部服务。实际接口以运行实例的 /v3/api-docs 为准，依赖以 POM 和 npm 锁文件为准。数据库文档保留迁移演进说明，不提供数据导出或账号清单。

未包含个人文稿、学校材料、私人待办、凭据、本机服务清单、演示账号、日志、截图或内部联调验收记录。图片及字体的许可、来源和素材提示词随前端源码保留。

## 分别获取工程

```sh
git clone --branch campus-frontend --single-branch https://github.com/RainSile/SchoolPerks.git schoolperks-web
git clone --branch campus-backend --single-branch https://github.com/RainSile/SchoolPerks.git schoolperks-api
git clone --branch campus-doc --single-branch https://github.com/RainSile/SchoolPerks.git schoolperks-docs
```

同一工作目录切换分支时，请先保存未提交修改。分支各自独立，不需要把三种目录相互合并后才能构建。
