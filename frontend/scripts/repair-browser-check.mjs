// Browser regression for exclusive portals, RBAC forms and the persistent assistant.
// All HTTP responses are fixtures; this script never calls a real model.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.CAMPUS_PLAYWRIGHT_MODULE || 'playwright')
const server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false, plugins: [vue()], server: { host: '127.0.0.1', port: 0 } })
let browser
try {
  await server.listen()
  browser = await chromium.launch({ headless: true, executablePath: process.env.CAMPUS_BROWSER_EXECUTABLE })
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } })
  await context.addInitScript(() => sessionStorage.setItem('campus.refresh-token', 'fixture-refresh'))
  const page = await context.newPage(), errors = [], requests = [], conversations = new Map()
  page.on('pageerror', e => errors.push(e.message))
  let type = 'USER', role = null, targetVersion = 0, assigned = false
  const home = () => ({ USER: '/app', MERCHANT: '/merchant', ADMIN: '/admin' })[type]
  const actor = () => ({ USER: '1', MERCHANT: '2', ADMIN: '3' })[type]
  const expiry = new Date(Date.now() + 3600000).toISOString()
  const target = () => ({ id: '4', nickname: '待授权管理员', accountType: 'ADMIN', status: 'ACTIVE', version: targetVersion, roleIds: assigned ? ['role-1'] : [] })
  await context.route('**/api/v1/**', async route => {
    const req = route.request(), url = new URL(req.url()), path = url.pathname.replace('/api/v1', ''), method = req.method()
    const respond = (value, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) })
    const paged = items => ({ items, total: items.length, page: 1, size: 20 })
    requests.push({ path, method, actor: actor() })
    if (path === '/session-refreshes') return respond({ sessionId: 'fixture-session', tokenType: 'Bearer', accessToken: 'fixture-access', refreshToken: 'fixture-refresh', accessExpiresAt: expiry, refreshExpiresAt: expiry }, 201)
    if (path === '/users/me') return respond({ id: actor(), accountType: type, homePath: home(), nickname: '独立端测试', maskedPhone: '139****0000', permissions: type === 'ADMIN' ? ['rbac:manage'] : type === 'MERCHANT' ? ['merchant:read'] : [] })
    if (path === '/assistant/capabilities') return respond({ enabled: true, accountType: type, maxMessageLength: 2000, timeoutSeconds: 10, tools: [] })
    if (path === '/assistant/conversations' && method === 'POST') { const id = `conversation-${actor()}`; conversations.set(id, []); return respond({ id, accountType: type }, 201) }
    const messages = path.match(/^\/assistant\/conversations\/([^/]+)\/messages$/)
    if (messages) {
      assert.equal(messages[1], `conversation-${actor()}`, 'Conversation must stay scoped to the active identity')
      if (method === 'GET') return respond(conversations.get(messages[1]) || [])
      assert.ok(req.headers()['idempotency-key'])
      await new Promise(resolve => setTimeout(resolve, 250))
      const row = { id: `message-${actor()}`, conversationId: messages[1], status: 'COMPLETED', question: req.postDataJSON().message, reply: '<script>window.__assistantXss=1</script> 安全回答', tools: [], sources: [], errorCode: null }
      conversations.get(messages[1]).push(row); return respond(row, 201)
    }
    if (path === '/admin/permissions') return respond([{ code: 'community:moderate', name: '社区审核' }, { code: 'rbac:manage', name: '权限管理' }])
    if (path === '/admin/roles') {
      assert.equal(type, 'ADMIN')
      if (method === 'POST') { const data = req.postDataJSON(); assert.equal(data.accountType, 'ADMIN'); assert.deepEqual(data.permissions, ['community:moderate']); role = { ...data, id: 'role-1', version: 0 }; return respond(role, 201) }
      return respond(paged(role ? [role] : []))
    }
    if (path === '/admin/accounts') return respond(paged([target()]))
    if (path === '/admin/accounts/4/roles') { const body = req.postDataJSON(); assert.equal(body.version, targetVersion); assert.deepEqual(body.roleIds, ['role-1']); assigned = true; targetVersion++; return respond(target()) }
    if (path === '/coupons' || path === '/posts' || path === '/users/me/merchants') return respond(paged([]))
    if (path === '/schools' || path === '/merchant-categories') return respond([])
    if (path === '/storage-capabilities' || path === '/map-capabilities') return respond({ available: false, configured: false })
    if (path === '/sessions/current') return route.fulfill({ status: 204 })
    throw new Error(`Unmocked fixture API: ${method} ${path}`)
  })
  const origin = `http://127.0.0.1:${server.httpServer.address().port}`
  const visit = path => page.goto(origin + path)
  await visit('/login')
  await page.waitForURL(origin + '/app')
  await page.getByRole('navigation', { name: '用户端导航' }).waitFor()
  assert.equal(await page.getByRole('link', { name: '商家工作台', exact: true }).count(), 0)
  await visit('/admin/rbac'); await page.waitForURL(origin + '/app')
  await page.getByRole('button', { name: '打开智能助手' }).click()
  await page.getByLabel('向助手提问').fill('查询当前账号优惠')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await page.getByText('<script>window.__assistantXss=1</script> 安全回答', { exact: true }).waitFor()
  assert.equal(await page.evaluate(() => window.__assistantXss), undefined)
  await page.getByRole('link', { name: '探店社区', exact: true }).click()
  await page.getByRole('dialog', { name: '校园生活助手' }).waitFor()
  await page.getByText('查询当前账号优惠', { exact: true }).waitFor()
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: '打开智能助手' }).waitFor()
  type = 'MERCHANT'; await visit('/login'); await page.waitForURL(origin + '/merchant')
  await page.getByRole('navigation', { name: '商家端导航' }).waitFor()
  await visit('/account/orders'); await page.waitForURL(origin + '/merchant')
  await page.getByRole('button', { name: '打开智能助手' }).click()
  await page.getByRole('dialog', { name: '门店经营助手' }).waitFor()
  assert.equal(await page.getByText('查询当前账号优惠', { exact: true }).count(), 0)
  await page.getByRole('button', { name: '收起助手', exact: true }).click()
  type = 'ADMIN'; await visit('/login'); await page.waitForURL(origin + '/admin')
  await page.getByRole('navigation', { name: '管理端导航' }).waitFor()
  await visit('/admin/rbac')
  await page.getByRole('heading', { name: '角色与账号权限' }).waitFor()
  await page.getByLabel('角色代码').fill('COMMUNITY_REVIEWER')
  await page.getByLabel('角色名称').fill('社区审核员')
  await page.getByLabel('社区审核 community:moderate').check()
  await page.getByRole('button', { name: '保存角色', exact: true }).click()
  await page.getByText('已保存。受影响账号需重新登录。').waitFor()
  await page.getByRole('button', { name: '管理授权' }).click()
  await page.getByLabel('社区审核员', { exact: true }).check()
  await page.getByRole('button', { name: '保存角色分配' }).click()
  await page.waitForFunction(() => document.querySelector('.notice')?.textContent.includes('已保存'))
  assert.equal(assigned, true)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.getByRole('button', { name: '打开智能助手' }).click()
  const bounds = await page.getByRole('dialog', { name: '平台管理助手' }).boundingBox()
  assert.ok(bounds.x >= 0 && bounds.x + bounds.width <= 390 && bounds.y >= 0)
  await page.waitForFunction(() => document.querySelector('.assistant-panel') && getComputedStyle(document.querySelector('.assistant-panel')).opacity === '1')
  if (process.env.CAMPUS_UI_SCREENSHOT) await page.screenshot({ path: process.env.CAMPUS_UI_SCREENSHOT, fullPage: true })
  assert.deepEqual(errors, [])
  assert.ok(!requests.some(r => r.path.startsWith('/admin/') && r.actor !== '3'))
  console.log('PASS: exclusive portal login/redirect/layout, RBAC role/grant forms, persistent assistant, identity isolation, escaped model text, mobile bounds. Test fixture APIs only.')
} finally { await browser?.close(); await server.close() }
