// E evidence: unmocked business HTTP, actual Spring application and MySQL. Supplier ports in
// this dedicated Spring test are explicit doubles, never real SMS/storage/payment acceptance.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import { writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.CAMPUS_PLAYWRIGHT_MODULE || 'playwright')
const root = fileURLToPath(new URL('..', import.meta.url))
const backend = `http://127.0.0.1:${process.env.CAMPUS_LIVE_BACKEND_PORT}`
const server = await createServer({ root, configFile: false, plugins: [vue()], server: {
  host: '127.0.0.1', port: Number(process.env.CAMPUS_LIVE_FRONTEND_PORT), strictPort: true,
  proxy: { '/api': { target: backend, changeOrigin: true, ws: true } },
} })
const steps = [], errors = [], actors = {}, failures = []
const mapDiagnostics = { proxyResponses: [], providerErrors: [] }
const title = `联调优惠-${Date.now()}`
const merchant = process.env.CAMPUS_LIVE_MERCHANT_ID, school = process.env.CAMPUS_LIVE_SCHOOL_ID
let browser
try {
  await server.listen()
  const origin = `http://127.0.0.1:${server.httpServer.address().port}`
  browser = await chromium.launch({ headless: true, executablePath: process.env.CAMPUS_BROWSER_EXECUTABLE,
    ...(process.env.CAMPUS_LIVE_BROWSER_PROXY ? { proxy: { server: process.env.CAMPUS_LIVE_BROWSER_PROXY, bypass: '127.0.0.1,localhost' } } : {}) })
  async function login(name, home) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await context.newPage()
    actors[name] = { context, page, token: null }
    page.on('pageerror', error => errors.push(error.message))
    page.on('request', request => { const token = request.headers().authorization; if (token?.startsWith('Bearer ')) actors[name].token = token })
    await page.goto(origin + '/login')
    await page.getByLabel('手机号', { exact: true }).fill(process.env[`CAMPUS_LIVE_${name}_PHONE`])
    await page.getByLabel('密码', { exact: true }).fill(process.env.CAMPUS_LIVE_PASSWORD)
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await page.waitForURL(origin + home)
    await page.getByRole('navigation', { name: { USER: '用户端导航', MERCHANT: '商家端导航', ADMIN: '管理端导航' }[name] }).waitFor()
    steps.push(`${name}: password login -> correct portal through real HTTP/MySQL`)
    return page
  }
  async function api(name, method, path, data, expected = 200) {
    const actor = actors[name]
    assert.ok(actor.token, 'Authenticated request was observed without printing credentials')
    const response = await actor.context.request.fetch(origin + '/api/v1' + path, { method, headers: { Authorization: actor.token, 'Idempotency-Key': crypto.randomUUID() }, ...(data === undefined ? {} : { data }) })
    if (response.status() !== expected) failures.push({ method, path, expected, actual: response.status() })
    assert.equal(response.status(), expected, `Real HTTP ${method} ${path}`)
    return response.status() === 204 ? null : response.json()
  }
  if (process.env.CAMPUS_LIVE_MAP_ONLY === 'true') {
    await login('MERCHANT', '/merchant')
    await api('MERCHANT', 'PUT', `/merchants/${merchant}/location`, { longitude: '116.4', latitude: '39.9', coordinateSystem: 'WGS84', version: 0 })
    const user = await login('USER', '/app')
    const { proxyResponses, providerErrors } = mapDiagnostics
    user.on('response', async response => {
      const url = new URL(response.url())
      if (!url.pathname.includes('/maps/_AMapService/')) return
      proxyResponses.push({ status: response.status(), path: url.pathname.replace(/\/v\d+\/.*/, '/{version}/{path}') })
      try {
        const text = await response.text()
        const code = text.match(/INVALID_USER_(?:KEY|SCODE|DOMAIN|SIGNATURE)|DAILY_QUERY_OVER_LIMIT/)?.[0]
        if (code) providerErrors.push(code)
      } catch {}
    })
    await user.goto(origin + '/nearby')
    await user.getByRole('heading', { name: '附近商家', exact: true }).waitFor()
    await user.waitForFunction(() => window.AMap && document.querySelector('.amap-canvas:not(.amap-hidden)') && document.querySelector('.amap-canvas canvas'), null, { timeout: 35000 })
    assert.equal(await user.getByRole('button', { name: '重试加载地图' }).count(), 0)
    await user.getByLabel('WGS84 经度').fill('116.4')
    await user.getByLabel('WGS84 纬度').fill('39.9')
    await user.getByRole('button', { name: '查询附近商家', exact: true }).click()
    await user.getByRole('heading', { name: 'business-test-store', exact: true }).waitFor()
    for (let attempt = 0; attempt < 60 && !proxyResponses.some(row => row.status === 200); attempt++) await new Promise(resolve => setTimeout(resolve, 250))
    assert.ok(proxyResponses.some(row => row.status === 200), 'Actual AMap proxy must return a successful provider resource')
    assert.deepEqual(providerErrors, [])
    const pixels = async () => {
      const png = await user.locator('.amap-canvas').screenshot()
      return user.evaluate(async encoded => {
        const bytes = Uint8Array.from(atob(encoded), value => value.charCodeAt(0))
        const image = await createImageBitmap(new Blob([bytes], { type: 'image/png' }))
        const canvas = new OffscreenCanvas(image.width, image.height), context = canvas.getContext('2d')
        context.drawImage(image, 0, 0)
        const data = context.getImageData(15, 15, image.width - 30, image.height - 65).data
        const colors = new Set(); let colored = 0
        for (let index = 0; index < data.length; index += 16) {
          colors.add((data[index] << 16) | (data[index + 1] << 8) | data[index + 2])
          if (Math.max(data[index], data[index + 1], data[index + 2]) - Math.min(data[index], data[index + 1], data[index + 2]) > 20) colored++
        }
        return { sampledColors: colors.size, coloredSamples: colored }
      }, png.toString('base64'))
    }
    let visual = await pixels()
    for (let attempt = 0; attempt < 3 && (visual.sampledColors < 128 || visual.coloredSamples < 500); attempt++) {
      await new Promise(resolve => setTimeout(resolve, 5000))
      visual = await pixels()
    }
    mapDiagnostics.visual = visual
    await user.screenshot({ path: join(process.env.CAMPUS_BUSINESS_EVIDENCE_DIR, 'map-live.png'), fullPage: true })
    assert.ok(visual.sampledColors >= 128 && visual.coloredSamples >= 500, 'Map stayed visually blank despite SDK/proxy initialization; inspect the saved real screenshot')
    assert.deepEqual(errors, [])
    steps.push('Real AMap SDK auto-loads without a manual map action; canvas rendered and actual proxy resource succeeded')
    steps.push('Synthetic WGS84 coordinates -> actual MySQL nearby query -> own merchant list')
    await writeFile(join(process.env.CAMPUS_BUSINESS_EVIDENCE_DIR, 'map-live-result.json'), JSON.stringify({ passed: true, evidence: 'E+X', steps, proxyResponses, providerErrors, visual, assistantFlowsTested: false, syntheticCoordinates: true }, null, 2))
    console.log('PASS: real map SDK auto-load, canvas, provider proxy and actual nearby business query.')
  } else {
  const owner = await login('MERCHANT', '/merchant')
  await owner.goto(origin + '/merchant/coupons')
  await owner.getByRole('heading', { name: '创建优惠草稿' }).waitFor()
  await owner.getByLabel('优惠标题').fill(title)
  await owner.getByLabel('最低消费金额（元）').fill('50')
  await owner.getByLabel('减免金额（元）').fill('10')
  await owner.getByLabel('发放数量').fill('5')
  const localDate = offset => { const date = new Date(Date.now() + offset); return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16) }
  await owner.getByLabel('有效期开始').fill(localDate(-3600000))
  await owner.getByLabel('有效期结束').fill(localDate(86400000))
  await owner.getByLabel('活动说明').fill('专属联调夹具，核实真实业务调用链。')
  await owner.getByRole('button', { name: '保存优惠草稿' }).click()
  await owner.getByText('优惠草稿已保存，请送交内容审核。').waitFor()
  await owner.getByRole('button', { name: '提交优惠审核' }).click()
  await owner.getByText('已提交内容审核。').waitFor()
  steps.push('Merchant UI: draft -> immutable review submission -> MySQL')

  const admin = await login('ADMIN', '/admin')
  await admin.goto(origin + '/admin/coupon-review-requests')
  await admin.locator('article.review-record').filter({ hasText: title }).getByRole('button', { name: '查看优惠申请' }).click()
  await admin.getByLabel('优惠审核原因').fill('联调审核通过')
  await admin.getByRole('button', { name: '通过优惠审核' }).click()
  await admin.getByText('优惠审核决定已保存，商家可在通过后发布。').waitFor()
  steps.push('Admin UI: queue -> review -> persistent approved state')

  await owner.reload()
  await owner.getByLabel('发布或下架说明').fill('联调正式发布')
  await owner.getByRole('button', { name: '发布优惠', exact: true }).click()
  await owner.getByText('优惠已发布。').waitFor()
  const user = await login('USER', '/app')
  const ticket = user.locator('article.coupon-ticket').filter({ hasText: title })
  await ticket.getByRole('button', { name: '领取优惠', exact: true }).click()
  await user.getByText('优惠券已放入我的券包，可前往查看并到店使用。').waitFor()
  await user.goto(origin + '/account/coupons')
  const ownClaim = user.locator('article.wallet-entry').filter({ hasText: title })
  await ownClaim.getByRole('button', { name: '出示核销码' }).click()
  await user.getByAltText('仅供本次到店核销的短时二维码').waitFor()
  const claims = await api('USER', 'GET', '/users/me/coupon-claims')
  const claim = claims.items.find(row => row.title === title)
  assert.ok(claim, 'Actual coupon claim must persist')
  const redemptionCode = (await user.locator('.redemption-code').textContent()).trim()
  assert.ok(redemptionCode.length > 0)
  const persistedStorage = await user.evaluate(() => JSON.stringify({ local: localStorage, session: sessionStorage }))
  assert.ok(!persistedStorage.includes(redemptionCode), 'The real redemption credential must stay out of persistent browser storage')
  await owner.goto(origin + '/merchant/redemptions')
  await owner.getByLabel('核销码或扫码结果').fill(redemptionCode)
  await owner.getByRole('button', { name: '确认核销', exact: true }).click()
  await owner.getByText('到店核销成功。').waitFor()
  const refreshedClaims = await api('USER', 'GET', '/users/me/coupon-claims')
  assert.equal(refreshedClaims.items.find(row => row.id === claim.id).status, 'REDEEMED')
  steps.push('Real coupon journey: publication -> student claim -> private QR -> merchant UI redemption -> persisted REDEEMED')

  await user.goto(origin + '/merchant/coupons'); await user.waitForURL(origin + '/app')
  await admin.goto(origin + '/account/coupons'); await admin.waitForURL(origin + '/admin')
  await api('USER', 'GET', '/admin/roles', undefined, 403)
  await api('MERCHANT', 'GET', '/orders', undefined, 403)
  steps.push('Actual router and backend both reject cross-portal access')

  const offer = await api('MERCHANT', 'POST', `/merchants/${merchant}/trade-offers`, { title: '联调套餐', description: 'fixture', kind: 'REGULAR', price: '19.90', stock: 3, startsAt: new Date(Date.now() - 60000).toISOString(), endsAt: new Date(Date.now() + 3600000).toISOString(), groupSize: 1, groupDurationSeconds: 0 }, 201)
  await api('MERCHANT', 'PATCH', `/trade-offers/${offer.id}`, { status: 'PUBLISHED', version: 0 })
  const order = await api('USER', 'POST', '/orders', { offerId: offer.id }, 201)
  assert.equal(order.amount, '19.90')
  await api('USER', 'POST', `/orders/${order.id}/cancellations`, {}, 201)
  assert.equal((await api('USER', 'GET', `/orders/${order.id}`)).status, 'CANCELLED')
  steps.push('Real HTTP trade: publish offer -> server price snapshot -> order -> cancellation -> persisted status')

  const post = await api('USER', 'POST', '/posts', { schoolId: school, merchantId: merchant, content: { title: '联调校园探店', body: '<script>window.__liveBusinessXss=1</script> 校园探店正文', imageIds: [] } }, 201)
  const review = await api('USER', 'POST', `/posts/${post.id}/review-requests`, { version: post.version }, 201)
  await api('ADMIN', 'POST', `/admin/post-review-requests/${review.id}/reviews`, { decision: 'APPROVED', reason: '联调审核' }, 201)
  await api('USER', 'PUT', `/posts/${post.id}/likes/me`, undefined)
  assert.equal((await api('USER', 'GET', `/posts/${post.id}/likes/me`)).liked, true)
  await user.goto(origin + `/community/posts/${post.id}`)
  await user.getByRole('heading', { name: '联调校园探店', exact: true }).waitFor()
  assert.equal(await user.evaluate(() => window.__liveBusinessXss), undefined)
  steps.push('Real HTTP + post UI: draft -> approval -> public detail -> like; authored script text never executes')

  await api('MERCHANT', 'PUT', `/merchants/${merchant}/location`, { longitude: '116.4', latitude: '39.9', coordinateSystem: 'WGS84', version: 0 })
  const nearby = await api('USER', 'GET', '/nearby-merchants?longitude=116.4&latitude=39.9&coordinateSystem=WGS84&radiusMeters=1000&size=20')
  assert.ok(nearby.items.some(row => row.id === merchant))
  const today = new Date().toISOString().slice(0, 10)
  const query = `?from=${today}&to=${today}&zone=Asia%2FShanghai`
  await api('MERCHANT', 'GET', `/merchants/${merchant}/dashboard${query}`)
  await api('ADMIN', 'GET', `/admin/dashboard${query}`)
  steps.push('Actual MySQL nearby lookup and both authorized business dashboards')
  assert.deepEqual(errors, [])
  await writeFile(join(process.env.CAMPUS_BUSINESS_EVIDENCE_DIR, 'browser-live-result.json'), JSON.stringify({ passed: true, evidence: 'E', steps, externalSuppliers: 'Not accepted; storage/payment ports are explicit test doubles', assistantFlowsTested: false }, null, 2))
  console.log(`PASS: ${steps.length} real business journey segments; no business HTTP fixtures.`)
  }
} catch (error) {
  await writeFile(join(process.env.CAMPUS_BUSINESS_EVIDENCE_DIR, 'browser-live-failure.json'), JSON.stringify({ passed: false, completedSteps: steps, httpFailures: failures, pageErrors: errors, mapDiagnostics, error: error.message }, null, 2))
  throw error
} finally {
  await browser?.close()
  await server.close()
}
