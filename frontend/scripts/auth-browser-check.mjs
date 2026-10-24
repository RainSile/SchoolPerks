// Optional headless UI contract check. HTTP responses below are test-only mocks.
// Run with CAMPUS_PLAYWRIGHT_MODULE pointing to an installed Playwright package.
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
const availablePort = probe.address().port
await new Promise(resolve => probe.close(resolve))
const server = await createServer({ root, configFile: false, plugins: [vue()], server: { host: '127.0.0.1', port: availablePort, strictPort: true } })
let browser
try {
  await server.listen()
  const port = server.httpServer.address().port
  browser = await chromium.launch({ headless: true, ...(process.env.CAMPUS_BROWSER_EXECUTABLE ? { executablePath: process.env.CAMPUS_BROWSER_EXECUTABLE } : {}) })
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } })
  const pageErrors = []
  page.on('pageerror', error => pageErrors.push(error.message))
  let smsAvailable = false, expired = false, tokenVersion = 0, refreshCount = 0, logoutCount = 0
  const profile = { id: '1', accountType: 'USER', homePath: '/app', nickname: '测试用户', maskedPhone: '139****0000', schoolId: null, schoolName: null, campusVerificationStatus: 'UNVERIFIED', permissions: [] }
  const history = []
  const tokens = () => ({ sessionId: 'test-session', tokenType: 'Bearer', accessToken: `test-access-${tokenVersion}`, refreshToken: `test-refresh-${tokenVersion}`, accessExpiresAt: new Date(Date.now() + 900000).toISOString(), refreshExpiresAt: new Date(Date.now() + 86400000).toISOString() })
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname.replace('/api/v1', '')
    const respond = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (path === '/authentication-capabilities') return respond({ enabled: true, smsAvailable, codeLength: 6, resendAfterSeconds: 60 })
    if (path === '/verification-challenges') return respond({ id: 'test-challenge', expiresAt: new Date(Date.now() + 300000).toISOString(), status: 'SENT' }, 201)
    if (path === '/sessions') {
      assert.equal(request.postDataJSON().code, '123456')
      tokenVersion++; expired = false
      return respond(tokens(), 201)
    }
    if (path === '/assistant/capabilities') return respond({ configured: false })
    if (path === '/map-capabilities') return respond({ configured: false })
    if (path === '/session-refreshes') {
      refreshCount++
      await new Promise(resolve => setTimeout(resolve, 150))
      assert.equal(request.postDataJSON().refreshToken, `test-refresh-${tokenVersion}`)
      tokenVersion++; expired = false
      return respond(tokens(), 201)
    }
    if (path === '/coupons') return respond({ items: [], total: 0, page: 1, size: 20 })
    if (path === '/schools') return respond([{ id: '1', name: '测试学校' }])
    if (expired || request.headers().authorization !== `Bearer test-access-${tokenVersion}`) return respond({ status: 401, detail: '会话已过期' }, 401)
    if (path === '/users/me') {
      if (request.method() === 'PATCH') {
        assert.deepEqual(Object.keys(request.postDataJSON()), ['nickname'])
        profile.nickname = request.postDataJSON().nickname
      }
      return respond(profile)
    }
    if (path === '/users/me/campus-verifications') {
      if (request.method() === 'POST') {
        history.push({ id: 'test-verification', schoolId: '1', schoolName: '测试学校', status: 'PENDING', submittedAt: new Date().toISOString(), reviewedAt: null, reviewReason: null })
        profile.campusVerificationStatus = 'PENDING'
        return respond(history[0], 201)
      }
      return respond(history)
    }
    if (path === '/sessions/current' && request.method() === 'DELETE') { logoutCount++; return route.fulfill({ status: 204 }) }
    throw new Error(`Unmocked API: ${request.method()} ${path}`)
  })
  const waitFor = async predicate => {
    for (let i = 0; i < 100; i++) { if (await predicate()) return; await new Promise(r => setTimeout(r, 50)) }
    throw new Error('UI condition timed out')
  }
  await page.goto(`http://127.0.0.1:${port}/account`)
  await page.getByRole('heading', { name: '登录', exact: true }).waitFor()
  await page.getByRole('button', { name: '短信登录', exact: true }).click()
  await page.getByText('短信服务暂不可用，请稍后重试。').waitFor()
  assert.equal(await page.getByRole('button', { name: '获取验证码' }).isDisabled(), true)
  smsAvailable = true
  await page.reload()
  await page.getByRole('button', { name: '短信登录', exact: true }).click()
  await page.getByLabel('手机号', { exact: true }).fill('13900000000')
  await waitFor(async () => page.getByRole('button', { name: '获取验证码' }).isEnabled())
  await page.getByRole('button', { name: '获取验证码' }).click()
  await page.getByText('验证码已发送，请查看手机短信。').waitFor()
  await page.getByLabel('验证码', { exact: true }).fill('123456')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.getByRole('heading', { name: '个人资料' }).waitFor()
  await page.getByText('139****0000').waitFor()
  await page.getByLabel('昵称').fill('浏览器验收昵称')
  await page.getByRole('button', { name: '保存昵称' }).click()
  await page.getByText('资料已保存。').waitFor()
  assert.equal(profile.nickname, '浏览器验收昵称')
  await page.getByRole('link', { name: '查看与提交校园认证 →' }).click()
  await page.getByLabel('学校', { exact: true }).selectOption('1')
  await page.getByLabel('真实姓名').fill('测试姓名')
  await page.getByLabel('学号').fill('TEST2026')
  await page.getByRole('button', { name: '提交认证申请' }).click()
  await page.getByText('已有待审核或通过的申请，无需重复提交。').waitFor()
  assert.equal(history.length, 1)
  expired = true
  const concurrent = await page.evaluate(async () => {
    const { request } = await import('/src/campus-shared/http.ts')
    return Promise.all([request('/users/me'), request('/users/me/campus-verifications')])
  })
  assert.equal(refreshCount, 1, 'simultaneous 401s must share one refresh request')
  assert.equal(concurrent[0].id, '1')
  await page.goto(`http://127.0.0.1:${port}/admin`)
  await page.getByRole('heading', { name: '发现校园周边的好优惠' }).waitFor()
  profile.accountType = 'ADMIN'; profile.homePath = '/admin'; profile.permissions = ['merchant:review']
  await page.goto(`http://127.0.0.1:${port}/admin`)
  await page.getByRole('heading', { name: '平台运营与审核' }).waitFor()
  profile.permissions = []
  await page.goto(`http://127.0.0.1:${port}/admin/merchant-applications`)
  await page.getByRole('heading', { name: '暂无访问权限' }).waitFor()
  await page.goto(`http://127.0.0.1:${port}/account`)
  await page.getByRole('button', { name: '退出当前账号' }).click()
  await page.getByRole('heading', { name: '登录', exact: true }).waitFor()
  assert.equal(logoutCount, 1)
  assert.equal(await page.evaluate(() => sessionStorage.getItem('campus.refresh-token')), null)
  await page.goto(`http://127.0.0.1:${port}/app`)
  await page.getByRole('link', { name: '前往认证' }).click()
  await page.getByRole('heading', { name: '登录', exact: true }).waitFor()
  assert.deepEqual(pageErrors, [])
  if (process.env.CAMPUS_UI_SCREENSHOT) await page.screenshot({ path: resolve(process.env.CAMPUS_UI_SCREENSHOT), fullPage: true })
  console.log('PASS: SMS availability, login redirect, profile, campus submission, single-flight refresh, live permission guards, logout. API responses were mocked only in this test.')
} finally {
  await browser?.close()
  await server.close()
}
