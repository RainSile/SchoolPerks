# 阿里云号码认证短信接入与验收

日期：2026-10-28。产品为 **PNVS 号码认证**，使用 `SendSmsVerifyCode` 和 `CheckSmsVerifyCode`，不是普通短信产品的 `SendSms`。

## 已接入的业务

| 本站场景 | 对外业务接口 | 赠送模板配置 | 独立核验方案配置 |
| --- | --- | --- | --- |
| 验证码登录 | 创建 LOGIN 挑战 → POST `/api/v1/sessions` | `ALIYUN_PNVS_LOGIN_TEMPLATE_CODE`，本次 100001 | `ALIYUN_PNVS_LOGIN_SCHEME` |
| 注册 | 创建 REGISTER 挑战 → POST `/api/v1/registrations` | `ALIYUN_PNVS_REGISTER_TEMPLATE_CODE`，本次 100001 | `ALIYUN_PNVS_REGISTER_SCHEME` |
| 忘记密码 | 创建 RESET_PASSWORD 挑战 → POST `/api/v1/password-resets` | `ALIYUN_PNVS_RESET_TEMPLATE_CODE`，本次 100003 | `ALIYUN_PNVS_RESET_SCHEME` |

挑战创建仍为 POST `/api/v1/verification-challenges`；原密码登录、注册、找回密码表单及 OpenAPI 合同保留，不新增绕过认证的调试接口。签名读取私有 `ALIYUN_PNVS_SIGN_NAME`，严格使用用户指定的赠送签名，不在业务代码中写入具体名称。

## 生成、核验与隔离

发送时使用 `##code##`，由阿里云生成验证码；默认 6 位、有效期 5 分钟，长度与有效期仍由业务配置传入。**生产请求固定 `ReturnVerifyCode=false`**，HTTP 不返回验证码，本地不存其明文或哈希。

本地保留随机挑战 ID、手机号、用途、过期时间、错误次数、消费状态，以及供应商类型／流水／核验方案。核验先锁定挑战并检查手机号、用途、期限、状态和尝试次数，满足条件后才调用云端；必须得到 `VerifyResult=PASS` 才允许认证，普通 `OK/Success` 不代表验证码正确。实际观察到的 `isv.ValidateFail` 按验证码错误处理，不误报为服务不可用。

三个用途必须使用不同 SchemeName，并在发送和核验时保持一致；OutId 使用本站挑战 ID。供应商流水是留存元数据，不冒充接口未提供的核验绑定参数。旧挑战、跨用途或跨手机号请求不能绕过本地检查，已消费挑战不再次调用云端。停用／更换供应商不能退回本地哈希核验；启用远程服务后也不接受旧的本地模拟挑战。

云端验证码已消费而本地后续事务发生意外回滚时，可能需要重新获取验证码；系统选择拒绝重放，不以缓存一次 PASS 的方式绕过核验。

## 配置与上线

公开配置默认关闭，真实值保存在忽略目录的 DPAPI 加密配置中；已将本机私有配置切换到 PNVS 并关闭模拟。**本轮是实现、测试和 Git 发布，未替换当前云端部署**。云端需要部署包含 V13 的新 JAR，并将以下项写入受限后端环境后再启用：

```dotenv
ALIYUN_PNVS_ENABLED=true
ALIYUN_PNVS_ENDPOINT=https://dypnsapi.aliyuncs.com
ALIYUN_PNVS_ACCESS_KEY_ID=private-value
ALIYUN_PNVS_ACCESS_KEY_SECRET=private-value
ALIYUN_PNVS_SIGN_NAME=approved-gift-signature
ALIYUN_PNVS_LOGIN_TEMPLATE_CODE=100001
ALIYUN_PNVS_REGISTER_TEMPLATE_CODE=100001
ALIYUN_PNVS_RESET_TEMPLATE_CODE=100003
ALIYUN_PNVS_LOGIN_SCHEME=campus-login
ALIYUN_PNVS_REGISTER_SCHEME=campus-register
ALIYUN_PNVS_RESET_SCHEME=campus-reset
SMS_SIMULATED_ENABLED=false
SMS_CODE_LENGTH=6
```

Endpoint、模板、方案、密钥、连接及读取超时均可配置；方案只能为不重复、最多 20 字符的安全名称。网络超时有上界，客户端不自动重试发送；`AutoRetry=0` 禁用供应商更换签名重试，以满足指定签名与费用控制要求。沿用本站手机号、IP、重发间隔和错误次数限制。接收真实短信需要账户具备相应权限与套餐；最小 API 权限为 `dypns:SendSmsVerifyCode`、`dypns:CheckSmsVerifyCode`。

本次 Flyway **V13** 只追加远程核验元数据并允许远程挑战的 `code_hash` 为 NULL，原 V1–V12 不修改；已有本地挑战保留 LOCAL 类型及哈希。改回旧应用包前应关闭新短信入口；不可为了回退旧包删除 V13 或覆盖已有数据。

## 依赖与兼容性

使用 Maven Central 已发布的 `com.aliyun:dypnsapi20170525:2.0.0` 普通 SDK。官方 shaded 包实测包含第二份 dom4j，因此未采用，也未放宽原有唯一实现检查。Tea 统一为 1.3.1、credentials-java 为 1.0.1；XML／加密库沿用项目现有 dom4j 2.1.4、BouncyCastle 1.81。原 MinIO、LangChain4j、OkHttp、Kotlin 等版本不变。依赖收敛规则增加阿里云、XML、加密库检查；实际编译和完整 SDK 回归通过。

## 测试结果与费用边界

- 原认证 11 项、密码认证 10 项及新增远程认证 7 项，共 **28 项真实 MySQL 测试通过**；短信模拟覆盖错误码、重复消费、跨手机号／用途、期限、错误次数、供应商切换、服务不可用和发送失败。
- **一次真实业务专项通过**：三种用途共发起 **3 次短信发送**，完成注册、密码登录、短信登录、验证码重放拒绝、密码重置、旧密码失效、新密码可用及旧会话撤销；经真实 SDK 调用阿里云核验。
- 真实专项只允许用户提供的一个手机号，发送前持久化锁定预算，每次保守预留 3 条分段额度，总上限 50，失败也计预留，不自动补发。本次共预留 9／50 安全额度，不表示实际计费 9 条；长短信以运营商拆分及账单为准。
- 为不让用户在聊天中传递验证码，**只有显式启用的测试 SDK 子类**临时请求供应商调试返回，在进程内使用并清除；业务适配器原始请求仍断言为 false，供应商响应中的验证码被测试层移除，测试也断言 HTTP 未泄露。生产实现无可开启这种行为的配置项。
- 业务 SQL 测试全部事务回滚；迁移感知快照核对 56 张表的原业务内容一致，V13 合法新增元数据和迁移记录单独处理，未把它们误算为原业务写入。
- 普通 `verify` 不执行真实短信专项；业务测试／打包脚本显式关闭 PNVS 和实时短信测试。真实专项必须同时显式启用 `CAMPUS_PNVS_LIVE_TEST`、提供私有凭据、允许的测试手机号和私有绝对路径预算文件，否则不会发送。

2026-10-28 用户确认实际收到 **2 条**，本次共发起 3 次发送，另 1 次的手机收件尚未确认。因此接口受理及核验专项通过，**手机收件验收为 2／3，尚未全部确认**。已有私有日志与成功标记可证明业务专项完成，但没有保存足够的逐条投递回执，无法据此判断是哪一次未收到及其原因；不将其直接归因为代码缺陷或供应商故障。记录为待核实的验收差异，暂不补发，发送次数仍为 3，预算预留仍为 9／50。后续核对应优先使用供应商发送记录及手机接收记录；API 受理及核验通过不能替代手机收件确认。原始测试手机号、密钥、验证码、密码、日志及附件 HTML 不发布到 Git。

官方参考：[发送验证码](https://help.aliyun.com/zh/pnvs/developer-reference/api-dypnsapi-2017-05-25-sendsmsverifycode)、[核验验证码](https://help.aliyun.com/zh/pnvs/developer-reference/api-dypnsapi-2017-05-25-checksmsverifycode)、[SDK 发布元数据](https://repo.maven.apache.org/maven2/com/aliyun/dypnsapi20170525/maven-metadata.xml)。
