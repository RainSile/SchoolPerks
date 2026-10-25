// Business-only variant: real Vue pages and fixture HTTP; assistant is disabled and never exercised.
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
  const page = await context.newPage(), errors = [], requests = []
  page.on('pageerror', error => errors.push(error.message))
  let type = 'USER', role = null, targetVersion = 0, assigned = false
  const home = () => ({ USER: '/app', MERCHANT: '/merchant', ADMIN: '/admin' })[type]
  const actor = () => ({ USER: '1', MERCHANT: '2', ADMIN: '3' })[type]
  const expiry = new Date(Date.now() + 3600000).toISOString()
  const target = () => ({ id: '4', nickname: '待授权管理员', accountType: 'ADMIN', status: 'ACTIVE', version: targetVersion, roleIds: assigned ? ['role-1'] : [] })
  await context.route('**/api/v1/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname.replace('/api/v1', ''), method = request.method()
    const respond = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    const paged = items => ({ items, total: items.length, page: 1, size: 20 })
    requests.push({ path, method, actor: actor() })
    if (path === '/session-refreshes') return respond({ sessionId: 'fixture-session', tokenType: 'Bearer', accessToken: 'fixture-access', refreshToken: 'fixture-refresh', accessExpiresAt: expiry, refreshExpiresAt: expiry }, 201)
    if (path === '/users/me') return respond({ id: actor(), accountType: type, homePath: home(), nickname: '独立端测试', maskedPhone: '139****0000', permissions: type === 'ADMIN' ? ['rbac:manage'] : type === 'MERCHANT' ? ['merchant:read'] : [] })
    if (path === '/assistant/capabilities') return respond({ enabled: false })
    assert.ok(!path.startsWith('/assistant/'), 'Assistant flows are outside this test run')
    if (path === '/admin/permissions') return respond([{ code: 'community:moderate', name: '社区审核' }, { code: 'rbac:manage', name: '权限管理' }])
    if (path === '/admin/roles') {
      assert.equal(type, 'ADMIN')
      if (method === 'POST') {
        const input = request.postDataJSON()
        assert.equal(input.accountType, 'ADMIN')
        assert.deepEqual(input.permissions, ['community:moderate'])
        role = { ...input, id: 'role-1', version: 0 }
        return respond(role, 201)
      }
      return respond(paged(role ? [role] : []))
    }
    if (path === '/admin/accounts') return respond(paged([target()]))
    if (path === '/admin/accounts/4/roles') {
      const input = request.postDataJSON()
      assert.equal(input.version, targetVersion)
      assert.deepEqual(input.roleIds, ['role-1'])
      assigned = true; targetVersion++
      return respond(target())
    }
    if (['/coupons', '/posts', '/uploads', '/users/me/merchants', '/users/me/merchant-applications'].includes(path)) return respond(paged([]))
    if (['/schools', '/merchant-categories'].includes(path)) return respond([])
    if (path === '/storage-capabilities') return respond({ enabled: true, available: false, maxBytes: 10485760, allowedTypes: ['application/pdf'], uploadTtlSeconds: 300, downloadTtlSeconds: 120 })
    if (path === '/map-capabilities') return respond({ configured: false })
    if (path === '/sessions/current') return route.fulfill({ status: 204 })
    throw new Error(`Unmocked business fixture: ${method} ${path}`)
  })
  const origin = `http://127.0.0.1:${server.httpServer.address().port}`
  for (const accountType of ['USER', 'MERCHANT', 'ADMIN']) {
    type = accountType
    await page.goto(origin + '/login')
    await page.waitForURL(origin + home())
    await page.getByRole('navigation', { name: { USER: '用户端导航', MERCHANT: '商家端导航', ADMIN: '管理端导航' }[type] }).waitFor()
    const forbiddenPath = { USER: '/admin/rbac', MERCHANT: '/account/orders', ADMIN: '/merchant/trade' }[type]
    await page.goto(origin + forbiddenPath)
    await page.waitForURL(origin + home())
  }
  await page.goto(origin + '/admin/rbac')
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
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
  assert.deepEqual(errors, [])
  assert.ok(!requests.some(request => request.path.startsWith('/admin/') && request.actor !== '3'))
  type = 'USER'
  await page.goto(origin + '/')
  await page.waitForURL(origin + '/app')
  await page.goto(origin + '/account/merchant-application')
  await page.getByRole('heading', { name: '商家入驻申请', exact: true }).waitFor()
  await page.goto(origin + '/account/merchant-applications')
  await page.getByRole('heading', { name: '我的入驻申请', exact: true }).waitFor()
  await page.goto(origin + '/business-test-unknown-page')
  await page.getByRole('heading', { name: '页面不存在', exact: true }).waitFor()
  assert.deepEqual(errors, [])
  console.log('PASS: three exclusive portals, cross-portal route guards, RBAC role/grant payloads, mobile width. HTTP fixtures only; assistant disabled.')
} finally {
  await browser?.close()
  await server.close()
}
