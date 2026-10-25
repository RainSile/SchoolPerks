# REST 接口合同

## 当前助手与跨端授权（2026-10-26）

新增GET /api/v1/assistant/business-apis，返回当前账号可用的固定业务操作与schema；POST /api/v1/assistant/conversations/{id}/message-streams采用SSE，Bearer和Idempotency-Key必填，JSON为message和allowWrites（默认false）。started/round/delta/tool/completed/error见ASSISTANT-BUSINESS-APIS.md；原JSON提问及状态接口保留。

相同路径按方法区分权限：GET /merchants/{id}为学生公开查询，PATCH为商家操作。学生/商家只用本端与共享本人接口，受限管理员保留细分权限；真实ADMIN具有business:manage后可跨端，但资源状态、版本、自审限制和私人会话归属仍生效。管理员会话校验针对实际ADMIN身份，不能把它改写为目标端身份。

助手转发本人JWT，URL/方法由目录固定，path/query/body仅接受已声明字段。写入先获本消息明确允许，使用不超过64位的稳定业务幂等键和持久结果日志。断开SSE不撤销已提交业务；结果未知先查询，不自动重复提交。完整API与权限表见ASSISTANT-BUSINESS-APIS.md。

交易状态机见 [STAGE4.md](STAGE4.md)，社区/位置/搜索见 [STAGE5.md](STAGE5.md)。

前五阶段本地业务及其后清单修复已实现，运行后/doc.html可查看完整OpenAPI。下表路径均以/api/v1开头。认证默认关闭时，相关业务明确503；支付宝沙箱另需显式启用和凭据，未准备时付款503。当前账号所属端为USER/MERCHANT/ADMIN，除公开/共用接口外需匹配端，再检查原有操作权限与资源归属。

## 第七阶段：经营看板与操作结果

| 方法与资源 | 行为/授权 |
| --- | --- |
| GET /merchants/{merchantId}/dashboard | MERCHANT + merchant:analytics:read，且真实本店OWNER/MANAGER；支持from/to（日期含首尾）、zone、refresh |
| GET /admin/dashboard | ADMIN + analytics:read；平台窗口汇总 |
| GET /merchant/dashboard?merchantId= | 兼容入口，必须明确门店；相同范围守卫 |
| GET /admin/operation-audit-events | ADMIN + audit:read；page/size及action/outcome/actorId/from/to过滤；时间UTC左闭右开、最大范围配置化 |

全部Bearer、no-store、OpenAPI。看板返回金额字符串、计算时间/缓存截止、可选时区和日期上限。沙箱支付和成功退款分别按发生时间统计；无领券核销率null，未采集浏览UV为false/null，不能把参与人数当UV。业务规则和AOP覆盖范围详见[STAGE7](STAGE7.md)。

## 清单修复：RBAC、助手及地图

| 方法与资源 | 行为/授权 |
| --- | --- |
| GET /admin/permissions | ADMIN + rbac:manage；权限目录 |
| GET /admin/roles、GET /admin/roles/{id} | 同上；分页角色及详情 |
| POST /admin/roles | 同上；201 + Location；代码唯一、所属端、权限目录校验 |
| PATCH /admin/roles/{id} | 同上；版本校验；不能修改自己持有的角色；受影响账号撤销会话 |
| DELETE /admin/roles/{id}?version= | 同上；204；不能删除使用中的角色 |
| GET /admin/accounts、GET /admin/accounts/{id} | 同上；分页账号及授权详情，不返回电话原文/令牌 |
| PATCH /admin/accounts/{id} | 同上；所属端/状态/version；不能改自身或直接转换仍有门店关系的账号 |
| PUT /admin/accounts/{id}/roles | 同上；version + 同端roleIds；撤销受影响账号会话 |
| GET /assistant/capabilities | 公开；配置状态；已登录时返回当前端的工具名称，不返回凭据 |
| POST /assistant/conversations | 登录，完整配置；空JSON请求，201 + Location |
| GET /assistant/conversations/{id}/messages | 本人同端会话，最近配置轮数，no-store |
| POST /assistant/conversations/{id}/messages | 本人同端，message + Idempotency-Key；新完成201、重放200、处理中202 + Location；无资源503 |
| GET /assistant/conversations/{id}/messages/{messageId} | 本人同端，状态及完成回答/来源，no-store |
| GET /map-capabilities | 公开；configured、公开JS Key和本机代理路径，不含安全密钥 |
| GET /maps/_AMapService/{version}/{*path} | 公开但按IP限频；官方SDK固定上游/路径代理，未启用503，未允许路径404 |

角色/账号状态使用版本冲突409、同端约束422、越权403；变更同时审计。UserProfile新增accountType/homePath，客户端不能提交这两个字段修改自己身份。助手会话跨账号/端404，同一幂等键不同输入409、限流429，供应商错误503且不回传供应商原文。完整行为见REPAIR-ACCEPTANCE.md。

## 第四阶段交易接口（已实现本地事务版）

| 方法与资源 | 行为/授权 |
| --- | --- |
| GET /trade-capabilities | 公开；enabled/paymentAvailable/paymentEnvironment=ALIPAY_SANDBOX，不含凭据 |
| GET /trade-offers | 公开；已发布、未结束、门店已审核的套餐分页，未来开始时间也可展示 |
| GET /trade-offers/{id} | 公开；已发布且门店已审核的套餐内容，不返回买家/成员身份 |
| GET /merchants/{merchantId}/trade-offers | 本店成员；含草稿/下架的套餐分页 |
| POST /merchants/{merchantId}/trade-offers | 本店有发布资格OWNER/MANAGER；创建草稿201+Location |
| GET /merchants/{merchantId}/trade-offers/{offerId} | 本店成员读取草稿/发布详情 |
| PATCH /trade-offers/{id} | 本店有发布资格OWNER/MANAGER；DTO status=PUBLISHED或OFFLINE/version，仅状态变更200 |
| GET /trade-offers/{id}/group-purchases | 公开；未到期OPEN团分页，只返回人数/期限，不暴露成员 |
| GET /group-purchases/{id} | 登录；团进度，只返回人数/状态/期限 |
| POST /orders | 登录；Idempotency-Key必填，DTO offerId/groupId；201新建，200同键同输入重放，409键冲突/库存不足/秒杀限购 |
| GET /orders | 本人订单分页 |
| GET /orders/{id} | 本人订单详情，不可变金额快照；他人404 |
| GET /merchants/{merchantId}/orders | 本店成员；订单分页，不含买家身份或核销码 |
| GET /merchants/{merchantId}/orders/{orderId} | 本店成员；本店订单详情 |
| POST /orders/{id}/cancellations | 本人待支付订单；新取消201，重复200，已付款409；库存只归还一次 |
| POST /orders/{id}/payments | 本人有效待支付订单；创建沙箱尝试201，恢复同一尝试200；未配置503；no-store |
| GET /orders/{id}/payments/{paymentId} | 本人尝试状态，不返回签名地址 |
| POST /payment-notifications/alipay-sandbox | form供应商入口；无Bearer但RSA2/app/seller/金额/订单校验；提交成功才纯文本success |
| POST /orders/{id}/refunds | 本人未核销已付款订单全额退款；新资源201 PENDING，重复200，不代表退款完成 |
| GET /orders/{id}/refunds | 本人订单退款资源数组，至多一条；包括团失败/迟到付款的自动退款 |
| GET /orders/{id}/refunds/{refundId} | 本人退款详情/status/attempts/脱敏错误代码 |
| POST /orders/{id}/refunds/{refundId}/retries | 本人FAILED退款；202+Location，保留原退款幂等号 |
| POST /orders/{id}/redemption-codes | 本人PAID订单；200 no-store短期码与expiresAt；限频429 |
| POST /merchants/{merchantId}/order-redemptions | 本店OWNER/MANAGER/CASHIER且门店可经营；DTO orderId/code；首次201，同码重试200 |

OfferInput：title/description/kind=REGULAR或SECKILL或GROUP/price（BigDecimal，两位小数）/stock/startsAt/endsAt/groupSize/groupDurationSeconds。普通/秒杀groupSize=1且groupDurationSeconds=0；拼团人数至少2、不得超过库存和配置上限。期限及库存上限来自配置；发布时间/价格/内容不可变更，改内容需新建套餐。

OrderInput不接受amount/currency/quantity/userId。每单一份、服务端CNY金额快照；GROUP下省略groupId为开团，提供则加入同一套餐的开放团。Idempotency-Key为8–64位字母/数字/下划线/连字符，按用户隔离。Coupon仍是到店权益，本阶段不叠加结算。

交易错误使用ProblemDetail和真实状态码：TRADE_RESOURCE_NOT_FOUND=404，INVALID_IDEMPOTENCY_KEY=400，INVALID_OFFER_RULES=422，STALE_VERSION/OFFER_NOT_ON_SALE/OUT_OF_STOCK/SECKILL_LIMIT/GROUP_UNAVAILABLE/IDEMPOTENCY_KEY_REUSED/ORDER_NOT_PAYABLE/ORDER_NOT_CANCELLABLE/ORDER_NOT_REFUNDABLE/INVALID_REDEMPTION_CODE/PAYMENT_MISMATCH=409，CODE_RATE_LIMITED=429，TRADE_UNAVAILABLE/SANDBOX_PAYMENT_UNAVAILABLE=503。无权限/跨店还会复用已有门店守卫403/404，未知JSON字段400。

## 认证与用户

| 方法及资源 | 行为 | 授权 |
| --- | --- | --- |
| GET /authentication-capabilities | 200：enabled/smsAvailable/codeLength/resendAfterSeconds/smsSimulated/passwordMinLength/passwordMaxBytes | 公开，不含固定测试码 |
| POST /verification-challenges | 201 + Location；DTO phone/purpose=LOGIN\|REGISTER\|RESET_PASSWORD；不含验证码 | 公开，手机号/IP 限频 |
| GET /verification-challenges/{id} | 200：状态和过期时间，不含手机号或验证码 | 随机挑战 ID |
| POST /sessions | 201；DTO phone/challengeId/code；自动注册普通用户 | 一次性验证码 |
| POST /password-sessions | 201；DTO phone/password；使用既有账号角色登录 | 手机号/IP 限频 |
| POST /registrations | 201；DTO phone/challengeId/code/nickname/password；仅创建普通用户并返回会话 | 一次性 REGISTER 验证码 |
| POST /password-resets | 204；DTO phone/challengeId/code/password；撤销该账号全部会话 | 一次性 RESET_PASSWORD 验证码 |
| PUT /users/me/password | 204；DTO currentPassword/password；撤销全部会话并要求重新登录 | 登录且当前密码正确 |
| POST /session-refreshes | 201；DTO refreshToken，返回轮换后令牌 | 不透明刷新令牌 |
| GET /sessions/current | 200：userId，不含令牌 | 登录 |
| DELETE /sessions/current | 204，撤销当前会话 | 登录 |
| GET /users/me | 200：本人资料、脱敏手机号、最新权限 | 登录 |
| PATCH /users/me | 200；只接受 nickname | 登录，只修改本人 |
| GET /schools | 200：最多 1000 所学校 | 公开 |
| POST /users/me/campus-verifications | 201 + Location；DTO schoolId/realName/studentNumber | 登录 |
| GET /users/me/campus-verifications | 200：本人最近 100 条记录 | 登录 |
| GET /users/me/campus-verifications/{id} | 200；其他用户记录按 404 处理 | 登录 |

密码/短信登录、注册、刷新返回 sessionId、tokenType、accessToken、accessExpiresAt、refreshToken、refreshExpiresAt，Cache-Control: no-store。Location 指向 /api/v1/sessions/current。令牌只允许在这些专用认证响应中出现。普通账号首次没有后台权限，不能通过请求体设置角色。密码默认至少10个字符且UTF-8不超过72字节，BCrypt强度12；策略从配置读取，禁止静默截断。

本人资料包含 id/nickname/maskedPhone/schoolId/schoolName/campusVerificationStatus/permissions；校园身份仅在审核通过时生效。认证申请响应不包含姓名、学号或密文。

| 错误 | HTTP | 语义 |
| --- | --- | --- |
| INVALID_REQUEST | 400 | 格式、未知字段或校验失败 |
| INVALID_VERIFICATION | 401 | 验证码错误、过期、耗尽、失效或已消费 |
| INVALID_CREDENTIALS | 401 | 手机号或密码错误、未设置密码或账号不可用，统一错误避免枚举 |
| PASSWORD_POLICY | 400 | 新密码未达到配置规则 |
| PHONE_REGISTERED | 409 | 已注册，不能覆盖已有密码或角色 |
| INVALID_SESSION / INVALID_TOKEN | 401 | 刷新失效、会话撤销、账号封禁或令牌不合法 |
| FORBIDDEN | 403 | 缺少最新授权 |
| SCHOOL_NOT_FOUND / VERIFICATION_NOT_FOUND | 404 | 学校或本人申请不存在 |
| VERIFICATION_EXISTS | 409 | 已有待审核/通过的申请 |
| VERIFICATION_RATE_LIMITED | 429 | 手机号或来源频率受限 |
| AUTH_UNAVAILABLE / SMS_UNAVAILABLE / SMS_DELIVERY_FAILED | 503 | 认证未启用、短信未准备或发送失败 |

错误统一是 ProblemDetail，使用真实 HTTP 状态和 code，不在 detail 中回显验证码、令牌、原始手机号或供应商错误。

## 其他现有接口

| 方法 | 资源 | 当前行为 | 授权 |
| --- | --- | --- | --- |
| GET | /platform | 200，基础工程状态 SCAFFOLD | 公开 |
| GET | /merchants | local 中真实分页；skeleton 为 501 | 公开，仅已审核商家 |
| GET | /coupons | 真实公开分页；未启用认证业务503 | 公开 |
| GET | /posts | 真实已审核公开帖子分页 | 公开 |
| GET | /search-results | 真实投影搜索，必填keyword | 公开 |
| GET | /orders | 真实本人订单分页 | 登录 |
| GET | /merchant/dashboard | 501 | analytics:read |
| POST | /assistant/conversations | 已实现，见清单修复表 | 登录、完整私有配置 |

受保护接口先鉴权，匿名返回 401。分页 page 从 1 开始、size 最多 100；成功分页 items/total/page/size，排序白名单。业务 ID 使用字符串，金额使用精确十进制，时间 UTC。

## 第六阶段与后续计划

### 第六阶段新增：私有营销草稿

前缀`/merchants/{merchantId}/marketing-drafts`。全部要求MERCHANT及真实门店成员、no-store；写入仅限已审核门店OWNER/MANAGER。收银员只读，不因角色表有同名权限获得写权。

| 方法/后缀 | 请求/响应 |
| --- | --- |
| GET /capabilities | enabled、文本长度、dailyChatLimit/dailyChatUsed及可选语气 |
| GET / | page/size，返回本店私有草稿分页 |
| GET /{id} | DRAFT/GENERATING/FAILED/ARCHIVED、version/attempt、来源摘要及sourceCurrent；不调用模型 |
| POST / | brief/tone/couponId（可空），Idempotency-Key必填；首次完成201、重放200、处理中202，Location指向草稿 |
| POST /{id}/generation-attempts | version；仅原创建者重试失败或过期任务，已完成重放不再次调用 |
| PATCH /{id} | version/title/body；人工编辑DRAFT |
| POST /{id}/status-changes | version/status，仅ARCHIVED；不能传PUBLISHED |

不存在或跨店404，其他端/收银写入403，输入校验400，版本/幂等输入冲突/来源变化/次数耗尽409，长度上限422，模型结果结构错误/违规工具输出502，缺资源、响应解析失败或供应商失败503。分钟限频及共享UTC日预算429；失败供应商尝试计入调用预算，幂等重放不重复计数。所有错误不回显供应商原文或凭据。

模型只输出title/body，不接收任何发布/核销/支付工具。生成保存和审计同事务，外部调用在事务外；真实规则从PromotionFactsPort读取。完整状态与重试语义见STAGE6.md。

第二至第六阶段接口及AI助手已实现，见各阶段表；异步领券仍为后续计划。

后续异步：GET /claim-requests/{id}。当前没有claim-request异步任务资源，同步领取直接返回已落库的券。第四阶段开团/参团统一通过POST /orders的groupId语义实现，没有额外的开团或成员创建接口。

AI会话和商家营销草稿已实现。营销写入由商家页面明确发起，模型本身没有自动写入或发布工具，接口见第六阶段表。

异步领券仍为计划，不能当作已实现；以后使用202和查询资源，不在落库前声称成功。营销文案只保留私有草稿，优惠仍需人工通过原业务审核/发布流程。禁止 GET 修改状态、动词式 /getUser 或 URL 中传递凭据。

## 第二阶段接口（均已实现）

下列路径同样以 /api/v1 开头。文件/门店业务在 local/prod + AUTH_ENABLED/JWT_ENABLED 时启用；默认关闭时，鉴权后的业务调用返回 503。公开商家详情与列表只返回 APPROVED。未知 JSON 字段拒绝 400。

| 方法与资源 | 行为/权限 |
| --- | --- |
| GET /merchant-categories | 公开；配置的分类代码集合 |
| GET /merchants/{id} | 公开；非 APPROVED 为 404；不含资质与所有者 |
| POST /merchant-applications | 登录；schoolId/name/category/address/description/qualificationFileId，坐标可选且成对；201 + 本人申请 Location |
| GET /users/me/merchant-applications | 登录；本人分页 |
| GET /merchant-applications/{id} | 登录；本人申请快照，其他人 404 |
| GET /users/me/merchants | 登录；真实所属门店分页，含本店角色 |
| GET /merchants/{id}/workspace | 本店成员；无关联 404 |
| PATCH /merchants/{id} | 本店 OWNER 且 APPROVED；仅 version/description/businessHours；并发过期 409 |
| GET /merchants/{id}/members | 本店 OWNER；成员分页 |
| POST /merchants/{id}/members | 本店 OWNER 且 APPROVED；userId/role=MANAGER或CASHIER；201 + 成员 Location；目标必须是活跃用户 |
| GET /merchants/{id}/members/{userId} | 本店 OWNER；单个成员 |
| DELETE /merchants/{id}/members/{userId} | 本店 OWNER 且 APPROVED；204；禁止移除 OWNER |
| POST /merchants/{id}/qualification-download-tickets | 本店 OWNER；200 短期私有链接、no-store、审计 |
| GET /storage-capabilities | 公开；enabled/available、允许 MIME/大小与期限；不含供应商秘密 |
| POST /uploads | 登录；fileName/contentType/size/purpose=MERCHANT_QUALIFICATION或COMMUNITY_IMAGE；201 + Location；临时 PUT 链接、no-store |
| GET /uploads | 本人文件分页；不含桶/key/签名 |
| GET /uploads/{id} | 本人元数据，其他人 404 |
| PUT /uploads/{id}/completion | 本人；封存并检查真实大小/MIME/文件头；READY 后幂等 200；过期410、不匹配422 |
| POST /uploads/{id}/download-tickets | 本人 READY 文件；200 短时链接、no-store、审计 |
| GET /admin/merchant-applications | merchant:review；status=PENDING/APPROVED/REJECTED，分页 |
| GET /admin/merchant-applications/{id} | merchant:review；申请快照 |
| POST /admin/merchant-applications/{id}/reviews | merchant:review；decision=APPROVED或REJECTED、reason；201 + 审核记录 Location；不可自审/重复审 |
| GET /admin/merchant-reviews | merchant:review；审核历史分页 |
| GET /admin/merchant-reviews/{id} | merchant:review；单条记录 |
| POST /admin/merchant-applications/{id}/qualification-download-tickets | merchant:review；只访问该申请关联的资质；200、no-store、审计 |
| GET /admin/merchants/{id} | merchant:review；当前状态及 version |
| POST /admin/merchants/{id}/status-changes | merchant:review；status=SUSPENDED或APPROVED/version/reason；201 + Location；禁止操作本人门店 |
| GET /admin/merchants/{id}/status-changes/{changeId} | merchant:review；状态历史单条 |
| GET /admin/campus-verifications | campus:review；状态分页，不含真实姓名/学号 |
| GET /admin/campus-verifications/{id}/identity | campus:review；独立读取解密身份，no-store + 审计 |
| POST /admin/campus-verifications/{id}/reviews | campus:review；decision/reason；201 + Location；不可自审/重复审 |
| GET /admin/campus-verification-reviews/{id} | campus:review；审核历史单条 |
| GET /admin/audit-events | audit:read；page/size，可筛 resourceType/resourceId |
| GET /admin/audit-events/{id} | audit:read；单条事件，不含私有内容 |

缺少平台权限为403；不是目标门店成员/文件所有者为404；本店店员操作 OWNER 功能为403；重复申请/重复审核/成员已存在/暂停写入/过期版本为409；待上传配额超限429；存储未配置或供应商异常503。短时签名链接只在专用凭据响应返回，不放入普通列表、审计、日志或浏览器持久存储。前端直传 PUT 不附平台 Bearer。

## 第三阶段接口（已实现MySQL同步版）

CouponInput为title/couponType/rules/totalStock/validFrom/validUntil/campusOnly/description。rules的threshold必填；AMOUNT_OFF只填amountOff，DISCOUNT只填discountRate（0到1之间，不含端点）。默认最多10万张、最长90天，实际以配置为准。金额由BigDecimal处理，时间为UTC。

| 方法与资源 | 行为与权限 |
| --- | --- |
| GET /coupons | 公开；page/size、keyword/schoolId/merchantId；仅PUBLISHED且审核通过、有发布时间、门店APPROVED、未到期；允许浏览未来活动 |
| GET /coupons/{id} | 同公开范围，其他404；不含私有券码 |
| POST /merchants/{merchantId}/coupons | 本店OWNER/MANAGER且具备发布资格；CouponInput；201 + management Location |
| GET /merchants/{merchantId}/coupons | 本店成员分页；含当前审核意见/状态/版本 |
| GET /coupons/{id}/management | 本店成员；完整草稿管理信息 |
| PATCH /coupons/{id} | 本店OWNER/MANAGER；version/content；只改非送审中的未发布草稿，清除旧审核许可 |
| POST /coupons/{id}/review-requests | 本店OWNER/MANAGER；version；201 + 不可变送审请求Location |
| GET /coupon-review-requests/{id} | 本店成员或coupon:review；原内容快照与独立review决定/原因 |
| GET /admin/coupon-review-requests | coupon:review；status=PENDING/APPROVED/REJECTED，分页 |
| POST /admin/coupon-review-requests/{id}/reviews | coupon:review；decision/reason；201 + 审核记录Location；禁止本人/所属门店自审 |
| GET /admin/coupon-reviews/{id} | coupon:review；不可覆写的审核决定 |
| POST /coupons/{id}/status-changes | 本店OWNER/MANAGER；version/status=PUBLISHED或OFFLINE/reason；201 + 状态历史Location |
| GET /coupons/{id}/status-changes/{changeId} | 本店成员；状态历史单条 |
| POST /coupons/{id}/claims | 登录；首次201，已领过200返回原券；均有Location。同步事务成功后才返回 |
| GET /users/me/coupon-claims | 本人分页，不含核销码/摘要 |
| GET /coupon-claims/{id} | 本人券状态，其他404；到期派生为EXPIRED |
| POST /coupon-claims/{id}/redemption-tickets | 本人待使用且有效的券；200 code/qrCodeSvg/expiresAt、no-store；轮换/限频；不可替其他人生成 |
| POST /redemptions | 本店OWNER/MANAGER/CASHIER；merchantId/code仅在请求体；首次201，持同码重试200；Location指向核销记录 |
| GET /redemptions/{id} | 本店成员；核销凭证 |
| GET /merchants/{merchantId}/redemptions | 本店成员分页历史 |
| GET /notification-capabilities | 公开；enabled/streamPath/pingIntervalSeconds/reconnectDelaySeconds |
| GET /users/me/notifications | 本人持久通知分页；GET不标记已读 |
| GET /users/me/notifications/{id} | 本人单条通知，其他404 |
| PUT /users/me/notifications/{id}/read-receipt | 本人幂等已读，200 |
| GET /admin/notification-deliveries | notification:manage；按PENDING/PROCESSING/PUBLISHED/FAILED分页，不返回原始payload |
| GET /admin/notification-deliveries/{id} | notification:manage；状态/次数/通用错误码 |
| PUT /admin/notification-deliveries/{id}/retry-request | notification:manage；FAILED重新排队并审计，已PENDING幂等；200 |

接口仍先验证真实会话及最新权限。跨店404；店员发布403；错误或跨店核销码统一400 INVALID_REDEMPTION_CODE，避免泄漏券归属；无对应门店成员关系404。库存售罄、未开始/已结束、下架领取、不可编辑、未审核发布、旧版本、重复审核409；生成码/核销尝试频繁429；未启用业务503。

下架不作废已领券，暂停门店则停止本店领券、签发核销码和核销。核销重复请求只返回原记录，不重复库存/审计/outbox；GET本人券可在码过期后恢复最终状态。现有领取次数唯一约束为1，不能只改配置放开。

WebSocket为/api/v1/notification-stream，不能用OpenAPI HTTP调试代替升级连接。首帧AUTHENTICATE携带accessToken，禁止URL凭据、禁止任意订阅userId；READY后PING/PONG，推送NotificationView(type=COUPON_REDEEMED、eventId、本人payload)。断线使用本人通知/券查询恢复；已撤销或过期会话关闭1008，Origin受白名单约束。详见STAGE3.md。

## 第五阶段接口（已实现本地业务与ES协议适配器）

下列路径均以 /api/v1 开头；受保护操作使用Bearer JWT与最新权限。公开GET的OpenAPI不声明Bearer，写操作逐项声明。未启用业务503；未知JSON字段400，旧版本409，越权资源按404或明确权限403处理。

| 方法与资源 | 行为与授权 |
| --- | --- |
| GET /community-capabilities | 公开；本地限额、persistence=MYSQL，Mongo/自动内容审核不可用 |
| GET /posts、GET /posts/{id} | 公开；仅PUBLISHED且作者活跃、关联门店APPROVED；列表keyword/schoolId/page/size |
| POST /posts | 登录；schoolId/可选merchantId/content={title,body,imageIds}；201 + 本人管理Location |
| GET /users/me/posts、GET /users/me/posts/{id} | 作者本人；含草稿、审核状态/版本及本人reviewReason |
| PATCH /posts/{id} | 作者；version/content；送审中拒绝，已公开修改立即撤回为DRAFT |
| DELETE /posts/{id} | 作者；软删除/撤回待审，重复204 |
| POST /posts/{id}/review-requests | 作者；version；不可变快照201 + Location |
| GET /post-review-requests/{id} | 作者或community:moderate；快照与审核原因 |
| GET /admin/post-review-requests | community:moderate；status筛选分页 |
| POST /admin/post-review-requests/{id}/reviews | community:moderate；decision=APPROVED或REJECTED/reason；禁止作者或关联门店自审；201 |
| PATCH /admin/posts/{id} | community:moderate；version/reason，显式隐藏 |
| POST /posts/{id}/image-download-tickets | 登录的公开帖读者、作者或审核员；只签该帖图片，no-store；无存储503 |
| GET /posts/{id}/comments | 公开；公开帖已审/活跃作者评论分页 |
| POST /posts/{id}/comments | 登录；body + Idempotency-Key；新待审评论201，同键同内容200，不同内容409 |
| GET /post-comments/{id} | 登录；本人/审核员可读私有评论，其他人只读公开帖已审评论 |
| GET /users/me/post-comments | 本人；含待审/已删除等状态 |
| DELETE /post-comments/{id} | 作者；幂等软删除204 |
| GET /admin/post-comments | community:moderate；状态分页 |
| PATCH /admin/post-comments/{id} | community:moderate；version/status=APPROVED或REJECTED或HIDDEN/reason；禁止自审通过或恢复已删除评论 |
| GET /posts/{id}/likes/me | 登录；本人点赞状态和计数 |
| PUT /posts/{id}/likes/me | 登录；幂等点赞200 |
| DELETE /posts/{id}/likes/me | 登录；幂等取消200，返回最新状态/计数 |
| POST /posts/{id}/reports | 登录；reason；每人每帖唯一，新201、重复200 |
| GET /post-reports/{id} | 举报人或community:moderate；其他人404 |
| GET /admin/post-reports | community:moderate；status筛选分页 |
| PATCH /admin/post-reports/{id} | community:moderate；version/status=RESOLVED或DISMISSED/reason；不会隐式隐藏帖子 |
| GET /merchants/{id}/location | 本店成员；未设置404 LOCATION_NOT_SET |
| PUT /merchants/{id}/location | 本店有发布资格OWNER/MANAGER；longitude/latitude/coordinateSystem=WGS84/version，初次version=0；200 |
| GET /nearby-merchants | 公开；必填longitude/latitude/coordinateSystem=WGS84，可选radiusMeters/schoolId/category/page/size，直线距离排序；no-store |
| GET /search-capabilities | 公开；backend/配置选择/坐标系/窗口/半径；不是远端健康检查 |
| GET /search-results | 公开；keyword必填≤100，resourceType=MERCHANT或COUPON或OFFER或POST、schoolId/page/size；窗口限制422 |
| GET /admin/search-index-rebuilds、GET /admin/search-index-rebuilds/{id} | search:manage；任务状态、processed、游标、错误码 |
| POST /admin/search-index-rebuilds | search:manage；持久任务201 + Location；已有活跃任务409 |
| POST /admin/search-index-rebuilds/{id}/retries | search:manage；FAILED重新排队，202 + Location |

帖子正文默认最多6000、评论2000、图片6张，实际同时受配置和DTO上限约束。评论Idempotency-Key为8–64位字母/数字/下划线/连字符。图片用途COMMUNITY_IMAGE仅JPEG/PNG且必须本人READY，不能引用资质文件。

搜索结果为 backend/items/total/page/size/windowLimited；标题和摘要高亮为纯文本片段{text,matched}，不是HTML。当前MYSQL后端为字面子串匹配；ES模式只接受白名单字段，远端不可用503 SEARCH_ENGINE_UNAVAILABLE，绝不静默伪造ES命中。附近坐标范围经度[-180,180]、纬度[-90,90]，非WGS84返回422，配置最大半径默认20000米；不是步行距离。
