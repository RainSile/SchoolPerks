# 支付宝沙箱配置与验证

更新：2026-10-28。

云端已部署受信任 HTTPS 入口并启用私有沙箱配置，真实虚拟付款、签名回调及退款通过，见[云端验收与付款方式边界](ALIPAY-CLOUD-ACCEPTANCE.md)、[部署与迁移](CLOUD-DEPLOYMENT.md)。本机加密配置保持关闭，避免本机订单通知误入云端库。下文“尚未部署”描述属于 10 月 27 日本机验证时的历史状态，不能据此判断云端支付能力。

## 当前状态

用户提供的沙箱应用、商家PID、应用PKCS8私钥、支付宝公钥及沙箱网关已写入本机既有DPAPI加密应用配置。应用公钥用于校验私钥配对；沙箱商家/买家的登录与支付密码另行加密保存，不注入业务后端，更不传给前端。

已验证应用密钥配对、双方RSA公钥格式及2048位长度，并使用项目现有支付宝SDK向真实沙箱发出一次随机不存在订单的只读查询。返回 `40004 / ACQ.TRADE_NOT_EXIST`，符合预期；同时对原始响应JSON片段进行RSA2验签通过。这证明签名请求被真实沙箱接受，不表示已完成付款或已核实一笔真实交易。

尚未部署可接收异步通知的公网HTTPS后端，因此 `ALIPAY_SANDBOX_ENABLED=false`，没有填入虚假的回调地址。正常启动不会因为缺少回调而失败，支付能力继续明确显示不可用；这不是“付款已开通”。

## 运行配置

公开配置仍为环境变量，实际值只在私有加密文件中：

| 变量 | 状态 |
| --- | --- |
| ALIPAY_SANDBOX_APP_ID | 已配置 |
| ALIPAY_SANDBOX_SELLER_ID | 已配置 |
| ALIPAY_SANDBOX_PRIVATE_KEY | 已配置，应用私钥 |
| ALIPAY_SANDBOX_PUBLIC_KEY | 已配置，支付宝公钥 |
| ALIPAY_SANDBOX_GATEWAY | 已配置用户提供的官方沙箱地址 |
| ALIPAY_SANDBOX_NOTIFY_URL | 尚未配置，等待公网HTTPS入口 |
| ALIPAY_SANDBOX_ENABLED | false，等待回调入口完成 |

应用配置保存在忽略目录 `.local/private-environment.clixml`，沙箱凭据备份及测试账号保存在 `.local/alipay/` 的加密文件中。DPAPI与本机用户绑定，部署到其他主机时需通过私有环境注入，不能直接搬运加密文件当作可跨机器读取的配置。买家登录和支付密码仅供沙箱付款操作，服务端API不需要它们。

本机预览启动器已改为尊重私有环境中的支付开关，不再无条件覆盖为false。它仍会在启动前端进程前移除后端及供应商凭据。默认业务测试显式关闭真实支付宝与实时沙箱检查，避免以后启用支付时意外触发远端服务。

## 完成部署后启用

准备一个支付宝服务器能访问、证书有效的HTTPS后端入口，设置：

```dotenv
ALIPAY_SANDBOX_NOTIFY_URL=https://<public-backend-host>/api/v1/payment-notifications/alipay-sandbox
ALIPAY_SANDBOX_ENABLED=true
```

地址指向后端通知接口；前端可以独立部署，也可以继续本机访问。反向代理需要保留POST表单，不应要求支付宝携带本站Bearer令牌。接口本身使用RSA2签名、应用ID、商家PID、金额、交易号与幂等校验确认通知；不能通过浏览器跳转或模拟请求确认支付成功。

配置后重启后端，检查支付能力，再使用沙箱买家完成真实下单→收银台付款→异步通知→订单更新→查单/退款等专项。当前没有启用定时任务、没有支付或退款、没有部署常驻前后端，也未修改已有订单状态。此前完整支付宝交易验收待办保持开放。

## 本轮验证

| 检查 | 结果 |
| --- | --- |
| .NET密钥校验 | PKCS8私钥与应用公钥匹配；支付宝公钥解析；RSA2048 |
| AlipaySandboxLiveTest | 2项通过：真实配置签名校验；真实只读查询及支付宝响应验签 |
| AlipaySandboxAdapterTest | 5项通过，包含RSA2、篡改/应用/商家拒绝、沙箱地址限制、运行库及配置脱敏 |
| TradeStageFourMySqlTest | 21项通过，保留显式支付替身验证订单、支付、取消、团购、退款等状态机 |
| ScaffoldContractTest | 7项通过 |
| 数据与打包 | 相关回归33项无失败/错误/跳过，Java17 verify及JAR打包通过；56表摘要差异0 |

两项真实检查与33项相关回归分开统计，不把本地支付替身当成真实付款证据。实际API SDK版本仍为 `com.alipay.sdk:alipay-sdk-java:4.40.934.ALL`，未升级Maven依赖。`AlipaySandboxProperties.toString()` 已脱敏，避免输出完整密钥。

原始证据在 `.local/alipay/` 及对应 `.local/business-tests/alipay-config-*`，不直接上传日志、XML、凭据或买家信息。后续复验真实只读检查需显式设置 `CAMPUS_ALIPAY_LIVE_TEST=true`，通过私有环境提供上述应用配置及测试用 `CAMPUS_ALIPAY_APP_PUBLIC_KEY`；必要时用 `CAMPUS_PAYMENT_PROXY_HOST/PORT` 指定测试代理。该测试只查询新生成的随机订单号，不创建订单，不付款。
