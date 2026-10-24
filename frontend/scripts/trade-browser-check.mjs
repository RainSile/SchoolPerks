// Browser contract test only. HTTP/WebSocket fixtures below never enter application code.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.CAMPUS_PLAYWRIGHT_MODULE || 'playwright')
const root = fileURLToPath(new URL('..', import.meta.url))
const server = await createServer({ root, configFile: false, plugins: [vue()], server: { host: '127.0.0.1', port: 0 } })
let browser
try {
  await server.listen()
  const port = server.httpServer.address().port
  browser = await chromium.launch({ headless: true, ...(process.env.CAMPUS_BROWSER_EXECUTABLE ? { executablePath: process.env.CAMPUS_BROWSER_EXECUTABLE } : {}) })
  const context = await browser.newContext({ viewport: { width: 1280, height: 960 } })
  await context.addInitScript(() => { if (location.protocol === 'http:' || location.protocol === 'https:') sessionStorage.setItem('campus.refresh-token', 'test-refresh') })
  const page = await context.newPage(), errors = [], apiErrors = []
  page.on('pageerror', e => errors.push(e.message))
  const now = new Date().toISOString(), expires = new Date(Date.now() + 3600000).toISOString(), starts = new Date(Date.now() - 60000).toISOString()
  let accountType = 'MERCHANT', permission = 'OWNER', paymentAvailable = false, offer = null, order = null, refunds = [], socket, requestedKey = null, creations = 0
  const store = { id: '11', name: '校园小馆', schoolId: '1', category: 'DINING', address: '东门', status: 'APPROVED', version: 0, description: '', businessHours: '', role: 'OWNER' }
  const paged = (items, url) => ({ items, total: items.length, page: Number(url.searchParams.get('page') || 1), size: 20 })
  await context.route('**/api/v1/**', async route => {
    try {
      const req = route.request(), url = new URL(req.url()), path = url.pathname.replace('/api/v1', ''), method = req.method()
      const respond = (value, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) })
      if (path === '/assistant/capabilities') return respond({ configured: false })
      if (path === '/map-capabilities') return respond({ configured: false })
      if (path === '/session-refreshes') return respond({ sessionId: 'test', tokenType: 'Bearer', accessToken: 'test-access', refreshToken: 'test-refresh', accessExpiresAt: expires, refreshExpiresAt: expires }, 201)
      if (path === '/users/me') return respond({ id: accountType === 'USER' ? '2' : '1', accountType, homePath: accountType === 'USER' ? '/app' : '/merchant', nickname: '验收用户', maskedPhone: '139****0000', permissions: accountType === 'USER' ? [] : ['merchant:read'] })
      if (path === '/users/me/merchants') return respond(paged([{ ...store, role: permission }], url))
      if (path === '/trade-capabilities') return respond({ enabled: true, paymentAvailable, paymentEnvironment: 'ALIPAY_SANDBOX' })
      if (path === '/notification-capabilities') return respond({ enabled: true, streamPath: '/api/v1/notification-stream', pingIntervalSeconds: 20, reconnectDelaySeconds: 1 })
      if (path === '/merchants/11/trade-offers') {
        if (method === 'POST') { const input = req.postDataJSON(); assert.equal(input.price, '19.90'); assert.equal(input.groupSize, 2); offer = { ...input, id: '21', merchantId: '11', status: 'DRAFT', version: 0, stockAvailable: input.stock, stockTotal: input.stock }; return respond(offer, 201) }
        return respond(paged(offer ? [offer] : [], url))
      }
      if (path === '/trade-offers/21' && method === 'PATCH') { const input = req.postDataJSON(); assert.equal(input.version, offer.version); offer.status = input.status; offer.version++; return respond(offer) }
      if (path === '/trade-offers') return respond(paged(offer?.status === 'PUBLISHED' ? [offer] : [], url))
      if (path === '/trade-offers/21/group-purchases') return respond(paged([{ id: '31', offerId: '21', targetSize: 2, reservedCount: 1, paidCount: 1, status: 'OPEN', expiresAt: expires }], url))
      if (path === '/group-purchases/31') return respond({ id: '31', offerId: '21', targetSize: 2, reservedCount: 2, paidCount: order?.status === 'GROUP_PENDING' ? 1 : 2, status: order?.status === 'GROUP_PENDING' ? 'OPEN' : 'SUCCEEDED', expiresAt: expires })
      if (path === '/orders' && method === 'POST') {
        const input = req.postDataJSON(); assert.deepEqual(input, { offerId: '21', groupId: '31' }); assert.ok(!('amount' in input)); assert.match(req.headers()['idempotency-key'], /^[a-z0-9-]{36}$/)
        // First transport result is lost; the next click must retain the idempotency key.
        if (!requestedKey) { requestedKey = req.headers()['idempotency-key']; return route.abort('failed') }
        assert.equal(req.headers()['idempotency-key'], requestedKey); creations++
        order = { id: '41', orderNo: 'CPtestorder', merchantId: '11', offerId: '21', groupId: '31', amount: offer.price, currency: 'CNY', offerSnapshot: { title: offer.title, description: offer.description, kind: 'GROUP', price: offer.price }, status: 'PENDING_PAYMENT', expiresAt: expires, paidAt: null, createdAt: now }
        return respond(order, 201)
      }
      if (path === '/orders' || path === '/merchants/11/orders') return respond(paged(order ? [order] : [], url))
      if (path === '/orders/41/payments') { assert.ok(paymentAvailable); return respond({ id: 'p1', orderId: '41', status: 'CREATED', checkoutUrl: 'https://openapi.alipaydev.com/gateway.do?test=1', expiresAt: expires }, 201) }
      if (path === '/orders/41/redemption-codes') { assert.equal(order.status, 'PAID'); return respond({ orderId: '41', code: 'a'.repeat(43), expiresAt: expires }) }
      if (path === '/merchants/11/order-redemptions') { assert.deepEqual(req.postDataJSON(), { orderId: '41', code: 'a'.repeat(43) }); order.status = 'REDEEMED'; return respond(order, 201) }
      if (path === '/orders/41/refunds') {
        if (method === 'POST') { order.status = 'REFUND_PENDING'; refunds = [{ id: 'r1', orderId: '41', amount: '19.90', status: 'FAILED', attempts: 2, lastErrorCode: 'PROVIDER_UNAVAILABLE' }]; return respond(refunds[0], 201) }
        return respond(refunds)
      }
      if (path === '/orders/41/refunds/r1/retries') { refunds[0].status = 'PENDING'; return respond(refunds[0], 202) }
      apiErrors.push(`${method} ${path}`); return respond({ status: 404, detail: 'Unexpected test request' }, 404)
    } catch (e) { apiErrors.push(e.message); return route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ status: 500, detail: e.message }) }) }
  })
  await context.routeWebSocket('**/notification-stream', ws => { socket = ws; ws.onMessage(text => { const frame = JSON.parse(text); if (frame.type === 'AUTHENTICATE') { assert.equal(frame.accessToken, 'test-access'); ws.send(JSON.stringify({ type: 'READY' })) } }) })
  const visit = path => page.goto(`http://127.0.0.1:${port}${path}`)
  await visit('/merchant/trade')
  await page.getByLabel('套餐标题').fill('双人午餐拼团')
  await page.getByLabel('套餐内容').fill('一份午餐，含饮品')
  await page.getByLabel('售卖方式').selectOption('GROUP')
  await page.getByLabel('每份价格（元）').fill('19.90')
  await page.getByLabel('库存总量').fill('4')
  const localValue = date => new Date(date - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16)
  await page.getByLabel('开始时间').fill(localValue(Date.now() - 60000)); await page.getByLabel('结束时间').fill(localValue(Date.now() + 3600000))
  await page.getByRole('button', { name: '保存套餐草稿', exact: true }).click()
  await page.getByText('套餐草稿已保存。核对价格和内容后再发布，发布后不可修改。').waitFor()
  await page.getByRole('button', { name: '发布套餐', exact: true }).click()
  await page.getByRole('button', { name: '下架套餐', exact: true }).waitFor()
  permission = 'CASHIER'; await visit('/merchant/trade'); await page.getByRole('heading', { name: '到店订单核销' }).waitFor()
  assert.equal(await page.getByRole('heading', { name: '创建套餐草稿' }).count(), 0)
  assert.equal(await page.getByRole('button', { name: '下架套餐', exact: true }).count(), 0)
  accountType = 'USER'; await visit('/trade'); await page.getByRole('button', { name: '查看可加入的团' }).click(); await page.getByRole('button', { name: '加入此团', exact: true }).click()
  await page.getByRole('status').waitFor(); await page.getByRole('button', { name: '加入此团', exact: true }).click()
  await page.waitForURL('**/account/orders'); await page.getByRole('button', { name: '准备沙箱支付' }).waitFor()
  assert.equal(creations, 1); assert.equal(await page.getByRole('button', { name: '准备沙箱支付' }).isDisabled(), true)
  paymentAvailable = true; await page.getByRole('button', { name: '刷新订单', exact: true }).click(); await page.getByRole('button', { name: '准备沙箱支付' }).click()
  await page.getByRole('link', { name: '打开支付宝沙箱收银台 ↗' }).waitFor()
  assert.match(await page.getByRole('link', { name: '打开支付宝沙箱收银台 ↗' }).getAttribute('href'), /^https:\/\/openapi\.alipaydev\.com\//)
  order.status = 'GROUP_PENDING'; socket.send(JSON.stringify({ eventId: 'e1', type: 'ORDER_PAID', payload: { orderId: '41' } }))
  await page.getByText('已支付，等待成团', { exact: true }).waitFor(); assert.equal(await page.getByRole('button', { name: '生成到店核销码' }).count(), 0)
  order.status = 'PAID'; socket.send(JSON.stringify({ eventId: 'e2', type: 'ORDER_GROUP_SUCCEEDED', payload: { orderId: '41' } }))
  await page.getByRole('button', { name: '生成到店核销码' }).click(); await page.getByText('a'.repeat(43), { exact: true }).waitFor()
  const storage = await page.evaluate(() => JSON.stringify({ local: localStorage, session: sessionStorage })); assert.ok(!storage.includes('a'.repeat(43)))
  await page.setViewportSize({ width: 390, height: 844 }); assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'order UI must fit mobile')
  if (process.env.CAMPUS_UI_SCREENSHOT) await page.screenshot({ path: resolve(process.env.CAMPUS_UI_SCREENSHOT), fullPage: true })
  await page.getByRole('button', { name: '申请全额退款' }).click(); await page.getByRole('button', { name: '重试退款', exact: true }).click(); await page.getByText(/退款 等待处理/).waitFor()
  // A separate paid fixture exercises the cashier form, not a real refund reversal.
  order.status = 'PAID'; refunds = []; accountType = 'MERCHANT'; await visit('/merchant/trade')
  await page.getByLabel('顾客订单编号').fill('41'); await page.getByLabel('顾客出示的短期核销码').fill('a'.repeat(43)); await page.getByRole('button', { name: '确认到店核销', exact: true }).click(); await page.getByText('订单 41 核销成功。').waitFor()
  assert.deepEqual(errors, []); assert.deepEqual(apiErrors, [])
  console.log('PASS: merchant draft/publish, cashier scope, group join, retained retry idempotency key, missing sandbox, safe checkout link, live paid/group state, memory-only code, refund/retry, cashier redemption and mobile layout. HTTP/WebSocket are test mocks.')
} finally { await browser?.close(); await server.close() }
