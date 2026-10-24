// UI contract checks only. Storage and API mocks never enter the production bundle.
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
  const base = `http://127.0.0.1:${server.httpServer.address().port}`
  browser = await chromium.launch({ headless: true, ...(process.env.CAMPUS_BROWSER_EXECUTABLE ? { executablePath: process.env.CAMPUS_BROWSER_EXECUTABLE } : {}) })
  const page = await browser.newPage({ viewport: { width: 1280, height: 960 } })
  const errors = [], apiErrors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.addInitScript(() => sessionStorage.setItem('campus.refresh-token', 'test-refresh'))
  const now = new Date().toISOString(), expires = new Date(Date.now() + 300000).toISOString()
  const profile = { id: '1', accountType: 'USER', homePath: '/app', nickname: '入驻测试', maskedPhone: '139****0000', campusVerificationStatus: 'UNVERIFIED', schoolId: null, schoolName: null, permissions: [] }
  let available = false, application = null, uploads = [], reviews = [], signed = 0, campusReads = 0, saved = 0, memberChanges = 0
  const store = { id: '11', name: '校园小馆', schoolId: '1', category: 'DINING', address: '学校东门', status: 'APPROVED', version: 0, description: '新鲜餐点', businessHours: '', role: 'OWNER' }
  let members = [{ userId: '1', nickname: '店主', role: 'OWNER', userStatus: 'ACTIVE', createdAt: now }]
  const campus = { id: 'campus-test', applicantId: '2', schoolId: '1', schoolName: '测试校园', status: 'PENDING', submittedAt: now }
  const paged = (items, url) => ({ items, total: items.length, page: Number(url.searchParams.get('page') || 1), size: Number(url.searchParams.get('size') || 20) })
  await page.route('https://storage.example.test/**', async route => {
    assert.equal(route.request().method(), 'PUT')
    assert.equal(route.request().headers().authorization, undefined, 'storage must not receive the platform JWT')
    assert.equal(route.request().headers()['content-type'], 'application/pdf')
    assert.ok(route.request().postDataBuffer().toString().startsWith('%PDF-'))
    return route.fulfill({ status: 200, headers: { 'Access-Control-Allow-Origin': '*' } })
  })
  await page.route('**/api/v1/**', async route => {
    try {
      const req = route.request(), url = new URL(req.url()), path = url.pathname.replace('/api/v1', ''), method = req.method()
      const respond = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
      const ticket = () => { signed++; return respond({ url: 'https://storage.example.test/private?signature=test', expiresAt: expires }) }
      if (path === '/assistant/capabilities') return respond({ configured: false })
      if (path === '/map-capabilities') return respond({ configured: false })
      if (path === '/coupons') return respond({ items: [], total: 0, page: 1, size: 20 })
      if (path === '/session-refreshes') return respond({ sessionId: 'test', tokenType: 'Bearer', accessToken: 'test-access', refreshToken: 'test-refresh', accessExpiresAt: expires, refreshExpiresAt: expires }, 201)
      if (path === '/coupons') return respond({ items: [], total: 0, page: 1, size: 20 })
    if (path === '/schools') return respond([{ id: '1', name: '测试校园' }])
      if (path === '/merchant-categories') return respond(['DINING', 'LIFE_SERVICE'])
      if (path === '/storage-capabilities') return respond({ enabled: true, available, maxBytes: 10485760, allowedTypes: ['application/pdf'], uploadTtlSeconds: 300, downloadTtlSeconds: 120 })
      assert.equal(req.headers().authorization, 'Bearer test-access')
      if (path === '/users/me') return respond(profile)
      if (path === '/uploads') {
        if (method === 'GET') return respond(paged(uploads, url))
        const body = req.postDataJSON()
        assert.equal(body.purpose, 'MERCHANT_QUALIFICATION'); assert.equal(body.fileName, 'qualification.pdf')
        uploads = [{ ...body, id: 'file-test', status: 'PENDING', createdAt: now, expiresAt: expires, completedAt: null }]
        return respond({ id: 'file-test', uploadUrl: 'https://storage.example.test/upload?signature=test', contentType: body.contentType, expiresAt: expires }, 201)
      }
      if (path === '/uploads/file-test/completion') { assert.equal(method, 'PUT'); uploads[0].status = 'READY'; uploads[0].completedAt = now; return respond(uploads[0]) }
      if (path === '/merchant-applications') {
        assert.equal(uploads[0].status, 'READY')
        application = { ...req.postDataJSON(), id: 'application-test', applicantId: '1', schoolName: '测试校园', status: 'PENDING', merchantId: null, submittedAt: now, reviewedAt: null, reviewReason: null }
        assert.equal(application.qualificationFileId, 'file-test'); return respond(application, 201)
      }
      if (path === '/users/me/merchant-applications') return respond(paged(application ? [application] : [], url))
      if (path === '/users/me/merchants') return respond(paged(application?.merchantId ? [store] : [], url))
      if (path === '/merchants/11/workspace') return respond({ ...store, role: profile.id === '3' ? 'CASHIER' : 'OWNER' })
      if (path === '/merchants/11' && method === 'PATCH') {
        const body = req.postDataJSON(); assert.equal(body.version, store.version); assert.deepEqual(Object.keys(body).sort(), ['businessHours', 'description', 'version'])
        Object.assign(store, body, { version: store.version + 1 }); saved++; return respond(store)
      }
      if (path === '/merchants/11/members') {
        if (method === 'POST') { const body = req.postDataJSON(); assert.equal(body.role, 'CASHIER'); members.push({ ...body, nickname: '新店员', userStatus: 'ACTIVE', createdAt: now }); memberChanges++; return respond(members.at(-1), 201) }
        return respond(paged(members, url))
      }
      if (path === '/merchants/11/members/3' && method === 'DELETE') { members = members.filter(x => x.userId !== '3'); memberChanges++; return route.fulfill({ status: 204 }) }
      if (path === '/merchants/11/qualification-download-tickets') return ticket()
      if (path === '/admin/merchant-applications') return respond(paged(application && application.status === url.searchParams.get('status') ? [application] : [], url))
      if (path === '/admin/merchant-reviews') return respond(paged(reviews, url))
      if (path === '/admin/merchant-applications/application-test/qualification-download-tickets') return ticket()
      if (path === '/admin/merchant-applications/application-test/reviews') {
        const body = req.postDataJSON(); assert.equal(body.decision, 'APPROVED'); assert.ok(body.reason.trim());
        Object.assign(application, { status: body.decision, merchantId: '11', reviewedAt: now, reviewReason: body.reason })
        reviews.push({ id: 'review-test', resourceId: application.id, reviewerId: profile.id, ...body, createdAt: now }); return respond(reviews[0], 201)
      }
      if (path === '/admin/merchants/11') return respond(store)
      if (path === '/admin/merchants/11/status-changes') { const body = req.postDataJSON(); assert.equal(body.version, store.version); store.status = body.status; store.version++; return respond({ id: 'change-test', ...body, version: store.version }, 201) }
      if (path === '/admin/campus-verifications') return respond(paged(campus.status === url.searchParams.get('status') ? [campus] : [], url))
      if (path === '/admin/campus-verifications/campus-test/identity') { campusReads++; return respond({ realName: '身份测试姓名', studentNumber: 'TEST2026' }) }
      if (path === '/admin/campus-verifications/campus-test/reviews') { const body = req.postDataJSON(); campus.status = body.decision; return respond({ id: 'campus-review', ...body, resourceId: campus.id, reviewerId: profile.id, createdAt: now }, 201) }
      if (path === '/admin/audit-events') return respond(paged([{ id: 'event-test', actorId: '9', action: 'MERCHANT_APPLICATION_REVIEWED', resourceType: 'MERCHANT_APPLICATION', resourceId: 'application-test', details: { decision: 'APPROVED' }, createdAt: now }], url))
      throw new Error(`Unmocked API: ${method} ${path}`)
    } catch (error) { apiErrors.push(error.message); await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ detail: error.message }) }) }
  })
  const visit = path => page.goto(base + path)
  await visit('/merchant/applications/new')
  await page.getByText('私有文件存储暂不可用，无法上传新资质。').waitFor()
  assert.equal(await page.getByLabel('上传资质文件').isDisabled(), true)
  assert.equal(await page.getByRole('button', { name: '提交入驻申请' }).isDisabled(), true)
  available = true; await page.reload()
  await page.getByLabel('服务校园').selectOption('1')
  await page.getByLabel('门店名称').fill(store.name)
  await page.getByLabel('商家分类').selectOption('DINING')
  await page.getByLabel('门店地址').fill(store.address)
  await page.getByLabel('门店介绍').fill(store.description)
  await page.getByLabel('上传资质文件').setInputFiles({ name: 'qualification.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7\ntest qualification') })
  await page.getByText('资质文件已上传并完成核验。').waitFor()
  await page.getByRole('button', { name: '提交入驻申请' }).click()
  await page.getByRole('heading', { name: '我的入驻申请' }).waitFor()
  await page.getByText('待审核', { exact: true }).waitFor()
  await visit('/admin/merchant-applications')
  await page.getByRole('heading', { name: '发现校园周边的好优惠' }).waitFor()
  profile.accountType = 'ADMIN'; profile.homePath = '/admin'; profile.id = '9'; profile.permissions = ['merchant:review', 'campus:review', 'audit:read']
  await visit('/admin/merchant-applications')
  await page.getByRole('button', { name: '查看申请', exact: true }).click()
  assert.equal(await page.getByRole('button', { name: '通过入驻' }).isDisabled(), true)
  await page.getByRole('button', { name: '获取申请资质凭据' }).click()
  assert.equal(await page.getByRole('link', { name: '查看私有资质' }).getAttribute('href'), 'https://storage.example.test/private?signature=test')
  await page.getByLabel('处理原因').fill('资质资料核验通过')
  await page.getByRole('button', { name: '通过入驻' }).click()
  await page.getByText('审核决定已保存。').waitFor()
  profile.accountType = 'MERCHANT'; profile.homePath = '/merchant'; profile.id = '1'; profile.permissions = ['merchant:read']
  await visit('/merchant')
  await page.getByLabel('门店介绍').fill('浏览器验收后的介绍')
  await page.getByLabel('营业时间').fill('10:00-22:00')
  await page.getByRole('button', { name: '保存门店资料' }).click()
  await page.getByText('门店资料已保存。').waitFor()
  assert.equal(saved, 1)
  await page.getByLabel('店员用户编号').fill('3')
  await page.getByRole('button', { name: '添加店员' }).click()
  await page.getByText('店员已添加。').waitFor()
  await page.getByRole('button', { name: '移除店员' }).click()
  await page.getByRole('button', { name: '移除店员' }).waitFor({ state: 'detached' })
  assert.equal(memberChanges, 2)
  await page.getByRole('button', { name: '获取资质下载凭据' }).click()
  await page.getByRole('link', { name: '打开短时资质链接' }).waitFor()
  if (process.env.CAMPUS_UI_SCREENSHOT) await page.screenshot({ path: resolve(process.env.CAMPUS_UI_SCREENSHOT), fullPage: true })
  profile.accountType = 'ADMIN'; profile.homePath = '/admin'; profile.id = '9'; profile.permissions = ['merchant:review', 'campus:review', 'audit:read']
  await visit('/admin/merchant-applications')
  await page.getByLabel('申请状态').selectOption('APPROVED')
  await page.getByRole('button', { name: '查看申请', exact: true }).click()
  await page.getByLabel('处理原因').fill('门店临时停业')
  await page.getByRole('button', { name: '暂停门店' }).click()
  await page.getByText('门店状态已更新。').waitFor()
  profile.accountType = 'MERCHANT'; profile.homePath = '/merchant'; profile.id = '1'; profile.permissions = ['merchant:read']
  await visit('/merchant')
  await page.getByText('门店已暂停，当前仅可查看资料。').waitFor()
  assert.equal(await page.getByRole('button', { name: '保存门店资料' }).isDisabled(), true)
  profile.id = '3'
  await visit('/merchant')
  await page.getByRole('heading', { name: store.name }).waitFor()
  assert.equal(await page.getByRole('heading', { name: '店员管理' }).count(), 0)
  profile.accountType = 'ADMIN'; profile.homePath = '/admin'; profile.id = '9'; profile.permissions = ['campus:review', 'audit:read']
  await visit('/admin/campus-verifications')
  await page.getByRole('button', { name: '查看认证申请' }).click()
  assert.equal(campusReads, 0)
  await page.getByRole('button', { name: '读取私有身份资料' }).click()
  await page.getByText('TEST2026', { exact: true }).waitFor()
  assert.equal(campusReads, 1)
  await page.getByLabel('校园审核原因').fill('校园身份核验通过')
  await page.getByRole('button', { name: '通过校园认证' }).click()
  await page.getByText('校园认证审核已保存。').waitFor()
  assert.equal(await page.getByText('TEST2026', { exact: true }).count(), 0)
  await visit('/admin/audit-events')
  await page.getByText('MERCHANT_APPLICATION_REVIEWED', { exact: true }).waitFor()
  await page.getByLabel('资源编号').fill('application-test')
  await page.getByRole('button', { name: '筛选审计' }).click()
  await page.setViewportSize({ width: 390, height: 844 })
  await page.getByRole('heading', { name: '操作审计' }).waitFor()
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'mobile content must not overflow the viewport')
  assert.equal(signed, 2); assert.deepEqual(errors, []); assert.deepEqual(apiErrors, [])
  console.log('PASS: unavailable storage, private upload without JWT, application/history, review and short links, owner/team/version updates, suspension, cashier UI, campus identity review, audit and mobile layout. APIs mocked only in this test.')
} finally {
  await browser?.close()
  await server.close()
}
