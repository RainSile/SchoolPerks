// Regression for report AM-UI-001. HTTP fixtures only; no real data or credentials.
// Originally failed; keep missing-reason feedback discoverable in both viewport sizes.
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.CAMPUS_PLAYWRIGHT_MODULE || 'playwright')
const server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false, plugins: [vue()], server: { host: '127.0.0.1', port: 0 } })
const results = []
let browser
try {
  await server.listen()
  const origin = `http://127.0.0.1:${server.httpServer.address().port}`
  browser = await chromium.launch({ headless: true, ...(process.env.CAMPUS_BROWSER_EXECUTABLE ? { executablePath: process.env.CAMPUS_BROWSER_EXECUTABLE } : {}) })
  for (const [width, height] of [[1440, 600], [390, 844]]) {
    const context = await browser.newContext({ viewport: { width, height } })
    let commentStatus = 'PENDING', writes = 0
    await context.addInitScript(() => sessionStorage.setItem('campus.refresh-token', 'fixture-refresh'))
    await context.route('**/api/v1/**', async route => {
      const request = route.request(), url = new URL(request.url()), path = url.pathname.replace('/api/v1', '')
      const reply = (value, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) })
      const paged = items => ({ items, page: 1, size: 20, total: items.length })
      if (path === '/session-refreshes') return reply({ accessToken: 'fixture-access', refreshToken: 'fixture-refresh', tokenType: 'Bearer' }, 201)
      if (path === '/users/me') return reply({ id: '2', accountType: 'ADMIN', homePath: '/admin', nickname: '测试审核员', maskedPhone: '***', permissions: ['community:moderate'] })
      if (path === '/assistant/capabilities') return reply({ enabled: false, configured: false })
      if (path === '/admin/post-review-requests') return reply(paged([]))
      if (path === '/admin/post-comments') return reply(paged(commentStatus === 'PENDING' ? [{ id: 'comment-1', postId: 'post-1', body: '审核反馈测试。'.repeat(190), status: commentStatus, version: 0, createdAt: '2026-11-09T00:00:00Z' }] : []))
      if (path === '/admin/post-comments/comment-1' && request.method() === 'PATCH') {
        const input = request.postDataJSON()
        assert.equal(input.status, 'APPROVED')
        assert.ok(input.reason.trim())
        writes++
        commentStatus = 'APPROVED'
        return reply({ id: 'comment-1', postId: 'post-1', status: commentStatus, version: 1 })
      }
      return reply({ title: 'Unexpected fixture request' }, 404)
    })
    try {
      const page = await context.newPage()
      await page.goto(origin + '/admin/community', { waitUntil: 'networkidle' })
      await Promise.all([page.waitForResponse(response => response.url().includes('/admin/post-comments?')), page.getByLabel('处理队列').selectOption('comments')])
      await page.getByRole('button', { name: '通过评论', exact: true }).click()
      const message = page.getByRole('status').filter({ hasText: '请先填写处理原因。' })
      await message.waitFor()
      const box = await message.boundingBox()
      const visible = Boolean(box && box.y >= 0 && box.y + box.height <= height)
      const focused = await page.getByLabel('处理原因 / 结论').evaluate(element => document.activeElement === element)
      results.push({ width, height, check: 'required reason feedback is discoverable', passed: visible || focused, visible, focused, writes })
      assert.equal(writes, 0, 'Invalid input must not perform a moderation write')
      await page.getByLabel('处理原因 / 结论').fill('测试通过')
      await page.getByRole('button', { name: '通过评论', exact: true }).click()
      await page.getByRole('status').filter({ hasText: '评论处理已保存。' }).waitFor()
      results.push({ width, check: 'valid approval still submits and refreshes', passed: writes === 1 && commentStatus === 'APPROVED' })
    } finally {
      await context.close()
    }
  }
  if (process.env.CAMPUS_RETEST_EVIDENCE_FILE) await fs.writeFile(process.env.CAMPUS_RETEST_EVIDENCE_FILE, JSON.stringify({ level: 'F', results }, null, 2))
  console.log(JSON.stringify(results))
  assert.ok(results.every(result => result.passed), 'AM-UI-001: missing-reason validation is outside the viewport and does not focus its field')
} finally {
  await browser?.close()
  await server.close()
}
