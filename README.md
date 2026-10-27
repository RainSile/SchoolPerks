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

只公开工程说明和脱敏测试汇总；个人文稿、学校材料、私人待办、凭据、本机服务清单、演示账号、原始日志和数据库快照均不包含。图片及字体的许可、来源和素材提示词随前端源码保留。

## 分别获取工程

```sh
git clone --branch campus-frontend --single-branch https://github.com/RainSile/SchoolPerks.git schoolperks-web
git clone --branch campus-backend --single-branch https://github.com/RainSile/SchoolPerks.git schoolperks-api
git clone --branch campus-doc --single-branch https://github.com/RainSile/SchoolPerks.git schoolperks-docs
```

同一工作目录切换分支时，请先保存未提交修改。分支各自独立，不需要把三种目录相互合并后才能构建。

<!-- campus-release-20261026-start -->
## 业务与助手验证文档

- [测试阶段清单](docs/BUSINESS-TEST-PLAN.md)
- [详细结果与最新修复](docs/BUSINESS-TEST-RESULTS.md)
- [业务缺陷台账](docs/BUSINESS-TEST-BUGS.md)
- [业务用例索引](docs/BUSINESS-TEST-CASES.md)
- [逐接口覆盖](docs/BUSINESS-API-COVERAGE.md)
- [前端路由覆盖](docs/BUSINESS-FRONTEND-COVERAGE.md)
- [助手 API 与权限](docs/ASSISTANT-BUSINESS-APIS.md)
- [助手测试报告](docs/ASSISTANT-TEST-RESULTS.md)
- [助手缺陷台账](docs/ASSISTANT-TEST-BUGS.md)

报告只公开脱敏技术汇总，原始日志、数据库快照、账号、密钥、本机配置、学术材料和私人待办均不公开。历史首轮与最新修复状态在报告中分别注明。
<!-- campus-release-20261026-end -->

<!-- campus-minio-20261027-start -->
## 私有对象存储专项

[MinIO部署、配置与真实业务验证](docs/MINIO-SETUP.md)记录轻量部署与文件权限。实际存储专项5项及相关回归共30项通过，56表摘要一致；不代替全部业务终验。部署模板及浏览器专项脚本位于campus-backend分支，原始日志、凭据和个人材料不发布。
<!-- campus-minio-20261027-end -->

<!-- campus-alipay-20261027-start -->
## 支付宝沙箱配置验证

[配置、验证与待启用条件](docs/ALIPAY-SANDBOX-SETUP.md)：2项真实只读检查、33项相关回归通过。凭据留在私有环境；公网HTTPS后端通知入口尚待部署，完整付款/通知/退款仍待验收。
<!-- campus-alipay-20261027-end -->
