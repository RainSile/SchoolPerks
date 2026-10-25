import fs from 'node:fs/promises'
import path from 'node:path'
const inventory = process.argv[2]
if (!inventory) throw new Error('Supply the inventory JSON exported by the assistant catalogue integration test.')
const apis = JSON.parse(await fs.readFile(inventory, 'utf8'))
const output = process.argv[3] || 'docs/ASSISTANT-BUSINESS-APIS.md'
const groups = { USER: '学生端', MERCHANT: '商家端', ADMIN: '管理端', SHARED: '共享的本人资料与公共能力' }
const lines = ['# 智能助手业务 API 与权限', '', '本表由实际注册的业务 Controller 生成，按 HTTP 方法计数；不是承诺所有外部供应商已经配置。', '',
  '当前目录共 **'+apis.length+' 个业务接口**。运行时以鉴权后的 `GET /api/v1/assistant/business-apis` 为准；工具执行前重新校验目录权限，随后携带本人 JWT 调用真实 API。', '',
  '## 权限和身份边界', '',
  '- 学生：USER 与 SHARED；只能处理本人订单、券包、帖子等私有资源。',
  '- 商家：MERCHANT 与 SHARED；必须是相应门店的有效成员，店主、经理、收银员仍受业务服务中的角色限制。不得调用学生端或管理端工具。',
  '- 平台管理员：具有 `business:manage` 的真实 ADMIN 身份可使用全部目录，仍保留审核状态、版本、库存、不能自审等校验。身份及审计记录保持 ADMIN，不冒充学生或商家。',
  '- 受限管理员：只开放 ADMIN/SHARED 中已授予细分权限的接口，不因 ADMIN 标签自动获得跨端能力。V12 将现有具备 `rbac:manage` 的 ADMIN 角色显式授予 `business:manage`；新角色应明确分配。',
  '- “本人”接口始终表示当前实际账号；管理员也不能读取其他用户的助手会话、会话历史或凭据。跨端能力不等于跨用户会话访问权。', '',
  '## 多步操作与重试', '',
  '`business_api_catalog` 按关键词返回当前权限内的接口摘要，按 operationId 返回 path/query/body schema；`execute_business_api` 只接受目录中的 operationId，路径和方法固定在服务端。path/query/body 是 JSON 对象字符串，不能传入 URL、JWT 或指定执行身份。', '',
  '每条消息默认只读。用户勾选“允许本次业务操作”后，仅执行该消息明确要求的写操作。模型先查询真实资源 ID、版本及状态，按顺序提交，再通过查询确认结果。', '',
  'MySQL 保存每个 turn 的写操作尝试和脱敏结果。同一幂等键的完成回答不再计费；同一 turn 的已完成写操作复用原结果，结果不确定的操作阻止盲目重放和后续写入。断开输出不会撤销已经提交的业务操作，应先查询实际结果。', '',
  '## 目录之外及资源限制', '',
  '登录、验证码、注册、密码/凭据、会话刷新/退出、支付平台回调、地图 SDK 代理、通知长连接及助手递归调用不作为模型业务工具。身份原文、口令、核销秘密、预签名 URL 等返回字段脱敏。', '',
  '上传文件的实际二进制传输、打开支付收银台、出示核销码需要用户界面配合；工具不能代替真人完成这些界面步骤。短信、对象存储或支付供应商未配置时，真实 API 的不可用状态会原样按安全结构反馈，不能宣称已经完成。', '',
  '## 流式协议', '',
  '`POST /api/v1/assistant/conversations/{id}/message-streams`，Bearer 与 Idempotency-Key 必填；请求 `{ "message": "…", "allowWrites": false }`。事件：started（消息 ID）、round（清空当前轮草稿）、delta（增量文本）、tool（工具名）、completed（持久化消息）、error（安全错误码）。', '',
  '页面通过 fetch 读取真正的 SSE 分片，支持中止、中文 UTF-8 跨片、Markdown 标题/列表/表格/代码/安全链接；禁用 HTML 与远程图片，渲染前使用 DOMPurify 清理。账号切换或失效清除本次草稿和可见历史。JSON 非流式接口继续可用。', '']
for (const [portal, label] of Object.entries(groups)) {
  const entries = apis.filter(api => api.portal === portal)
  lines.push(`## ${label}（${entries.length} 项）`, '', '| 方法 | API | 功能 | 额外权限 / 范围 |', '| --- | --- | --- | --- |')
  for (const api of entries) {
    const permission = api.permission ? `\`${api.permission}\`` : portal === 'MERCHANT' ? '门店成员及操作角色' : portal === 'USER' ? '公开查询 / 本人资源校验' : '公开能力 / 当前本人'
    lines.push(`| ${api.method} | \`${api.path}\` | ${api.summary.replaceAll('|', '／')} | ${permission} |`)
  }
  lines.push('')
}
lines.push('## 维护与复现', '', '先运行 `AssistantIsolationMySqlTest#catalogReflectsExactPortalAndDatabaseBackedPlatformGrants` 并设置 `CAMPUS_ASSISTANT_INVENTORY` 导出 JSON，再运行：', '', '```sh', 'node scripts/generate-assistant-api-docs.mjs <inventory.json>', '```', '', '目录测试同时核对三端过滤、管理员扩展授权、敏感端点排除和参数 schema。表中的细分权限不是资源归属授权的替代品；所有真实业务服务仍执行各自的权限和归属检查。', '')
await fs.mkdir(path.dirname(output), { recursive: true }); await fs.writeFile(output, lines.join('\n'))
console.log(`Generated ${output}: ${apis.length} registered business operations.`)
