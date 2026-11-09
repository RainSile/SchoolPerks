// Regression AM-UI-002: the merchant workspace links to its own application history.
// Every API response is a local fixture; no real account or external request is used.
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
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
  browser = await chromium.launch({ headless: true, ...(process.env.CAMPUS_BROWSER_EXECUTABLE ? { executablePath: process.env.CAMPUS_BROWSER_EXECUTABLE } : {}) })
  const context = await browser.newContext()
  await context.addInitScript(() => sessionStorage.setItem('campus.refresh-token', 'fixture-refresh'))
  await context.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '')
    const respond = value => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(value) })
    const store = { id: '11', name: '测试门店', schoolId: '1', category: 'DINING', address: '测试地址', status: 'APPROVED', version: 0, description: '', businessHours: '', role: 'OWNER', qualificationReady: true, pendingQualificationRequestId: null }
    if (path === '/session-refreshes') return respond({ accessToken: 'fixture-access', refreshToken: 'fixture-refresh' })
    if (path === '/users/me') return respond({ id: '1', accountType: 'MERCHANT', homePath: '/merchant', nickname: '测试店主', maskedPhone: '***', permissions: ['merchant:read'] })
    if (path === '/assistant/capabilities') return respond({ enabled: false })
    if (path === '/users/me/merchants') return respond({ items: [store], page: 1, size: 20, total: 1 })
    if (path === '/merchants/11/workspace') return respond(store)
    if (path === '/merchants/11/members' || path === '/users/me/merchant-applications') return respond({ items: [], page: 1, size: 20, total: 0 })
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{}' })
  })
  const page = await context.newPage()
  await page.goto(origin + '/merchant', { waitUntil: 'networkidle' })
  await page.getByRole('link', { name: '我的入驻申请 →', exact: true }).click()
  const reachedHistory = await page.waitForURL('**/merchant/applications', { timeout: 2000 }).then(() => true, () => false)
  const result = { level: 'F', check: 'merchant can open workspace application-history link', passed: reachedHistory, expected: '/merchant/applications', actual: new URL(page.url()).pathname }
  if (process.env.CAMPUS_RETEST_EVIDENCE_FILE) await fs.writeFile(process.env.CAMPUS_RETEST_EVIDENCE_FILE, JSON.stringify(result, null, 2))
  console.log(JSON.stringify(result))
  assert.ok(reachedHistory, 'AM-UI-002: workspace history link redirects merchant back to workspace')
} finally {
  await browser?.close()
  await server.close()
}
