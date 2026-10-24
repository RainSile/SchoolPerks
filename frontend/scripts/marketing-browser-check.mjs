// Explicit UI fixtures only; no remote model, publishing or database writes.
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
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } })
  await context.addInitScript(() => sessionStorage.setItem('campus.refresh-token', 'fixture-refresh'))
  const page = await context.newPage(), errors = [], writes = [], rows = []
  let enabled = false, role = 'OWNER', shouldFail = false, pending = false, conflict = false, generationCalls = 0, retryCalls = 0, listPolls = 0
  const now = new Date().toISOString()
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/api/v1/**', async route => {
    const req = route.request(), url = new URL(req.url()), path = url.pathname.replace('/api/v1', ''), method = req.method()
    const respond = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    const paged = items => ({ items, page: 1, size: 20, total: items.length })
    if (method !== 'GET') writes.push(path)
    if (path === '/session-refreshes') return respond({ sessionId: 'fixture-session', tokenType: 'Bearer', accessToken: 'fixture-access', refreshToken: 'fixture-refresh', accessExpiresAt: new Date(Date.now()+900000).toISOString(), refreshExpiresAt: new Date(Date.now()+86400000).toISOString() }, 201)
    if (path === '/users/me') return respond({ id: '2', accountType: 'MERCHANT', homePath: '/merchant', nickname: '测试商家', maskedPhone: '138****0000', permissions: ['merchant:read'] })
    if (path === '/assistant/capabilities') return respond({ enabled: false })
    if (path === '/users/me/merchants') return respond(paged([{ id: '10', name: '聚餐小店', role, status: 'APPROVED' }, { id: '20', name: '另一家门店', role, status: 'APPROVED' }]))
    if (path === '/coupons') { assert.equal(url.searchParams.get('merchantId'), '10'); return respond(paged([{ id: '100', title: '满50减8' }])) }
    const base = '/merchants/10/marketing-drafts'
    if (path === base + '/capabilities') return respond({ enabled: enabled && role !== 'CASHIER', maxBriefLength: 2000, maxTitleLength: 100, maxBodyLength: 4000, dailyChatLimit: 40, dailyChatUsed: generationCalls + retryCalls, tones: ['FRIENDLY','CONCISE','LIVELY'] })
    if (path === base && method === 'GET') {
      listPolls++
      if (pending && listPolls > 1) { rows[0].status = 'DRAFT'; rows[0].body = '异步完成的文案'; pending = false }
      return respond(paged(rows))
    }
    if (path === base && method === 'POST') {
      generationCalls++
      assert.match(req.headers()['idempotency-key'], /^[A-Za-z0-9_-]{8,64}$/)
      const data = req.postDataJSON(); assert.deepEqual(Object.keys(data).sort(), ['brief','couponId','tone']); assert.equal(data.couponId, '100')
      rows.unshift({ id: 'draft-' + generationCalls, merchantId: '10', createdBy: '2', brief: data.brief, tone: data.tone, couponId: data.couponId, status: shouldFail ? 'FAILED' : pending ? 'GENERATING' : 'DRAFT', title: '<script>window.__marketingXss=1</script>', body: shouldFail || pending ? null : '满减规则以活动页面为准', version: 2, attempt: 1, errorCode: shouldFail ? 'MARKETING_AI_UNAVAILABLE' : null, sourceCurrent: true, sources: [{ type: 'COUPON', id: '100', title: '满50减8' }], retryAfter: null, createdAt: now, updatedAt: now })
      if (shouldFail) return respond({ detail: '文案生成暂不可用，可使用原请求重试', code: 'MARKETING_AI_UNAVAILABLE', status: 503 }, 503)
      listPolls = 0
      return respond(rows[0], pending ? 202 : 201)
    }
    if (path.endsWith('/generation-attempts')) { retryCalls++; const row = rows.find(row => path.includes('/'+row.id+'/')); assert.equal(req.postDataJSON().version, row.version); row.status = 'DRAFT'; row.body = '重试完成的草稿'; row.errorCode = null; row.version++; row.attempt++; return respond(row) }
    if (method === 'PATCH' && path.startsWith(base+'/')) {
      const row = rows.find(row => path.endsWith('/'+row.id)), data = req.postDataJSON()
      if (conflict) { row.version++; conflict = false; return respond({ detail: '草稿已更新，请刷新后重试', status: 409 }, 409) }
      assert.equal(data.version, row.version); row.title = data.title; row.body = data.body; row.version++; return respond(row)
    }
    if (path.endsWith('/status-changes')) { const row = rows.find(row => path.includes('/'+row.id+'/')), data = req.postDataJSON(); assert.equal(data.status, 'ARCHIVED'); assert.equal(data.version, row.version); row.status = 'ARCHIVED'; row.version++; return respond(row) }
    throw new Error(`Unmocked API ${method} ${path}`)
  })
  await page.goto(origin+'/merchant/marketing')
  await page.getByText('营销生成服务尚未启用或配置不完整，已有草稿仍可查看和编辑。', { exact: true }).waitFor()
  assert.equal(await page.getByRole('button', { name: '生成并保存草稿', exact: true }).isDisabled(), true)
  enabled = true; await page.reload()
  await page.getByLabel('文案要求', { exact: true }).fill('写一则自然的同学聚餐文案')
  await page.getByLabel('参考本店优惠（可选，最近100条）', { exact: true }).selectOption('100')
  await page.getByRole('button', { name: '生成并保存草稿', exact: true }).click()
  await page.getByRole('heading', { name: '<script>window.__marketingXss=1</script>', exact: true }).waitFor()
  assert.equal(await page.evaluate(() => window.__marketingXss), undefined)
  await page.getByRole('button', { name: '编辑文案', exact: true }).click()
  await page.getByLabel('文案标题', { exact: true }).fill('人工核实标题')
  await page.getByLabel('文案正文', { exact: true }).fill('人工核实优惠条件后保存的文案')
  conflict = true; await page.getByRole('button', { name: '保存人工修改', exact: true }).click()
  await page.getByText('草稿已更新，请刷新后重试', { exact: true }).waitFor()
  await page.getByRole('button', { name: '取消编辑', exact: true }).click()
  await page.getByRole('button', { name: '编辑文案', exact: true }).click()
  await page.getByLabel('文案标题', { exact: true }).fill('人工核实标题')
  await page.getByLabel('文案正文', { exact: true }).fill('人工核实优惠条件后保存的文案')
  await page.getByRole('button', { name: '保存人工修改', exact: true }).click()
  await page.getByRole('heading', { name: '人工核实标题', exact: true }).waitFor()
  rows[0].sourceCurrent = false
  await page.getByRole('button', { name: '刷新草稿', exact: true }).click()
  await page.getByText('参考信息已经变化或不可用，请重新核实当前优惠条件后再使用文案。', { exact: true }).waitFor()
  await page.getByRole('button', { name: '归档草稿', exact: true }).click()
  await page.getByText('草稿已归档。', { exact: true }).waitFor()
  shouldFail = true
  await page.getByLabel('文案要求', { exact: true }).fill('失败后的重试测试')
  await page.getByRole('button', { name: '生成并保存草稿', exact: true }).click()
  await page.getByRole('button', { name: '重试生成', exact: true }).waitFor()
  await page.getByRole('button', { name: '重试生成', exact: true }).click()
  await page.getByText('重试完成的草稿', { exact: true }).waitFor(); assert.equal(retryCalls, 1)
  shouldFail = false; pending = true
  await page.getByLabel('文案要求', { exact: true }).fill('等待生成结果的测试')
  await page.getByRole('button', { name: '生成并保存草稿', exact: true }).click()
  await page.getByText('异步完成的文案', { exact: true }).waitFor({ timeout: 10000 })
  assert.equal(generationCalls, 3)
  role = 'CASHIER'; await page.reload()
  await page.getByText('当前门店状态或角色仅允许查看已有草稿。', { exact: true }).waitFor()
  assert.equal(await page.getByRole('button', { name: '生成并保存草稿', exact: true }).count(), 0)
  assert.equal(await page.getByRole('button', { name: '编辑文案', exact: true }).count(), 0)
  role = 'OWNER'; await page.reload()
  await page.getByRole('heading', { name: 'AI 营销草稿', exact: true }).waitFor()
  await page.setViewportSize({ width: 390, height: 844 })
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth) <= page.viewportSize().width)
  if (process.env.CAMPUS_UI_SCREENSHOT) await page.screenshot({ path: process.env.CAMPUS_UI_SCREENSHOT, fullPage: true })
  assert.ok(writes.every(path => path === '/session-refreshes' || path.includes('/marketing-drafts')))
  assert.deepEqual(errors, [])
  console.log('PASS: generation unavailable, private draft, escaped model text, edit conflict, source warning, archive, retry, accepted polling, cashier read-only and mobile bounds. Fixture APIs only.')
} finally { await browser?.close(); await server.close() }
