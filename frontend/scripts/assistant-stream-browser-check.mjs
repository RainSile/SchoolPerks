import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import { setTimeout as delay } from 'node:timers/promises'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'
const { chromium } = createRequire(import.meta.url)(process.env.CAMPUS_PLAYWRIGHT_MODULE || 'playwright')
let type = 'USER', mode = 'normal', started = 0, cancelled = 0, completed = 0
const actor = () => ({ USER: '1', MERCHANT: '2', ADMIN: '3' })[type]
const conversations = new Map(), errors = [], remoteImages = []
const source = ['# 校园', '\n\n**优惠**已核实。\n\n', '| 项目 | 结果 |\n| --- | --- |\n| 优惠券 | 已领取 |\n\n', '```java\nvar campus = 1;\n```\n\n', '[安全链接](https://example.com)\n\n<script>window.__assistantXss=1</script>\n\n[危险](javascript:alert(1))\n\n![追踪](https://tracker.invalid/pixel.png)']
const server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false, cacheDir: '.local/vite-assistant-stream-cache', plugins: [vue(), {
  name: 'assistant-delayed-stream-fixture', configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
    const match = req.url?.match(/^\/api\/v1\/assistant\/conversations\/([^/]+)\/message-streams$/)
    if (!match || req.method !== 'POST') return next()
    started++; const identity = actor(), scenario = mode
    assert.equal(match[1], `conversation-${identity}`)
    assert.equal(req.headers.authorization, `Bearer fixture-${identity}`)
    assert.ok(req.headers['idempotency-key'])
    let raw = ''; for await (const chunk of req) raw += chunk
    const input = JSON.parse(raw)
    let done = false; res.on('close', () => { if (!done) cancelled++ })
    res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-store' })
    const frame = async (event, data) => {
      const text = `event:${event}\r\ndata:${JSON.stringify(data)}\r\n\r\n`, bytes = Buffer.from(text)
      const chinese = bytes.indexOf(Buffer.from('校')), split = chinese >= 0 ? chinese + 1 : Math.floor(bytes.length / 2)
      if (res.destroyed) return
      res.write(bytes.subarray(0, split)); await delay(10); if (!res.destroyed) res.write(bytes.subarray(split))
    }
    await frame('round', { round: 1 })
    for (const chunk of source) { if (res.destroyed) return; await frame('delta', { text: chunk }); await delay(scenario === 'slow' ? 700 : 150) }
    if (scenario === 'revoked') { await frame('error', { status: 401, code: 'INVALID_SESSION', detail: '请重新登录' }); done = true; return res.end() }
    const row = { id: `message-${++completed}`, conversationId: match[1], status: 'COMPLETED', question: input.message, reply: source.join(''), sources: [], tools: [], errorCode: null }
    conversations.get(match[1]).push(row); await frame('completed', row); done = true; res.end()
  }) },
}], server: { host: '127.0.0.1', port: 0 } })
let browser
try {
  await server.listen()
  browser = await chromium.launch({ headless: true, executablePath: process.env.CAMPUS_BROWSER_EXECUTABLE })
  const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
  await context.addInitScript(() => sessionStorage.setItem('campus.refresh-token', 'fixture-refresh'))
  const page = await context.newPage(); page.on('pageerror', error => errors.push(error.message))
  page.on('request', request => { if (request.url().includes('tracker.invalid')) remoteImages.push(request.url()) })
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname.replace('/api/v1', '')
    if (path.endsWith('/message-streams')) return route.continue()
    const reply = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (path === '/session-refreshes') return reply({ accessToken: `fixture-${actor()}`, refreshToken: 'fixture-refresh' })
    if (path === '/users/me') return reply({ id: actor(), nickname: `fixture-${type}`, accountType: type, homePath: type === 'MERCHANT' ? '/merchant' : '/app', permissions: type === 'MERCHANT' ? ['merchant:read'] : [] })
    if (path === '/assistant/capabilities') return reply({ enabled: true, accountType: type, maxMessageLength: 2000, timeoutSeconds: 20, tools: [] })
    if (path === '/assistant/conversations') { const id = `conversation-${actor()}`; conversations.set(id, []); return reply({ id, accountType: type }, 201) }
    const history = path.match(/^\/assistant\/conversations\/([^/]+)\/messages$/)
    if (history) { assert.equal(history[1], `conversation-${actor()}`); return reply(conversations.get(history[1]) || []) }
    if (path.includes('capabilities')) return reply({ enabled: false })
    if (path === '/schools' || path === '/merchant-categories') return reply([])
    if (path === '/platform') return reply({})
    return reply({ items: [], total: 0, page: 1, size: 10 })
  })
  const origin = `http://127.0.0.1:${server.httpServer.address().port}`
  await page.goto(origin + '/login'); await page.waitForURL(origin + '/app')
  await page.getByRole('button', { name: '打开智能助手' }).click()
  await page.getByLabel('向助手提问').fill('Markdown 与真实分段传输')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await page.locator('.assistant-live h1').waitFor(); assert.equal(completed, 0, 'Partial content must appear before completion')
  await page.getByRole('button', { name: '发送', exact: true }).waitFor()
  assert.equal(await page.locator('.assistant-answer table').count(), 1)
  assert.match(await page.locator('.assistant-answer pre code').innerText(), /var campus = 1/)
  const link = page.locator('.assistant-answer a', { hasText: '安全链接' }); assert.equal(await link.getAttribute('target'), '_blank'); assert.match(await link.getAttribute('rel'), /noopener/)
  assert.equal(await page.locator('.assistant-answer a[href^="javascript:"]').count(), 0)
  assert.equal(await page.locator('.assistant-answer img,.assistant-answer script').count(), 0)
  assert.equal(await page.evaluate(() => window.__assistantXss), undefined); assert.deepEqual(remoteImages, [])
  mode = 'slow'; await page.getByLabel('向助手提问').fill('停止输出测试'); await page.getByLabel('允许本次业务操作', { exact: false }).check()
  await page.getByRole('button', { name: '发送', exact: true }).click(); await page.locator('.assistant-live').waitFor()
  await page.getByRole('button', { name: '停止', exact: true }).click(); await page.getByText('已提交的业务操作不会撤回', { exact: false }).waitFor()
  await delay(200); assert.equal(started, 2); assert.equal(completed, 1); assert.ok(cancelled >= 1, 'Stop must close the network stream')
  await page.getByLabel('向助手提问').fill('切换身份测试'); await page.getByRole('button', { name: '发送', exact: true }).click(); await page.locator('.assistant-live').waitFor()
  type = 'MERCHANT'; await page.goto(origin + '/login'); await page.waitForURL(origin + '/merchant')
  await page.getByRole('button', { name: '打开智能助手' }).click(); await page.getByRole('dialog', { name: '门店经营助手' }).waitFor()
  assert.equal(await page.getByText('切换身份测试', { exact: true }).count(), 0)
  assert.equal(await page.locator('.assistant-answer').count(), 0)
  assert.equal(await page.getByLabel('允许本次业务操作', { exact: false }).isChecked(), false)
  mode = 'revoked'; await page.getByLabel('向助手提问').fill('撤销会话测试'); await page.getByRole('button', { name: '发送', exact: true }).click()
  await page.getByText('登录后即可使用助手。', { exact: false }).waitFor(); assert.equal(await page.locator('.assistant-answer').count(), 0)
  assert.deepEqual(errors, [])
  console.log('PASS: real delayed SSE/fragmented UTF-8, typewriter, Markdown/table/code/link, XSS/image blocking, stop/no automatic replay, identity isolation, revoked-session clearing, mobile layout.')
} finally { await browser?.close(); await server.close() }
