# 智能助手业务 API 与权限

本表由实际注册的业务 Controller 生成，按 HTTP 方法计数；不是承诺所有外部供应商已经配置。

当前目录共 **151 个业务接口**。运行时以鉴权后的 `GET /api/v1/assistant/business-apis` 为准；工具执行前重新校验目录权限，随后携带本人 JWT 调用真实 API。

## 权限和身份边界

- 学生：USER 与 SHARED；只能处理本人订单、券包、帖子等私有资源。
- 商家：MERCHANT 与 SHARED；必须是相应门店的有效成员，店主、经理、收银员仍受业务服务中的角色限制。不得调用学生端或管理端工具。
- 平台管理员：具有 `business:manage` 的真实 ADMIN 身份可使用全部目录，仍保留审核状态、版本、库存、不能自审等校验。身份及审计记录保持 ADMIN，不冒充学生或商家。
- 受限管理员：只开放 ADMIN/SHARED 中已授予细分权限的接口，不因 ADMIN 标签自动获得跨端能力。V12 将现有具备 `rbac:manage` 的 ADMIN 角色显式授予 `business:manage`；新角色应明确分配。
- “本人”接口始终表示当前实际账号；管理员也不能读取其他用户的助手会话、会话历史或凭据。跨端能力不等于跨用户会话访问权。

## 多步操作与重试

`business_api_catalog` 按关键词返回当前权限内的接口摘要，按 operationId 返回 path/query/body schema；`execute_business_api` 只接受目录中的 operationId，路径和方法固定在服务端。path/query/body 是 JSON 对象字符串，不能传入 URL、JWT 或指定执行身份。

每条消息默认只读。用户勾选“允许本次业务操作”后，仅执行该消息明确要求的写操作。模型先查询真实资源 ID、版本及状态，按顺序提交，再通过查询确认结果。

MySQL 保存每个 turn 的写操作尝试和脱敏结果。同一幂等键的完成回答不再计费；同一 turn 的已完成写操作复用原结果，结果不确定的操作阻止盲目重放和后续写入。断开输出不会撤销已经提交的业务操作，应先查询实际结果。

## 目录之外及资源限制

登录、验证码、注册、密码/凭据、会话刷新/退出、支付平台回调、地图 SDK 代理、通知长连接及助手递归调用不作为模型业务工具。身份原文、口令、核销秘密、预签名 URL 等返回字段脱敏。

上传文件的实际二进制传输、打开支付收银台、出示核销码需要用户界面配合；工具不能代替真人完成这些界面步骤。短信、对象存储或支付供应商未配置时，真实 API 的不可用状态会原样按安全结构反馈，不能宣称已经完成。

## 流式协议

`POST /api/v1/assistant/conversations/{id}/message-streams`，Bearer 与 Idempotency-Key 必填；请求 `{ "message": "…", "allowWrites": false }`。事件：started（消息 ID）、round（清空当前轮草稿）、delta（增量文本）、tool（工具名）、completed（持久化消息）、error（安全错误码）。

页面通过 fetch 读取真正的 SSE 分片，支持中止、中文 UTF-8 跨片、Markdown 标题/列表/表格/代码/安全链接；禁用 HTML 与远程图片，渲染前使用 DOMPurify 清理。账号切换或失效清除本次草稿和可见历史。JSON 非流式接口继续可用。

## 学生端（51 项）

| 方法 | API | 功能 | 额外权限 / 范围 |
| --- | --- | --- | --- |
| GET | `/api/v1/coupon-claims/{id}` | 查看本人券状态 | 公开查询 / 本人资源校验 |
| POST | `/api/v1/coupon-claims/{id}/redemption-tickets` | 本人生成短时核销二维码 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/coupons` | 分页浏览公开优惠 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/coupons/{id}` | 查看公开优惠详情 | 公开查询 / 本人资源校验 |
| POST | `/api/v1/coupons/{id}/claims` | 同步领取一张优惠券 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/group-purchases/{id}` | 拼团进度，不返回成员身份 | 公开查询 / 本人资源校验 |
| POST | `/api/v1/merchant-applications` | 提交本人商家入驻申请 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/merchant-applications/{id}` | 查询本人入驻申请详情 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/merchants` | 分页查询已审核商家 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/merchants/{id}` | 查询已审核商家的公开详情 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/nearby-merchants` | 按WGS84球面直线距离排序附近已审核商家；不保存用户位置 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/orders` | 本人订单分页 | 公开查询 / 本人资源校验 |
| POST | `/api/v1/orders` | 服务端定价下单，一单一份；Idempotency-Key 必填 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/orders/{id}` | 本人订单详情和不可变金额快照 | 公开查询 / 本人资源校验 |
| POST | `/api/v1/orders/{id}/cancellations` | 取消本人待支付订单；重复取消200，新取消201 | 公开查询 / 本人资源校验 |
| POST | `/api/v1/orders/{id}/payments` | 创建或恢复同一支付宝沙箱支付尝试；未配置503 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/orders/{id}/payments/{paymentId}` | 本人支付尝试状态，不返回签名地址 | 公开查询 / 本人资源校验 |
| POST | `/api/v1/orders/{id}/redemption-codes` | 生成本人已付款订单短期核销码；数据库只存摘要 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/orders/{id}/refunds` | 查询本人订单的退款资源（至多一条） | 公开查询 / 本人资源校验 |
| POST | `/api/v1/orders/{id}/refunds` | 申请本人未核销已付款订单的全额退款，PENDING不代表已退款 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/orders/{id}/refunds/{refundId}` | 查询本人退款及重试状态 | 公开查询 / 本人资源校验 |
| POST | `/api/v1/orders/{id}/refunds/{refundId}/retries` | 本人重试已耗尽自动重试的退款；保留原供应商幂等号 | 公开查询 / 本人资源校验 |
| DELETE | `/api/v1/post-comments/{id}` | 本人删除评论，重复删除幂等 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/post-comments/{id}` | 本人/审核员查看评论，其他登录用户仅能读公开帖已审评论 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/post-reports/{id}` | 仅举报人或审核员可查看举报及处理结果 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/post-review-requests/{id}` | 作者或审核员查看帖子审核快照/原因 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/posts` | 已审核且公开的帖子分页 | 公开查询 / 本人资源校验 |
| POST | `/api/v1/posts` | 创建本人图文草稿；图片必须为本人READY社区图片 | 公开查询 / 本人资源校验 |
| DELETE | `/api/v1/posts/{id}` | 本人软删除帖子；同时撤回待审核请求，重复删除幂等 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/posts/{id}` | 公开帖子详情；隐藏、未审核或作者停用时404 | 公开查询 / 本人资源校验 |
| PATCH | `/api/v1/posts/{id}` | 修改本人帖子内容并退回草稿；送审中不可改，旧版本409 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/posts/{id}/comments` | 公开帖已审核评论分页，不展示待审/隐藏评论 | 公开查询 / 本人资源校验 |
| POST | `/api/v1/posts/{id}/comments` | 创建本人待审评论；Idempotency-Key必填，重复同内容200 | 公开查询 / 本人资源校验 |
| POST | `/api/v1/posts/{id}/image-download-tickets` | 公开帖登录用户/作者/审核员获取该帖图片短时链接 | 公开查询 / 本人资源校验 |
| DELETE | `/api/v1/posts/{id}/likes/me` | 取消本人点赞，重复请求幂等 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/posts/{id}/likes/me` | 本人点赞状态与公开计数 | 公开查询 / 本人资源校验 |
| PUT | `/api/v1/posts/{id}/likes/me` | 本人点赞，联合唯一键保证重复请求幂等 | 公开查询 / 本人资源校验 |
| POST | `/api/v1/posts/{id}/reports` | 举报公开帖子，每人每帖一份举报资源，重复200 | 公开查询 / 本人资源校验 |
| POST | `/api/v1/posts/{id}/review-requests` | 本人提交不可变内容审核快照 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/search-results` | 公开商家/优惠/套餐/帖子搜索；明确返回MYSQL或ELASTICSEARCH后端 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/trade-offers` | 公开套餐、秒杀和拼团套餐列表 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/trade-offers/{id}` | 公开套餐详情，价格与内容发布后不可修改 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/trade-offers/{id}/group-purchases` | 可参与的拼团，不返回成员身份 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/users/me/campus-verifications` | 查询本人最近 100 条校园认证记录 | 公开查询 / 本人资源校验 |
| POST | `/api/v1/users/me/campus-verifications` | 提交本人校园认证申请 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/users/me/campus-verifications/{id}` | 查询本人单条校园认证申请 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/users/me/coupon-claims` | 分页查询本人优惠券 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/users/me/merchant-applications` | 分页查询本人入驻申请 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/users/me/post-comments` | 本人评论与审核状态分页 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/users/me/posts` | 本人帖子草稿及审核状态分页 | 公开查询 / 本人资源校验 |
| GET | `/api/v1/users/me/posts/{id}` | 本人帖子详情，包括未公开内容 | 公开查询 / 本人资源校验 |

## 商家端（37 项）

| 方法 | API | 功能 | 额外权限 / 范围 |
| --- | --- | --- | --- |
| GET | `/api/v1/coupon-review-requests/{id}` | 本店成员或优惠审核员查看送审快照 | 门店成员及操作角色 |
| PATCH | `/api/v1/coupons/{id}` | 店主或经理修改草稿 | 门店成员及操作角色 |
| GET | `/api/v1/coupons/{id}/management` | 本店成员查看优惠草稿及管理信息 | 门店成员及操作角色 |
| POST | `/api/v1/coupons/{id}/review-requests` | 提交优惠内容审核 | 门店成员及操作角色 |
| POST | `/api/v1/coupons/{id}/status-changes` | 审核通过后发布或下架优惠 | 门店成员及操作角色 |
| GET | `/api/v1/coupons/{id}/status-changes/{changeId}` | 本店成员查询优惠状态变更记录 | 门店成员及操作角色 |
| GET | `/api/v1/merchant/dashboard` | 查询门店经营统计（兼容入口） | `merchant:analytics:read` |
| PATCH | `/api/v1/merchants/{id}` | 店主修改门店介绍与营业时间 | 门店成员及操作角色 |
| GET | `/api/v1/merchants/{id}/location` | 本店成员读取明确坐标系的位置和版本 | 门店成员及操作角色 |
| PUT | `/api/v1/merchants/{id}/location` | 本店有发布资格OWNER/MANAGER设置WGS84位置；未知旧坐标不自动沿用 | 门店成员及操作角色 |
| GET | `/api/v1/merchants/{id}/members` | 店主分页查询本店成员 | 门店成员及操作角色 |
| POST | `/api/v1/merchants/{id}/members` | 店主添加本店店员 | 门店成员及操作角色 |
| DELETE | `/api/v1/merchants/{id}/members/{userId}` | 店主移除店员 | 门店成员及操作角色 |
| GET | `/api/v1/merchants/{id}/members/{userId}` | 店主查询本店单个成员 | 门店成员及操作角色 |
| POST | `/api/v1/merchants/{id}/qualification-download-tickets` | 店主创建本店私有资质下载凭据 | 门店成员及操作角色 |
| GET | `/api/v1/merchants/{id}/workspace` | 查询本店工作台资料 | 门店成员及操作角色 |
| GET | `/api/v1/merchants/{merchantId}/coupons` | 本店成员分页查看优惠管理记录 | 门店成员及操作角色 |
| POST | `/api/v1/merchants/{merchantId}/coupons` | 本店店主或经理创建优惠草稿 | 门店成员及操作角色 |
| GET | `/api/v1/merchants/{merchantId}/dashboard` | 查询所属门店经营统计 | `merchant:analytics:read` |
| GET | `/api/v1/merchants/{merchantId}/marketing-drafts` | 分页查看本人有权经营门店的私有营销草稿 | 门店成员及操作角色 |
| POST | `/api/v1/merchants/{merchantId}/marketing-drafts` | 根据本店公开事实生成私有营销草稿 | 门店成员及操作角色 |
| GET | `/api/v1/merchants/{merchantId}/marketing-drafts/capabilities` | 查询本人门店的营销生成能力、文本限制及当天调用预算 | 门店成员及操作角色 |
| GET | `/api/v1/merchants/{merchantId}/marketing-drafts/{id}` | 查询营销草稿及生成状态，重新校验来源，不调用模型 | 门店成员及操作角色 |
| PATCH | `/api/v1/merchants/{merchantId}/marketing-drafts/{id}` | 人工编辑未归档文案草稿；按版本拒绝覆盖并发修改 | 门店成员及操作角色 |
| POST | `/api/v1/merchants/{merchantId}/marketing-drafts/{id}/generation-attempts` | 原创建者重试失败或租约过期的生成；达到上限后409 | 门店成员及操作角色 |
| POST | `/api/v1/merchants/{merchantId}/marketing-drafts/{id}/status-changes` | 归档营销草稿，仅支持ARCHIVED，不支持发布 | 门店成员及操作角色 |
| POST | `/api/v1/merchants/{merchantId}/order-redemptions` | 本店 OWNER/MANAGER/CASHIER 核销订单；同码重试幂等 | 门店成员及操作角色 |
| GET | `/api/v1/merchants/{merchantId}/orders` | 本店成员查询本店订单，不返回买家身份 | 门店成员及操作角色 |
| GET | `/api/v1/merchants/{merchantId}/orders/{orderId}` | 本店订单详情，不返回买家身份 | 门店成员及操作角色 |
| GET | `/api/v1/merchants/{merchantId}/redemptions` | 本店成员分页查询核销历史 | 门店成员及操作角色 |
| GET | `/api/v1/merchants/{merchantId}/trade-offers` | 本店套餐列表 | 门店成员及操作角色 |
| POST | `/api/v1/merchants/{merchantId}/trade-offers` | 有发布资格的本店 OWNER/MANAGER 创建套餐草稿 | 门店成员及操作角色 |
| GET | `/api/v1/merchants/{merchantId}/trade-offers/{offerId}` | 本店套餐草稿或发布内容详情 | 门店成员及操作角色 |
| POST | `/api/v1/redemptions` | 本店店主、经理或核销店员完成核销 | 门店成员及操作角色 |
| GET | `/api/v1/redemptions/{id}` | 本店成员查询核销凭证 | 门店成员及操作角色 |
| PATCH | `/api/v1/trade-offers/{id}` | 发布或下架套餐，必须携带当前 version | 门店成员及操作角色 |
| GET | `/api/v1/users/me/merchants` | 查询本人所属门店 | 门店成员及操作角色 |

## 管理端（44 项）

| 方法 | API | 功能 | 额外权限 / 范围 |
| --- | --- | --- | --- |
| GET | `/api/v1/admin/accounts` | rbac:manage 查询账号所属端、状态与角色，不返回手机号 | `rbac:manage` |
| GET | `/api/v1/admin/accounts/{id}` | rbac:manage 单个账号授权详情 | `rbac:manage` |
| PATCH | `/api/v1/admin/accounts/{id}` | rbac:manage 修改他人账号所属端或状态，并撤销旧会话 | `rbac:manage` |
| PUT | `/api/v1/admin/accounts/{id}/roles` | rbac:manage 按版本替换同端角色，禁止修改自身授权 | `rbac:manage` |
| GET | `/api/v1/admin/audit-events` | 分页查询关键业务审计事件 | `audit:read` |
| GET | `/api/v1/admin/audit-events/{id}` | 查询单条审计事件 | `audit:read` |
| GET | `/api/v1/admin/campus-verification-reviews/{id}` | 查询校园认证审核历史记录 | `campus:review` |
| GET | `/api/v1/admin/campus-verifications` | 分页查询校园认证申请 | `campus:review` |
| GET | `/api/v1/admin/campus-verifications/{id}/identity` | 授权审核员读取加密身份资料 | `campus:review` |
| POST | `/api/v1/admin/campus-verifications/{id}/reviews` | 审核校园认证 | `campus:review` |
| GET | `/api/v1/admin/coupon-review-requests` | 按状态分页查询优惠送审请求 | `coupon:review` |
| POST | `/api/v1/admin/coupon-review-requests/{id}/reviews` | 创建优惠审核决定 | `coupon:review` |
| GET | `/api/v1/admin/coupon-reviews/{id}` | 读取不可覆写的优惠审核记录 | `coupon:review` |
| GET | `/api/v1/admin/dashboard` | 查询平台经营统计 | `analytics:read` |
| GET | `/api/v1/admin/merchant-applications` | 按状态分页查询入驻申请 | `merchant:review` |
| GET | `/api/v1/admin/merchant-applications/{id}` | 查询待审或已审入驻快照 | `merchant:review` |
| POST | `/api/v1/admin/merchant-applications/{id}/qualification-download-tickets` | 审核员创建申请所附资质的短时凭据 | `merchant:review` |
| POST | `/api/v1/admin/merchant-applications/{id}/reviews` | 审核入驻并创建不可覆写的记录 | `merchant:review` |
| GET | `/api/v1/admin/merchant-reviews` | 分页查询商家审核历史 | `merchant:review` |
| GET | `/api/v1/admin/merchant-reviews/{id}` | 查询不可覆写的商家审核记录 | `merchant:review` |
| GET | `/api/v1/admin/merchants/{id}` | 审核员查询门店当前状态和版本 | `merchant:review` |
| POST | `/api/v1/admin/merchants/{id}/status-changes` | 暂停或恢复门店 | `merchant:review` |
| GET | `/api/v1/admin/merchants/{id}/status-changes/{changeId}` | 查询门店状态变更记录 | `merchant:review` |
| GET | `/api/v1/admin/notification-deliveries` | 按状态分页查询通知投递 | `notification:manage` |
| GET | `/api/v1/admin/notification-deliveries/{id}` | 查询单条投递状态 | `notification:manage` |
| PUT | `/api/v1/admin/notification-deliveries/{id}/retry-request` | 将失败通知重新排队 | `notification:manage` |
| GET | `/api/v1/admin/operation-audit-events` | 按时间、操作者、动作和结果分页查询操作审计，不含请求或响应正文 | `audit:read` |
| GET | `/api/v1/admin/permissions` | rbac:manage 查询可分配权限目录 | `rbac:manage` |
| GET | `/api/v1/admin/post-comments` | community:moderate 评论审核队列 | `community:moderate` |
| PATCH | `/api/v1/admin/post-comments/{id}` | 审核/隐藏评论；拒绝旧版本、自审通过和删除后恢复 | `community:moderate` |
| GET | `/api/v1/admin/post-reports` | community:moderate 举报处理队列 | `community:moderate` |
| PATCH | `/api/v1/admin/post-reports/{id}` | 处理举报并留存结论；隐藏内容需另行明确操作 | `community:moderate` |
| GET | `/api/v1/admin/post-review-requests` | community:moderate 帖子内容审核队列 | `community:moderate` |
| POST | `/api/v1/admin/post-review-requests/{id}/reviews` | 审核通过公开/驳回；禁止自审和关联门店自审 | `community:moderate` |
| PATCH | `/api/v1/admin/posts/{id}` | community:moderate 隐藏违规帖子，必须说明原因 | `community:moderate` |
| GET | `/api/v1/admin/roles` | rbac:manage 查询角色分页及所属端 | `rbac:manage` |
| POST | `/api/v1/admin/roles` | rbac:manage 创建所属端固定的角色 | `rbac:manage` |
| DELETE | `/api/v1/admin/roles/{id}` | rbac:manage 按版本删除未被使用的角色 | `rbac:manage` |
| GET | `/api/v1/admin/roles/{id}` | rbac:manage 角色及权限详情 | `rbac:manage` |
| PATCH | `/api/v1/admin/roles/{id}` | rbac:manage 按版本修改角色，撤销受影响账号旧会话 | `rbac:manage` |
| GET | `/api/v1/admin/search-index-rebuilds` | search:manage 查询索引重建任务分页 | `search:manage` |
| POST | `/api/v1/admin/search-index-rebuilds` | search:manage 创建持久索引重建任务，不自动创建远端索引 | `search:manage` |
| GET | `/api/v1/admin/search-index-rebuilds/{id}` | search:manage 查询重建进度、游标与错误状态 | `search:manage` |
| POST | `/api/v1/admin/search-index-rebuilds/{id}/retries` | search:manage 重试失败的索引重建，保留已处理游标 | `search:manage` |

## 共享的本人资料与公共能力（19 项）

| 方法 | API | 功能 | 额外权限 / 范围 |
| --- | --- | --- | --- |
| GET | `/api/v1/community-capabilities` | 社区可用性、人工审核策略及内容上限，不含私有配置 | 公开能力 / 当前本人 |
| GET | `/api/v1/map-capabilities` | 地图配置及浏览器公开JS Key；不返回安全密钥，configured不是健康探测 | 公开能力 / 当前本人 |
| GET | `/api/v1/merchant-categories` | 查询可选商家分类 | 公开能力 / 当前本人 |
| GET | `/api/v1/notification-capabilities` | 查询通知能力与连接参数 | 公开能力 / 当前本人 |
| GET | `/api/v1/platform` | 查询工程阶段 | 公开能力 / 当前本人 |
| GET | `/api/v1/schools` | 查询校园认证可选学校 | 公开能力 / 当前本人 |
| GET | `/api/v1/search-capabilities` | 搜索后端、窗口与坐标系；configured不代表远端健康检查通过 | 公开能力 / 当前本人 |
| GET | `/api/v1/storage-capabilities` | 查询文件服务可用性与限制 | 公开能力 / 当前本人 |
| GET | `/api/v1/trade-capabilities` | 交易与沙箱支付可用性，不返回凭据 | 公开能力 / 当前本人 |
| GET | `/api/v1/uploads` | 分页查询本人文件元数据 | 公开能力 / 当前本人 |
| POST | `/api/v1/uploads` | 创建本人私有上传凭据 | 公开能力 / 当前本人 |
| GET | `/api/v1/uploads/{id}` | 查询本人文件元数据 | 公开能力 / 当前本人 |
| PUT | `/api/v1/uploads/{id}/completion` | 确认上传并封存私有文件 | 公开能力 / 当前本人 |
| POST | `/api/v1/uploads/{id}/download-tickets` | 创建本人文件短时下载凭据 | 公开能力 / 当前本人 |
| GET | `/api/v1/users/me` | 查询本人资料和当前权限 | 公开能力 / 当前本人 |
| PATCH | `/api/v1/users/me` | 修改本人昵称 | 公开能力 / 当前本人 |
| GET | `/api/v1/users/me/notifications` | 分页查询本人持久通知 | 公开能力 / 当前本人 |
| GET | `/api/v1/users/me/notifications/{id}` | 查询本人通知 | 公开能力 / 当前本人 |
| PUT | `/api/v1/users/me/notifications/{id}/read-receipt` | 幂等标记本人通知已读 | 公开能力 / 当前本人 |

## 维护与复现

先运行 `AssistantIsolationMySqlTest#catalogReflectsExactPortalAndDatabaseBackedPlatformGrants` 并设置 `CAMPUS_ASSISTANT_INVENTORY` 导出 JSON，再运行：

```sh
node scripts/generate-assistant-api-docs.mjs <inventory.json>
```

目录测试同时核对三端过滤、管理员扩展授权、敏感端点排除和参数 schema。表中的细分权限不是资源归属授权的替代品；所有真实业务服务仍执行各自的权限和归属检查。
