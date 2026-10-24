// UI contracts only: HTTP and AMap SDK are explicit fixtures, never production adapters.
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
  const origin = `http://127.0.0.1:${server.httpServer.address().port}`
  browser = await chromium.launch({ headless: true, executablePath: process.env.CAMPUS_BROWSER_EXECUTABLE })
  for (const type of ['USER', 'MERCHANT', 'ADMIN']) {
    const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
    const page = await context.newPage(), errors = [], purposes = []
    let registered = false, reset = false, changed = false, mapRequests = 0, failMap = true
    page.on('pageerror', error => errors.push(error.message))
    const tokens = { sessionId: 'fixture-session', tokenType: 'Bearer', accessToken: 'fixture-access', refreshToken: 'fixture-refresh', accessExpiresAt: new Date(Date.now()+900000).toISOString(), refreshExpiresAt: new Date(Date.now()+86400000).toISOString() }
    const home = { USER: '/app', MERCHANT: '/merchant', ADMIN: '/admin' }[type]
    await page.route('**/api/v1/**', async route => {
      const req = route.request(), path = new URL(req.url()).pathname.replace('/api/v1', '')
      const respond = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
      const data = () => req.postDataJSON()
      if (path === '/authentication-capabilities') return respond({ enabled: true, smsAvailable: true, smsSimulated: true, codeLength: 4, resendAfterSeconds: 0, passwordMinLength: 10, passwordMaxBytes: 72 })
      if (path === '/verification-challenges') { purposes.push(data().purpose); return respond({ id: `fixture-${data().purpose}`, status: 'SENT', expiresAt: new Date(Date.now()+300000).toISOString() }, 201) }
      if (path === '/password-sessions') { assert.deepEqual(Object.keys(data()).sort(), ['password','phone']); return respond(tokens, 201) }
      if (path === '/registrations') { assert.equal(type, 'USER'); assert.equal(data().challengeId, 'fixture-REGISTER'); assert.equal(data().code, '8752'); assert.equal(data().password, 'Browser-Password!'); assert.equal(data().accountType, undefined); registered = true; return respond(tokens, 201) }
      if (path === '/password-resets') { assert.equal(data().challengeId, 'fixture-RESET_PASSWORD'); assert.equal(data().code, '8752'); reset = true; return route.fulfill({ status: 204 }) }
      if (path === '/users/me/password') { assert.equal(req.method(), 'PUT'); assert.equal(data().currentPassword, 'Browser-Password!'); assert.equal(data().password, 'Changed-Password!'); changed = true; return route.fulfill({ status: 204 }) }
      if (path === '/users/me') return respond({ id: '1', accountType: type, homePath: home, nickname: '浏览器验收', maskedPhone: '138****0000', campusVerificationStatus: 'UNVERIFIED', permissions: type === 'MERCHANT' ? ['merchant:read'] : [] })
      if (path === '/session-refreshes') return respond(tokens, 201)
      if (path === '/sessions/current') return route.fulfill({ status: 204 })
      if (path === '/assistant/capabilities') return respond({ enabled: false })
      if (path === '/map-capabilities') return respond({ configured: true, jsKey: 'fixture-key', proxyPath: '/api/v1/maps/_AMapService' })
      if (path === '/search-capabilities') return respond({ nearbyAvailable: true, defaultRadiusMeters: 2000, maxRadiusMeters: 10000 })
      if (path === '/merchant-categories' || path === '/schools') return respond([])
      if (['/coupons','/users/me/merchants','/nearby-merchants'].includes(path)) return respond({ items: [], total: 0, page: 1, size: 20 })
      throw new Error(`Unmocked fixture API ${req.method()} ${path}`)
    })
    await page.route('https://webapi.amap.com/maps?**', async route => {
      mapRequests++
      if (failMap) return route.abort()
      return route.fulfill({ contentType: 'application/javascript', body: `window.AMap={Map:class {constructor(el){this.el=el;el.innerHTML='<div class="fixture-map">地图已自动加载</div>'} add(){} remove(){} setFitView(){} setCenter(){} destroy(){this.el.innerHTML=''}},Marker:class{},convertFrom(points,type,callback){callback('complete',{locations:points.map(p=>({getLng:()=>p[0],getLat:()=>p[1]}))})}}` })
    })
    await page.goto(origin+'/login')
    await page.getByLabel('手机号', { exact: true }).fill('13800000000')
    await page.getByLabel('密码', { exact: true }).fill('Browser-Password!')
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await page.waitForURL(origin+home)
    await page.goto(origin+'/account/password')
    await page.getByLabel('当前密码', { exact: true }).fill('Browser-Password!')
    await page.getByLabel('新密码', { exact: true }).fill('Changed-Password!')
    await page.getByLabel('确认密码', { exact: true }).fill('Changed-Password!')
    await page.getByRole('button', { name: '修改密码并退出登录' }).click()
    await page.waitForURL(/\/login\?passwordChanged=true$/)
    assert.equal(changed, true)
    assert.equal(await page.evaluate(() => sessionStorage.getItem('campus.refresh-token')), null)
    if (type === 'USER') {
      await page.getByRole('link', { name: '注册账号', exact: true }).click()
      await page.getByLabel('手机号', { exact: true }).fill('13800000000')
      await page.getByLabel('昵称', { exact: true }).fill('注册验收')
      await page.getByRole('button', { name: '获取验证码', exact: true }).click()
      await page.getByText('本地模拟验证码已生效，请使用本机配置的测试验证码。', { exact: true }).waitFor()
      await page.getByLabel('验证码', { exact: true }).fill('8752')
      await page.getByLabel('新密码', { exact: true }).fill('Browser-Password!')
      await page.getByLabel('确认密码', { exact: true }).fill('Mismatch-Password!')
      await page.getByRole('button', { name: '注册账号', exact: true }).click()
      await page.getByText('两次输入的密码不一致。', { exact: true }).waitFor()
      assert.equal(registered, false)
      await page.getByLabel('确认密码', { exact: true }).fill('Browser-Password!')
      await page.getByRole('button', { name: '注册账号', exact: true }).click()
      await page.waitForURL(origin+'/app'); assert.equal(registered, true)
      await page.goto(origin+'/account')
      await page.getByRole('button', { name: '退出当前账号' }).click()
      await page.getByRole('link', { name: '忘记密码', exact: true }).click()
      await page.getByLabel('手机号', { exact: true }).fill('13800000000')
      await page.getByRole('button', { name: '获取验证码', exact: true }).click()
      await page.getByText('本地模拟验证码已生效，请使用本机配置的测试验证码。', { exact: true }).waitFor()
      await page.getByLabel('验证码', { exact: true }).fill('8752')
      await page.getByLabel('新密码', { exact: true }).fill('Changed-Password!')
      await page.getByLabel('确认密码', { exact: true }).fill('Changed-Password!')
      await page.getByRole('button', { name: '找回密码', exact: true }).click()
      await page.waitForURL(origin+'/login'); assert.equal(reset, true)
      await page.getByText('密码已重置，旧会话已退出，请使用新密码登录。', { exact: true }).waitFor()
      assert.deepEqual(purposes, ['REGISTER','RESET_PASSWORD'])
      await page.goto(origin+'/nearby')
      await page.getByRole('button', { name: '重试加载地图', exact: true }).waitFor()
      assert.equal(mapRequests, 1, 'Map SDK must be requested automatically on entry')
      failMap = false
      await page.getByRole('button', { name: '重试加载地图', exact: true }).click()
      await page.locator('.fixture-map').waitFor(); assert.equal(mapRequests, 2)
      await page.getByRole('link', { name: '优惠首页', exact: true }).click()
      // Client navigation reuses the loaded SDK across component remounts.
      await page.getByRole('link', { name: '附近商家', exact: true }).click()
      await page.locator('.fixture-map').waitFor(); assert.equal(mapRequests, 2)
    }
    assert.deepEqual(errors, [])
    await context.close()
  }
  console.log('PASS: password login for USER/MERCHANT/ADMIN, registration confirmation, reset, password change, automatic map request and retry. Explicit UI fixtures only.')
} finally { await browser?.close(); await server.close() }
