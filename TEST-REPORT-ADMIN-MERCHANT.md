# 管理端与商家端后端复测报告

## 2026-11-09 合并后云端部署验收

已将视觉实验分支完整合并至 campus-frontend，并删除其本地及远端分支。部署源码：前端 ac1ba1b，后端 f1aada2；后续仅文档提交不改变部署代码。下节为部署前修复记录，其中“尚未部署”是当时状态。

- 合并后10组浏览器夹具回归、1440/390/320px视觉检查、类型检查/构建通过；实际前后端14段联调再次通过，本机57表数据摘要一致。上一轮119项业务检查继续作为其范围内的修复证据。
- 后端使用Java17及external-sdks构建；默认构建281项中执行37、按环境条件跳过244，0失败/错误。跳过项不计为本次通过，不与此前显式启用的119项重复累加。
- 云端新发布已启动，Java、网站、MySQL、隧道四项服务active，健康UP；后端JAR SHA-256为3888487aa74266cc47a4f439a06b6a7844332659684804c838ad33508ca2119b，前端归档为a858f619f8997c3362c5d4b27f3df9bb8f4cf4e378098418a85cfd3cc7a940ea。
- 切换前完成私有数据库备份，保留旧发布；原服务私有配置摘要未变。V14成功，56→57张基表，新补交表为空，Flyway增加一条成功记录。原业务表除支付调度外摘要相同；逐列核对5条支付记录，仅3条next_check_at变化，订单关联、供应商编号、状态及创建时间未变。未恢复覆盖数据库。
- 正常TLS校验下16项公网接口检查通过；9项真实浏览器检查通过：三种屏宽新素材与统一图标、三端密码登录、学生访问管理审核API被拒、商家本人历史与资质提示、管理员队列读取。线上当前无待审评论，未执行审批写入；完整评论审批在本机真实后端已验证，不能把线上队列读取算作线上审批成功。
- 桌面/手机页脚图片加载及布局通过，人物33%、背景13%保持；无页面脚本错误或横向溢出。本次登录会话均撤销；会话/审计正常留存，不宣称整个云库零写入。没有新增优惠、帖子、订单或审批，短信发送0，未重复调用模型、实际支付或退款。

## 2026-11-09 修复验收

当前结论：**本轮三项缺陷已修复并通过本地回归**。作业分支为 campus-frontend 与 campus-backend，未修改实验分支，尚未重新部署云端。不宣称全部业务分支或外部供应商已重新验收。

| 缺陷 | 修复与验证 |
| --- | --- |
| AM-UI-001 评论审核点击无明显反馈 | 保留必填原因；缺失时提示并滚动聚焦输入框，服务端错误定位反馈。1440×600、390×844 的夹具及真实前后端均通过：空原因不发送 PATCH；补填后仅一次提交，数据库为 APPROVED，学生端刷新后公开可见。 |
| AM-UI-002 商家无法查看本人申请历史 | 历史页改为登录共享路由；后端两个历史 GET 允许已登录账号，仍按申请人隔离，其他人详情 404；新入驻 POST 仍按原 USER 准入。实际工作台链接与升级后的账号历史查询通过。 |
| AM-DATA-001 旧门店缺资质但前端开放建券 | 工作台返回 qualificationReady 与 pendingQualificationRequestId；缺资质时隐藏优惠/套餐创建表单并引导补交。本人 READY 资质提交后，由管理员审核绑定原门店；不新增门店、不绕过资格检查、不自动解除暂停。补交至真实优惠发布/领取/核销全链通过。 |

### 本次验证

- 后端最终一次执行 119 项，0 失败、0 错误、0 跳过。其中 108 项 MySQL/MockMvc、10 项单元或基础合同、1 项实际浏览器联调；该浏览器用例包含 14 段旅程，不能再加作 14 个 JUnit 用例。
- 明细：商家18、优惠18、社区19、交易21、RBAC6、看板12、业务读取及权限矩阵14、权限单元3、基础合同7、浏览器联调1。
- 前端 10 组 F 回归：merchant、coupon、trade、community、analytics、rbac-business、auth、password-map、admin-comment-feedback、merchant-history；全部通过。TypeScript 检查与生产构建通过。
- E 联调：真实密码登录及分端；资质补交/管理员审核；商家建券/送审、管理员审批、发布、学生领取及商家核销并持久化；跨端拒绝；套餐下单取消；帖子审批/点赞/XSS；两尺寸评论审批；附近查询及两端看板。
- 新资质负例：非店主的经理/收银员 403、跨店及他人文件 404、未封存文件 409、旧版本 409、重复申请/审批 409；旧申请驳回后可重提，暂停状态保留。审计故障时资质绑定、版本、审核状态/记录均回滚。
- 本机迁移 V14 首次执行只新增空关系表及 Flyway 记录；此后最终回归前后 57 张基表行数及逐行摘要差异为 0。未清库、未重置自增值。

### 接口与发布注意

新增 POST /api/v1/merchants/{id}/qualification-requests，body 为 version、qualificationFileId；成功 201，Location 指向现有 merchant-applications 资源，带 OpenAPI/Knife4j 注解。实际店主提交；具有既有跨业务权限的管理员仍需关联实际店主的 READY 文件，不能用管理员文件代替店主资质。审批复用管理员 merchant-applications/{id}/reviews。申请增加 purpose=ONBOARDING 或 QUALIFICATION；补交申请的 merchantId 指向既有门店。

V14__merchant_qualification_supplements.sql 为增量迁移，保留原入驻表的一店一原始申请唯一约束，补交关系独立存储。V1–V14 不可修改，后续从 V15 起。部署时先备份目标库，交由原 Flyway 执行 V14，再发布配套前端；无需新增环境变量或依赖。

这些测试使用随机本机端口与专属夹具。存储和支付端口在本次 D/E 中显式使用替身；真实 MinIO、支付宝、短信、AI 不属于本次重新验收，发送短信 0 条。原始运行日志、凭据、令牌、真实附件不提交 Git。旧演示店不会自动伪造资质，实际使用时仍须店主上传真实文件并由管理员审核。

### 复验入口

后端 scripts/test-business-backend.ps1 支持 -TestSelection。选取本报告列出的测试类，BusinessReadChainsMySqlTest 使用 #business*，BusinessLiveJourneyMySqlTest 使用 #businessRealBrowserCouponJourney，需显式本机测试库授权配置。前后端为分离检出时，CAMPUS_FRONTEND_PROJECT_ROOT 指向含 frontend/ 的配对前端仓库；联调还需要 Node、Playwright、Chrome，可用 CAMPUS_PLAYWRIGHT_MODULE 与 CAMPUS_BROWSER_EXECUTABLE 配置。独立前端回归直接 node frontend/scripts/<名称>-browser-check.mjs；不需要真实账号。

## 修复前复测记录（历史）

日期：2026-11-09。分支campus-backend，基线f9581e0。配对前端campus-frontend/3563b36。后端检查通过，但**整体验收未通过**，真实前端有缺陷且商家完整链路受阻。

| 测试类 | 数量 |
| --- | --- |
| MerchantStageTwoMySqlTest | 原14+新增1 |
| CouponStageThreeMySqlTest | 18 |
| CommunityStageFiveMySqlTest | 19 |
| TradeStageFourMySqlTest | 21 |
| RbacIsolationMySqlTest | 6 |
| AnalyticsStageSevenMySqlTest | 12 |
| BusinessReadChainsMySqlTest（business*） | 14 |
| CurrentActorBusinessPermissionTest | 3 |
| ScaffoldContractTest | 7 |
| 合计 | 115 |

共105项真实MySQL检查和10项单元/基础合同，0失败、0错误、0跳过；支付、短信等供应商端口在D层测试中为替身或关闭。首轮114项、追加1项分别执行，两轮56表摘要差异均0。首轮有1266条HTTP轨迹、159个方法+模板组合；追加7条。请求数与路由数不代表全部业务分支覆盖。

新增approvedLegacyStoreWithoutQualificationCannotCreateCouponOrChangeStock只改本测试事务新建门店夹具，确认无资质门店即使APPROVED仍返回409且不创建优惠，随后回滚。不要为了让演示数据能操作而放宽资质所有权与就绪校验。

真实评论：空原因没有PATCH；补填后PATCH200，APPROVED且公开可见，刷新保持；隐藏后HIDDEN且不再公开。旧版本409，普通用户、两店店主、店长、收银员读取管理审核队列均403。真实演示店建券409/QUALIFICATION_REQUIRED，完整发布核销链受阻。MinIO上传确认通过一次；MERCHANT账号新入驻403为当前USER准入及测试前置不符，不计产品缺陷，不把后续链记作通过。

本轮只新增回归和报告，没有修复实现、改部署配置或停数据库/中间件。没有主动调用短信、模型、支付/退款。云端有专属测试写入和审计，不宣称零变化。原始凭据、令牌、签名URL、真实评论及运行截图不提交。

可用现有scripts/test-business-backend.ps1在授权配置下选取上述类；追加门槛测试可单独选择MerchantStageTwoMySqlTest#approvedLegacyStoreWithoutQualificationCannotCreateCouponOrChangeStock。AM-UI-001、AM-UI-002、AM-DATA-001统一修复后，还须复测失败断言和真实商家全链路。
