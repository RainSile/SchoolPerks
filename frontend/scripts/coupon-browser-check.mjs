// UI contract check: HTTP and WebSocket responses below are test-only mocks.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'
import { createServer as createNetServer } from 'node:net'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.CAMPUS_PLAYWRIGHT_MODULE || 'playwright')
const root = fileURLToPath(new URL('..', import.meta.url))
const probe = createNetServer()
await new Promise(resolve => probe.listen(0, '127.0.0.1', resolve))
const port = probe.address().port
await new Promise(resolve => probe.close(resolve))
const server = await createServer({ root, configFile: false, plugins: [vue()], server: { host: '127.0.0.1', port, strictPort: true } })
let browser
try {
  await server.listen()
  browser = await chromium.launch({ headless: true, ...(process.env.CAMPUS_BROWSER_EXECUTABLE ? { executablePath: process.env.CAMPUS_BROWSER_EXECUTABLE } : {}) })
  const context = await browser.newContext({ viewport: { width: 1280, height: 960 } })
  await context.addInitScript(() => {
    if (location.protocol === 'http:' || location.protocol === 'https:') sessionStorage.setItem('campus.refresh-token', 'test-refresh')
  })
  const page = await context.newPage(), errors = [], apiErrors = [], profiles = new Map()
  const profile = (id, permissions) => ({ id, accountType: id === '9' ? 'ADMIN' : ['1', '3'].includes(id) ? 'MERCHANT' : 'USER', homePath: id === '9' ? '/admin' : ['1', '3'].includes(id) ? '/merchant' : '/app', nickname: '浏览器验收用户', maskedPhone: '139****0000', schoolId: null, schoolName: null, campusVerificationStatus: 'UNVERIFIED', permissions })
  profiles.set(page, profile('1', ['merchant:read']))
  context.on('page', tab => tab.on('pageerror', error => errors.push(error.message)))
  page.on('pageerror', error => errors.push(error.message))
  const now = new Date().toISOString(), expires = new Date(Date.now() + 86400000).toISOString()
  const store = { id: '11', name: '校园小馆', schoolId: '1', category: 'DINING', address: '东门', status: 'APPROVED', version: 0, description: '', businessHours: '', role: 'OWNER' }
  let coupon = null, review = null, claim = null, redemption = null, notices = [], walletSocket = null, authFrames = 0, claimCreates = 0, ticketRequests = 0, claimQueries = 0
  const code = `101.${'a'.repeat(43)}`
  const paged = (items, url) => ({ items, total: items.length, page: Number(url.searchParams.get('page') || 1), size: Number(url.searchParams.get('size') || 20) })
  await context.route('**/api/v1/**', async route => {
    try {
      const req = route.request(), tab = req.frame().page(), current = profiles.get(tab) || profile('2', []), url = new URL(req.url()), path = url.pathname.replace('/api/v1', ''), method = req.method()
      const respond = (value, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) })
      if (path === '/assistant/capabilities') return respond({ configured: false })
      if (path === '/map-capabilities') return respond({ configured: false })
      if (path === '/session-refreshes') return respond({ sessionId: 'test', tokenType: 'Bearer', accessToken: 'test-access', refreshToken: 'test-refresh', accessExpiresAt: expires, refreshExpiresAt: expires }, 201)
      if (path === '/schools') return respond([{ id: '1', name: '测试校园' }])
      if (path === '/coupons') return respond(paged(coupon?.status === 'PUBLISHED' ? [{ ...coupon, merchantName: store.name, schoolId: '1' }] : [], url))
      if (path === '/notification-capabilities') return respond({ enabled: true, streamPath: '/api/v1/notification-stream', pingIntervalSeconds: 20, reconnectDelaySeconds: 1 })
      assert.equal(req.headers().authorization, 'Bearer test-access')
      if (path === '/users/me') return respond(current)
      if (path === '/users/me/merchants') return respond(paged([{ ...store, role: current.id === '3' ? 'CASHIER' : 'OWNER' }], url))
      if (path === '/merchants/11/coupons') {
        if (method === 'POST') {
          const input = req.postDataJSON(); assert.equal(input.rules.threshold, 50); assert.equal(input.rules.amountOff, 10); assert.equal(input.rules.discountRate, null)
          coupon = { ...input, id: '21', merchantId: '11', remainingStock: input.totalStock, status: 'DRAFT', version: 0, reviewStatus: 'NONE', reviewRequestId: null, publishedAt: null }
          return respond(coupon, 201)
        }
        return respond(paged(coupon ? [coupon] : [], url))
      }
      if (path === '/coupons/21/review-requests') {
        assert.equal(req.postDataJSON().version, coupon.version)
        review = { id: 'request-test', couponId: coupon.id, submittedBy: '1', contentVersion: coupon.version, status: 'PENDING', createdAt: now, reviewedAt: null, content: structuredClone(coupon) }
        coupon.version++; coupon.reviewStatus = 'PENDING'; coupon.reviewRequestId = review.id
        return respond(review, 201)
      }
      if (path === '/admin/coupon-review-requests') return respond(paged(review?.status === url.searchParams.get('status') ? [review] : [], url))
      if (path === '/admin/coupon-review-requests/request-test/reviews') {
        const input = req.postDataJSON(); assert.equal(input.decision, 'APPROVED'); assert.ok(input.reason.trim())
        review.status = input.decision; coupon.reviewStatus = input.decision; coupon.version++
        return respond({ id: 'review-test', resourceId: review.id, reviewerId: current.id, ...input, createdAt: now }, 201)
      }
      if (path === '/coupons/21/status-changes') {
        const input = req.postDataJSON(); assert.equal(input.version, coupon.version); assert.ok(input.reason.trim())
        coupon.status = input.status; coupon.version++; coupon.publishedAt ||= now
        return respond({ id: 'status-test', couponId: coupon.id, ...input, version: coupon.version }, 201)
      }
      if (path === '/coupons/21/claims') {
        if (claim) return respond(claim)
        claimCreates++; coupon.remainingStock--
        claim = { id: '101', couponId: '21', merchantId: '11', merchantName: store.name, title: coupon.title, couponType: coupon.couponType, rules: coupon.rules, status: 'AVAILABLE', claimedAt: now, validFrom: coupon.validFrom, validUntil: coupon.validUntil, redeemedAt: null, redemptionId: null }
        return respond(claim, 201)
      }
      if (path === '/users/me/coupon-claims') { claimQueries++; return respond(paged(claim ? [claim] : [], url)) }
      if (path === '/users/me/notifications') return respond(paged([...notices, { id: 'order-notice', eventId: 'order-event', type: 'ORDER_PAID', payload: { orderId: '401', merchantId: '11' }, createdAt: now, readAt: null }], url))
      if (path === '/coupon-claims/101/redemption-tickets') {
        ticketRequests++
        return respond({ code, qrCodeSvg: "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 32 32'><rect width='32' height='32' fill='white'/><path d='M4 4h24v24H4z' fill='black'/></svg>", expiresAt: new Date(Date.now() + 30000).toISOString() })
      }
      if (path === '/merchants/11/redemptions') return respond(paged(redemption ? [redemption] : [], url))
      if (path === '/redemptions') {
        assert.deepEqual(req.postDataJSON(), { merchantId: '11', code })
        claim.status = 'REDEEMED'; claim.redeemedAt = now; claim.redemptionId = '201'
        redemption = { id: '201', claimId: '101', merchantId: '11', operatorId: current.id, redeemedAt: now }
        notices = [{ id: 'notice-test', eventId: 'event-test', type: 'COUPON_REDEEMED', payload: { claimId: '101', redemptionId: '201' }, createdAt: now, readAt: null }]
        walletSocket.send(JSON.stringify(notices[0]))
        return respond(redemption, 201)
      }
      if (path === '/users/me/notifications/notice-test/read-receipt') { assert.equal(method, 'PUT'); notices[0].readAt = now; return respond(notices[0]) }
      throw new Error(`Unmocked API: ${method} ${path}`)
    } catch (error) { apiErrors.push(error.message); await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ detail: error.message }) }) }
  })
  await page.routeWebSocket('**/api/v1/notification-stream', ws => {
    assert.equal(new URL(ws.url()).search, '', 'WebSocket URL must not contain credentials')
    walletSocket = ws
    ws.onMessage(message => {
      const input = JSON.parse(message)
      if (input.type === 'AUTHENTICATE') { authFrames++; assert.deepEqual(Object.keys(input).sort(), ['accessToken', 'type']); assert.equal(input.accessToken, 'test-access'); ws.send(JSON.stringify({ type: 'READY' })) }
      else { assert.equal(input.type, 'PING'); ws.send(JSON.stringify({ type: 'PONG' })) }
    })
  })
  const visit = path => page.goto(`http://127.0.0.1:${port}${path}`)
  await visit('/merchant/coupons')
  await page.getByRole('heading', { name: '创建优惠草稿' }).waitFor()
  await page.getByLabel('优惠标题').fill('午餐满五十减十元')
  await page.getByLabel('最低消费金额（元）').fill('50')
  await page.getByLabel('减免金额（元）').fill('10')
  await page.getByLabel('发放数量').fill('2')
  const localDate = offset => {
    const date = new Date(Date.now() + offset)
    return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
  }
  await page.getByLabel('有效期开始').fill(localDate(-3600000))
  await page.getByLabel('有效期结束').fill(localDate(86400000))
  await page.getByLabel('活动说明').fill('工作日午餐，到店使用。')
  await page.getByRole('button', { name: '保存优惠草稿' }).click()
  await page.getByText('优惠草稿已保存，请送交内容审核。').waitFor()
  await page.getByRole('button', { name: '提交优惠审核' }).click()
  await page.getByText('已提交内容审核。').waitFor()
  assert.equal(await page.getByRole('button', { name: '发布优惠', exact: true }).count(), 0)
  profiles.set(page, profile('9', ['coupon:review']))
  await visit('/admin')
  await page.getByRole('link', { name: '进入优惠审核 →' }).click()
  await page.getByRole('button', { name: '查看优惠申请' }).click()
  await page.getByLabel('优惠审核原因').fill('活动规则清晰，内容审核通过')
  await page.getByRole('button', { name: '通过优惠审核' }).click()
  await page.getByText('优惠审核决定已保存，商家可在通过后发布。').waitFor()
  profiles.set(page, profile('1', ['merchant:read']))
  await visit('/merchant/coupons')
  await page.getByRole('button', { name: '发布优惠', exact: true }).waitFor()
  assert.equal(await page.getByRole('button', { name: '发布优惠', exact: true }).isDisabled(), true)
  await page.getByLabel('发布或下架说明').fill('审核通过，确认正式发布')
  await page.getByRole('button', { name: '发布优惠', exact: true }).click()
  await page.getByText('优惠已发布。').waitFor()
  assert.equal(await page.getByRole('button', { name: '编辑草稿' }).count(), 0)
  profiles.set(page, profile('2', []))
  await visit('/app')
  await page.getByRole('button', { name: '领取优惠', exact: true }).click()
  await page.getByText('优惠券已放入我的券包，可前往查看并到店使用。').waitFor()
  await page.getByRole('button', { name: '领取优惠', exact: true }).click()
  await page.getByText('优惠券已放入我的券包，可前往查看并到店使用。').waitFor()
  assert.equal(claimCreates, 1); assert.equal(coupon.remainingStock, 1)
  if (process.env.CAMPUS_UI_SCREENSHOT) await page.screenshot({ path: resolve(process.env.CAMPUS_UI_SCREENSHOT), fullPage: true })
  await visit('/account/coupons')
  await page.getByText('实时通知已连接', { exact: true }).waitFor()
  assert.equal(await page.getByRole('heading', { name: '核销通知' }).count(), 0, 'order events must not be displayed as coupon redemptions')
  await page.getByRole('button', { name: '出示核销码' }).click()
  await page.getByAltText('仅供本次到店核销的短时二维码').waitFor()
  assert.equal(ticketRequests, 1)
  const storage = await page.evaluate(() => ({ local: JSON.stringify(localStorage), session: JSON.stringify(sessionStorage) }))
  assert.ok(!JSON.stringify(storage).includes(code), 'redemption credential must remain in memory')
  const staff = await context.newPage(); profiles.set(staff, profile('3', ['merchant:read']))
  await staff.goto(`http://127.0.0.1:${port}/merchant/coupons`)
  await staff.getByText('当前门店状态或角色仅允许查看活动。').waitFor()
  assert.equal(await staff.getByRole('heading', { name: '创建优惠草稿' }).count(), 0)
  await staff.goto(`http://127.0.0.1:${port}/merchant/redemptions`)
  await staff.getByLabel('核销码或扫码结果').fill(code)
  await staff.getByRole('button', { name: '确认核销', exact: true }).click()
  await staff.getByText('到店核销成功。').waitFor()
  await page.getByText('到店核销成功，券状态已更新。').waitFor()
  await page.getByText('已核销', { exact: true }).waitFor()
  assert.equal(await page.getByAltText('仅供本次到店核销的短时二维码').count(), 0)
  await page.getByRole('button', { name: '标记已读' }).click()
  await page.getByRole('heading', { name: '核销通知' }).waitFor({ state: 'detached' })
  const before = claimQueries
  walletSocket.send(JSON.stringify(notices[0])); walletSocket.send(JSON.stringify(notices[0]))
  await page.waitForTimeout(150)
  assert.equal(claimQueries, before, 'replayed events must not refresh or create duplicate UI notifications')
  await walletSocket.close({ code: 1001, reason: 'test reconnect' })
  await page.getByText('连接已断开，正在重连', { exact: true }).waitFor()
  await page.getByText('实时通知已连接', { exact: true }).waitFor()
  assert.equal(authFrames, 2)
  await page.setViewportSize({ width: 390, height: 844 })
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'wallet must fit the mobile viewport')
  if (process.env.CAMPUS_UI_SCREENSHOT) await page.screenshot({ path: resolve(process.env.CAMPUS_UI_SCREENSHOT + '.wallet.png'), fullPage: true })
  await visit('/admin/coupon-review-requests')
  await page.getByRole('heading', { name: '发现校园周边的好优惠' }).waitFor()
  assert.deepEqual(errors, []); assert.deepEqual(apiErrors, [])
  console.log('PASS: draft/review/publication, immutable published UI, public browse, duplicate claim, private QR memory, cashier limits, redemption and live wallet update, inbox receipt, event dedupe/reconnect, route guard and mobile layout. HTTP/WebSocket mocked only in this test.')
} finally {
  await browser?.close()
  await server.close()
}
