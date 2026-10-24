// Explicit UI contract fixtures; no database writes, remote providers or load generation.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import { createServer, preview } from 'vite'
import vue from '@vitejs/plugin-vue'
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.CAMPUS_PLAYWRIGHT_MODULE || 'playwright')
const built = process.env.CAMPUS_UI_BUILD === 'true'
const root = fileURLToPath(new URL('..', import.meta.url))
const server = built
  ? await preview({ root, configFile: false, preview: { host: '127.0.0.1', port: 0 } })
  : await createServer({ root, configFile: false, plugins: [vue()], server: { host: '127.0.0.1', port: 0 } })
let browser
try {
  if (!built) await server.listen()
  const origin = `http://127.0.0.1:${server.httpServer.address().port}`
  browser = await chromium.launch({ headless: true, executablePath: process.env.CAMPUS_BROWSER_EXECUTABLE })
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } })
  await context.addInitScript(() => sessionStorage.setItem('campus.refresh-token', 'fixture-refresh'))
  const page = await context.newPage(), errors = [], calls = []
  let portal = 'MERCHANT', permission = true, empty = false, failure = false
  page.on('pageerror', error => errors.push(error.message))
  const now = new Date().toISOString()
  const paged = items => ({ items, page: 1, size: 20, total: items.length })
  const fixture = store => ({
    scope: portal === 'ADMIN' ? 'PLATFORM' : 'MERCHANT', merchantId: store, merchantName: store === '20' ? '第二家门店' : '测试门店',
    from: '2024-06-02', to: '2024-06-03', zone: 'Asia/Shanghai', startsAt: '2024-06-01T16:00:00Z', endsAt: '2024-06-03T16:00:00Z',
    calculatedAt: now, cacheExpiresAt: now, paymentEnvironment: 'ALIPAY_SANDBOX', currency: 'CNY', allowedZones: ['Asia/Shanghai', 'UTC'], maxDays: 93,
    metrics: { claimCount: empty ? 0 : 2, couponRedemptionCount: 1, cohortRedemptionCount: empty ? 0 : 1, cohortRedemptionRate: empty ? null : '0.5000',
      createdOrderCount: 0, paidOrderCount: 0, orderRedemptionCount: 0, refundCount: 0, participatingUserCount: empty ? 0 : 2, paidAmount: '20.00', refundAmount: '42.34', netCashFlow: '-22.34' },
    daily: [{ date: '2024-06-02', claimCount: empty ? 0 : 2, couponRedemptionCount: 1, createdOrderCount: 0, paidOrderCount: 0, orderRedemptionCount: 0, refundCount: 0, paidAmount: '20.00', refundAmount: '42.34' },
      { date: '2024-06-03', claimCount: 0, couponRedemptionCount: 0, createdOrderCount: 0, paidOrderCount: 0, orderRedemptionCount: 0, refundCount: 0, paidAmount: '0.00', refundAmount: '0.00' }],
    topCoupons: empty ? [] : [{ couponId: '100', title: '<img src=x onerror="window.__analyticsXss=1">', claimCount: 2, cohortRedemptionCount: 1 }],
    orderStatuses: [], newAccounts: 2, newMerchants: 1, pageViewUvAvailable: false, pageViewUv: null, definitions: ['沙箱支付按支付时间统计，退款按成功完成时间统计。', '浏览 UV 尚未采集。'],
  })
  await page.route('**/api/v1/**', async route => {
    const req = route.request(), url = new URL(req.url()), path = url.pathname.replace('/api/v1', '')
    const respond = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (path === '/session-refreshes') return respond({ sessionId: 'fixture', accessToken: 'fixture', refreshToken: 'fixture-refresh', tokenType: 'Bearer', accessExpiresAt: new Date(Date.now()+900000).toISOString(), refreshExpiresAt: new Date(Date.now()+86400000).toISOString() }, 201)
    if (path === '/users/me') return respond({ id: '2', accountType: portal, nickname: '测试', permissions: portal === 'ADMIN' ? ['audit:read', ...(permission ? ['analytics:read'] : [])] : ['merchant:read', ...(permission ? ['merchant:analytics:read'] : [])] })
    if (path === '/assistant/capabilities') return respond({ enabled: false })
    if (path === '/users/me/merchants') return respond(paged([{ id: '10', name: '测试门店', role: permission ? 'OWNER' : 'CASHIER', status: 'APPROVED' }, { id: '20', name: '第二家门店', role: 'MANAGER', status: 'SUSPENDED' }]))
    if (path.endsWith('/dashboard')) {
      calls.push(Object.fromEntries(url.searchParams))
      if (failure) { failure = false; return respond({ detail: '统计暂不可用，请重试', status: 503 }, 503) }
      return respond(fixture(path.includes('/20/') ? '20' : '10'))
    }
    if (path === '/admin/operation-audit-events') {
      if (url.searchParams.get('outcome')) assert.equal(url.searchParams.get('outcome'), 'DENIED')
      return respond(paged([{ id: 'op-1', actorId: '2', action: 'MERCHANT_DASHBOARD_QUERIED', resourceType: 'MERCHANT', resourceId: '10', outcome: 'DENIED', httpStatus: 403, errorCode: 'FORBIDDEN', durationMillis: 4, createdAt: now }]))
    }
    throw new Error('Unmocked API '+path)
  })
  await page.goto(origin+'/merchant/dashboard')
  await page.getByRole('heading', { name: '门店经营看板', exact: true }).waitFor()
  await page.getByText('50.00%', { exact: true }).waitFor()
  await page.locator('canvas').first().waitFor()
  assert.equal(await page.locator('canvas').count(), 2)
  await page.getByText('— 未采集', { exact: true }).waitFor()
  await page.getByText('-22.34', { exact: true }).waitFor()
  await page.getByText('<img src=x onerror="window.__analyticsXss=1">', { exact: true }).waitFor()
  assert.equal(await page.evaluate(() => window.__analyticsXss), undefined)
  assert.equal(calls.length, 1)
  await page.getByRole('button', { name: '重新计算', exact: true }).click()
  await page.getByText('50.00%', { exact: true }).waitFor()
  assert.equal(calls.at(-1).refresh, 'true')
  await page.getByLabel('门店', { exact: true }).selectOption('20')
  await page.getByText('第二家门店 ·', { exact: false }).waitFor()
  await page.getByLabel('统计时区').selectOption('UTC')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await page.getByText('50.00%', { exact: true }).waitFor()
  assert.equal(calls.at(-1).zone, 'UTC')
  failure = true
  await page.getByRole('button', { name: '重新计算', exact: true }).click()
  await page.getByRole('alert').waitFor()
  await page.getByRole('button', { name: '重试', exact: true }).click()
  await page.getByText('50.00%', { exact: true }).waitFor()
  empty = true
  await page.getByRole('button', { name: '重新计算', exact: true }).click()
  await page.getByText('— 无领券', { exact: true }).waitFor()
  await page.getByText('暂无领取记录', { exact: true }).waitFor()
  await page.setViewportSize({ width: 390, height: 844 })
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth) <= 390)
  if (process.env.CAMPUS_UI_SCREENSHOT) await page.screenshot({ path: process.env.CAMPUS_UI_SCREENSHOT, fullPage: true })
  permission = false; const before = calls.length
  await page.reload()
  await page.getByRole('heading', { name: '暂无访问权限' }).waitFor()
  assert.equal(await page.getByRole('link', { name: '经营看板', exact: true }).count(), 0)
  assert.equal(calls.length, before)
  portal = 'ADMIN'; permission = true; empty = false
  await page.goto(origin+'/admin/dashboard')
  await page.getByRole('heading', { name: '平台经营看板' }).waitFor()
  await page.getByRole('heading', { name: '新增账号 / 门店' }).waitFor()
  const mobileMenu = page.getByRole('button', { name: '展开菜单', exact: true })
  if (await mobileMenu.isVisible()) await mobileMenu.click()
  await page.getByRole('link', { name: '操作结果审计', exact: true }).click()
  await page.getByRole('cell', { name: 'FORBIDDEN', exact: true }).waitFor()
  await page.getByLabel('结果', { exact: true }).selectOption('DENIED')
  await page.getByRole('button', { name: '查询结果' }).click()
  await page.getByRole('cell', { name: '403', exact: true }).waitFor()
  permission = false
  await page.goto(origin+'/admin/dashboard')
  await page.getByRole('heading', { name: '暂无访问权限' }).waitFor()
  assert.equal(await page.getByRole('link', { name: '平台经营看板', exact: true }).count(), 0)
  assert.deepEqual(errors, [])
  console.log('PASS: scoped merchant/platform dashboards, ECharts canvas, money, no-denominator rate, uncollected UV, time zone, refresh/retry, escaped titles, dynamic menus, denied deep links, audit filters and mobile bounds. Fixture APIs only.')
} finally {
  await browser?.close()
  if (built) await new Promise((resolve, reject) => server.httpServer.close(error => error ? reject(error) : resolve()))
  else await server.close()
}
