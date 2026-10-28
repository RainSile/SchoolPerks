# 数据库与迁移

## 当前结构：V13（2026-10-28）

当前55张业务基表、Flyway历史表和9个视图；下面V1–V6说明是历史演进。V7加入实际账号端和RBAC授权版本，V8加入助手会话/持久消息与租约，V9加入密码认证，V10加入营销草稿及交互预算，V11加入统计视图及操作审计，V12加入平台跨端权限和助手写操作日志。V13 为验证码挑战加入 verifier_type、provider_reference、provider_scope；LOCAL 挑战保留哈希，远程挑战不保存验证码哈希，code_hash 允许为 NULL，并用约束区分模式。已执行 V1–V13 不可改，下一迁移从 V14 开始。云端旧实例在部署新包前仍可能处于 V12，不应将源码版本冒充实例状态。

V12的campus_assistant_tool_execution以(turn_id,ordinal)为主键、外键绑定真实消息；记录固定operationId、规范化入参摘要、RUNNING/COMPLETED及脱敏结果。先提交尝试记录再调用真实业务API，完整结果才标记完成；未知结果不盲重放，同一turn已完成意图复用结果。网络调用不持有数据库事务。

business:manage只授予实际ADMIN角色，现有rbac:manage的ADMIN角色由迁移显式获得此权限；普通账号不被提升。账号真实端、会话、权限、成员和资源范围仍由原业务表校验，不创建假身份。

MongoDB已用于assistant_turns已完成聊天，查询必须同时限定owner、portal、conversationId；SQL仍是命令与状态事实。Pinecone只存公开资料投影，不能写入私有聊天。修复回归按专属根ID清理并比较56表摘要，自增序列正常前进，不回退编号。

当前已执行V1–V11：54张业务表、flyway_schema_history、9个只读视图（4公开来源/目录、5私有统计）；后续只能追加V12+。V9扩展短信挑战用途为LOGIN/REGISTER/RESET_PASSWORD，使用既有password_hash列实现密码认证，不新增默认账号。下文旧阶段项数为对应阶段历史。

## V11 第七阶段（已执行）

新增campus_analytics_event/claim/order/merchant/account，只读聚合支付/成功退款/领券/核销和创建计数；统计视图不是公开API，只由analytics在后端权限和范围守卫后查询。事件视图UNION ALL保留原业务时间，领取与核销唯一关联不产生扇出；Java生成UTC日界线。新增日期窗口索引，不修改历史业务记录。

campus_operation_audit保存固定动作/资源、操作者ID、SUCCESS/DENIED/FAILURE、HTTP/通用错误代码、耗时与时间；不保存请求或响应正文/异常原文，不依赖actor外键删除历史。原campus_audit_event业务事务审计保留。V11已执行不可改；详见STAGE7.md。

## V10 第六阶段（已执行）

campus_marketing_draft保存门店/创建者/幂等键、输入摘要、公开事实快照、草稿状态、版本、尝试及租约；唯一键为门店/创建者/请求键。campus_ai_usage按账号/UTC日期/CHAT或EMBEDDING记录保守调用计数，不保存供应商密钥或精确账单。模型请求不占SQL事务；最终保存重查会话、门店、来源及租约，审计失败回滚结果。

公开campus_search_source视图追加source_version/valid_from/valid_until，旧列和内容哈希协议保留。Pinecone仅追加文档元数据，不修改索引schema。详见STAGE6.md。

## V7 / V8 清单修复（已执行）

V7给campus_user增加唯一account_type及access_version、campus_role增加所属端/version、campus_auth_session保存签发时授权版本，并新增campus_rbac_lock串行锁。既有门店成员迁为MERCHANT，既有平台权限账号迁为ADMIN；权限目录固定迁移，未插入角色、管理员或测试账号。授权更新与撤销会话/审计同事务。

V8新增campus_assistant_conversation和campus_assistant_turn；会话绑定owner_id/account_type，消息的conversation_id/request_key唯一，保存输入哈希、临时输入、状态、attempt、租约token与期限。完整消息在Mongo assistant_turns集合，owner/portal/conversationId/createdAt索引；成功后SQL临时输入清除。Mongo保存采用attempt条件防止过期worker覆盖，SQL完成采用token防旧确认；中断重试沿用相同消息ID。跨库不宣称原子事务，先保存消息后完成命令，可由同一幂等键恢复。

Pinecone Documents索引只保存公开内容向量及业务ID/来源哈希，ES是另一份可重建公开搜索投影，均不存私有文件凭据或账号问答。隐藏、删除及返回前主库校验不能省略。评论/点赞仍由MySQL保存。

## V5 交易结构（已执行）

| 表/变更 | 用途与约束 |
| --- | --- |
| campus_trade_offer | 套餐类型/BigDecimal价格/总库存与可用库存/售卖期限/团规则/发布版本；库存范围与类型CHECK |
| campus_trade_catalog（视图） | 仅已发布且门店APPROVED的公开套餐投影，不跨模块引用Mapper |
| campus_trade_group | OPEN/SUCCEEDED/FAILED；已付款≤已占位≤成团人数，期限索引 |
| campus_trade_group_member | 团/用户联合唯一，订单唯一，成员历史保留 |
| campus_order（原表扩充） | offer/group关联、GROUP_PENDING/REFUND_PENDING状态、stock_released、核销时间与短期码摘要/期限 |
| campus_trade_order_request | 用户/Idempotency-Key联合唯一；保存原输入关联与唯一订单 |
| campus_trade_payment | 每订单唯一尝试，供应商订单号唯一、支付宝交易号唯一；周期查单时间 |
| campus_trade_callback | 通知ID主键、支付关联、原参数规范化SHA-256；不存原通知/买家隐私 |
| campus_trade_cancellation | 每订单唯一取消记录，原因/时间；不覆盖历史 |
| campus_trade_refund | 每订单唯一全额退款、持久供应商幂等ID、租约/次数/退避/通用错误码/完成时间 |

V5只删除V1的ck_order_status并用扩展状态CHECK替换，没有修改V1迁移本身。原订单offer_id允许NULL以保留早期结构；本阶段所有新订单必须有套餐关联，业务查询排除无套餐的旧占位行。金额为DECIMAL(12,2)，币种CNY为当前交易协议，应用不接收客户端金额。

下单扣库存与订单/请求/成员/审计/outbox同事务；取消使用订单stock_released条件更新，已释放则不再次加库存。已付款退款只在供应商确认后释放仍保留的库存。回调幂等表、支付状态、订单迁移和审计/outbox同事务，不能先记通知成功再提交业务。

后台逐单处理超时/团结果；退款以SKIP LOCKED短事务领取，HTTP在事务外，用原退款ID重试。租约token防止过期worker确认新任务。MySQL REPEATABLE READ下判断状态使用FOR UPDATE当前行，不依赖普通快照；锁顺序见STAGE4.md。

测试只在授权campus_perks_db内回滚夹具或按精确账号/门店/订单ID删除；清理子表在前，保留外键、迁移与视图，不清空整个库或执行Flyway clean。

MySQL 8.0.16+，`campus_perks_db`，utf8mb4，InnoDB。V1 提供 14 张基础表，另有 Flyway 自建的迁移历史表，不在各业务模块复制建表脚本。

| 表 | 归属 | 核心约束 |
| --- | --- | --- |
| campus_school | user | 校园主数据 |
| campus_user | user | 手机号唯一、校园外键、用户状态 |
| campus_role / campus_permission | admin | 编码唯一 |
| campus_user_role / campus_role_permission | admin | 组合主键 |
| campus_merchant | merchant | 所有者/校园外键、状态/经纬度检查 |
| campus_merchant_member | merchant | 门店与用户组合主键 |
| campus_coupon | coupon | 非负库存、有效期、规则快照 |
| campus_coupon_claim | coupon | 用户与券唯一、券码摘要唯一 |
| campus_order | trade | 订单号唯一、非负金额、状态机字段 |
| campus_redemption | coupon | 每张已领券最多核销一次 |
| campus_outbox_event | integration | 事件 ID、重试时间与投递状态 |
| campus_consumed_event | integration | 消费者与事件 ID 组合主键 |

V1 是基础模型，下面的表由后续阶段通过 V2、V3…迁移增加：

- 菜品与进一步门店资料变更审核；入驻、审核、营业时间已在 V3，校园认证申请在 V2。
- 秒杀套餐库存、拼团活动/团/成员、支付尝试/回调、取消/退款流水、幂等请求。
- 帖子/图片关联/举报/内容审核；MongoDB 评论和点赞集合及唯一索引。
- AI 会话/消息/检索来源；文件对象/用途/所有权及关键操作审计已在 V3。

MerchantEntity 已映射门店写入字段。入驻必须经过 MerchantWorkflowServiceImpl 的申请/审核事务，不能绕过流程直接写 APPROVED。旧 qualification_object_key 字段仅为兼容保留，新门店使用 qualification_file_id；公开 VO 不返回两者。其他待实现模块仍应按迁移补齐实体。

已执行迁移不可修改；变更新增迁移。不启用自动建库、Flyway clean 或 baseline-on-migrate。遇到非空且非本工程创建的库，先调查，不通过基线操作掩盖差异。

## 一致性

库存 SQL 带 `remaining_stock > 0` 条件并检查受影响行数。Redis 负责预占和削峰，MySQL 约束兜底。`uk_claim_user_coupon` 限定每人每券一次，放宽限额需要迁移，不只是改 YAML。

核销需同时校验 claim→coupon→merchant 与当前店员门店关系，外键不自动保证该业务关系。券码保存摘要；生成强度、限频与轮换在核销阶段实现。

订单 offer_snapshot 保存下单时商品、规则和价格，活动修改不能改写历史结算。amount 使用 BigDecimal/DECIMAL，currency 显式保存。

业务行和outbox在同一事务写入；第三阶段notification模块已实现租约投递与重试、持久通知唯一去重及WebSocket提示。campus_consumed_event仍留待未来MQ消费者；当前去重由campus_notification的event/user唯一约束承担。

## 测试

默认 `mvn verify` 不使用本机数据库。`CAMPUS_MYSQL_TEST=true` 才执行 MySQL 测试。凭据仅通过当前进程环境传递；连接池默认上限 3，不启动压测或改动 MySQL 服务设置。

MySqlReadTest 验证迁移、分页和审核状态过滤，临时数据随测试事务回滚。测试数据库与 Flyway 历史保留，供后续开发使用。


## V2 认证与校园认证

V2 已新增五张表，V2 完成时共 19 张业务表及 Flyway 历史表；V3 后见下节。V1 未改动。

| 表 | 约束与用途 |
| --- | --- |
| campus_verification_rate | HMAC 频率桶；手机号冷却、手机号窗口、IP 窗口；行锁串行计数 |
| campus_verification_challenge | 一次性 LOGIN 挑战；HMAC 验证码、状态、次数、到期时间 |
| campus_auth_session | 用户会话及绝对到期/撤销时间；每次请求验证 |
| campus_refresh_token | 只存刷新令牌 HMAC；记录消费时间，支持轮换与重放检测 |
| campus_campus_verification | AES-256-GCM 加密身份；用户/学校外键；生成列约束每用户最多一条 PENDING/APPROVED |

验证码消费锁定挑战行。刷新先锁会话，再以 locking/current read 重读令牌，避免 MySQL REPEATABLE READ 旧快照误判并发重放。故意返回的认证错误不回滚尝试计数、发送失败状态和重放撤销；数据库/加密等未预期错误仍回滚。

申请只创建 PENDING，不更新 campus_user.school_id。身份加密使用随机 IV、userId 作为 AAD；普通 API 不返回原文，第二阶段独立审核接口按 campus:review 授权、审计并 no-store。IDENTITY_KEY_BASE64 必须持久安全保存；现有密文没有多版本密钥元数据，密钥轮换需要专门的数据迁移，不能直接换键启动。

expires_at 当前负责逻辑过期，历史验证码/令牌的物理清理尚未安排定时任务；后续按审计与隐私保留策略增加有上限的分批清理，不能直接清空活跃记录。并发测试中少量已提交的临时数据按唯一测试手机号精确清理，其余测试事务回滚。

## V3 商家、文件与审核（已执行）

V3完成时为25张业务表 + flyway_schema_history。V1/V2未修改；当前V4结果见下节。

| 表/变更 | 用途与约束 |
| --- | --- |
| campus_file_object | owner/purpose、随机临时与封存 key、声明大小、PENDING/READY、期限；封存 key 唯一；业务 ID 不含存储地址 |
| campus_merchant_application | 不可变申请快照与资质 FK；生成列唯一限制每人一个 PENDING；经纬度成对；获批关联门店唯一 |
| campus_merchant_review | 每申请唯一决定、审核员/原因/时间；不覆盖旧审核 |
| campus_merchant_status_change | 门店暂停/恢复、前后状态、版本与原因 |
| campus_audit_event | 事务内关键操作、操作者、资源、精简 JSON；不含令牌、身份原文、对象 key/签名 URL |
| campus_campus_verification_review | 校园申请唯一审核记录；通过才更新 user.school_id |
| campus_merchant | 新增 qualification_file_id FK、description、business_hours；完整实体映射 |
| campus_merchant_member | 新增 created_at；保留门店/用户联合唯一关系 |

审核先锁申请人账号，再锁申请，防止并发重复审批；只有活跃账号可获批，被停用账号仍允许驳回。商家审批通过时同事务创建门店、OWNER、审核记录和审计。店员配额按门店行锁串行；更新/暂停使用 version CAS，过期版本 409。测试只用有限并发，不是压测。

READY 文件不再调用封存操作；仅验证拥有者/用途后可关联申请。PENDING 过期按逻辑到期处理；自动清理、外部封存孤儿对象回收与文件保留策略尚未实现。不能直接删除已被申请引用的资质。学校/管理员/权限未自动播种。

## V4 优惠、核销与通知（已执行）

当前30张业务表、flyway_schema_history及1个只读视图；V1至V4均已执行，后续只新增V5+。

| 表/变更 | 用途 |
| --- | --- |
| campus_coupon | 新增description/campus_only、内容审核状态/当前请求引用、首次发布时间 |
| campus_coupon_claim | 新增短时核销码到期/签发时间；原码摘要唯一约束继续生效，原文不入库 |
| campus_coupon_review_request | 不可变JSON内容快照、内容版本、提交者；生成列限制每优惠一个PENDING请求 |
| campus_coupon_review | 每请求唯一决定、原因、审核员/时间；不覆盖历史 |
| campus_coupon_status_change | 发布/下架的前后状态、版本与原因 |
| campus_redemption_attempt_rate | 操作人固定窗口计数；错误凭据不回滚计数 |
| campus_outbox_event | 新增lease_token/locked_until/last_error_code；PENDING→PROCESSING→PUBLISHED或重试/FAILED |
| campus_notification | 持久本人通知、已读时间，event_id/user_id唯一 |
| campus_coupon_catalog（视图） | 只读优惠/门店公开字段投影；未审核/无发布时间、暂停/过期/下架不公开 |

优惠、审核请求存在当前引用与历史外键，测试清理先将coupon.review_request_id设NULL，再删除审核决定/请求；不得关闭外键检查。只有未发布且非送审中草稿可修改，修改清除当前许可。已发布规则/日期/总量冻结，库存只由领取事务递减。

锁顺序、逻辑过期、限频、码轮换、下架后已领券继续使用和outbox租约语义见STAGE3.md。后台没有批量将券写成EXPIRED的任务，过期由读取和核销校验执行；物理清理及保留策略仍待设计。实际测试回滚/清理后30张业务表为空，迁移及视图保留。

## V6 社区、位置与搜索结构（已执行）

| 表/变更 | 用途与约束 |
| --- | --- |
| campus_file_object | 用途CHECK扩充COMMUNITY_IMAGE，仍使用READY封存流程 |
| campus_merchant_location | merchant_id主键，WGS84经纬度、版本/更新人；不回填未知来源旧坐标 |
| campus_post | 学校/可选商家/作者、图文内容、状态与乐观版本、发布/删除时间 |
| campus_post_image | 帖子/文件及顺序，文件和顺序均防重复 |
| campus_post_review_request | 不可变JSON快照/内容版本、审核决定和原因，生成列保证每帖单一待审 |
| campus_post_comment | 待审/已审/驳回/隐藏/删除、版本；帖子/作者/request_key唯一 |
| campus_post_like | 帖子/用户联合主键；幂等点赞关系，不手工累加计数 |
| campus_post_report | 帖子/举报人唯一；OPEN/RESOLVED/DISMISSED及处理意见/版本 |
| campus_community_moderation | 内容审核/隐藏等操作的资源、前后状态、原因与操作者历史 |
| campus_search_document | 类型/资源ID联合主键；正文/哈希、visible与递增revision；隐藏保留空墓碑 |
| campus_search_rebuild | 单一活跃任务约束、游标/计数、租约token/到期、错误码与完成时间 |
| campus_post_catalog（视图） | 仅PUBLISHED、活跃作者、可选关联门店APPROVED |
| campus_search_source（视图） | MERCHANT/COUPON/OFFER/POST公开源联合，优惠/套餐到期过滤 |

V6新增10张业务表、2个视图；V1–V5保持原内容和校验和。外部索引不是业务事实，主库源是可见性和内容校验依据。消费者使用独立短事务，测试必须按本次资源和事件ID清理其独立提交，不能依赖外围测试回滚或清空全表。图片凭据/供应商密钥不存入搜索投影，客户查询位置不持久化。MongoDB尚无集合或迁移；接入前读SEARCH-SETUP.md的跨库一致性合同。
