// Opt-in: real browser PUT/GET against the configured private object store; no application UI server.
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'
import http from 'node:http'
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.CAMPUS_PLAYWRIGHT_MODULE || 'playwright')
const root = fileURLToPath(new URL('../', import.meta.url))
const filename = path.resolve(process.argv[2] || '')
if (!filename.startsWith(path.resolve(root, '.local') + path.sep)) throw Error('Private probe input must stay inside .local')
const input = JSON.parse(await fs.readFile(filename, 'utf8'))
const expected = new URL(process.env.MINIO_PUBLIC_ENDPOINT || process.env.MINIO_ENDPOINT)
if (new URL(input.url).origin !== expected.origin) throw Error('Probe target does not match the configured storage origin')
const origin = process.env.CAMPUS_STORAGE_UI_ORIGIN || 'http://127.0.0.1:5173'
if (!['127.0.0.1', 'localhost'].includes(new URL(origin).hostname)) throw Error('Use a loopback browser fixture origin')
let browser, fixture
try {
  // A routed synthetic document has no loopback network address and Chrome blocks its
  // local-network fetch. Serve a real local fixture; preserve normal browser security.
  fixture = http.createServer((request, response) => {
    response.writeHead(request.url === '/__storage_probe__' ? 200 : 404, { 'Content-Type': 'text/html' })
    response.end('<!doctype html><link rel="icon" href="data:,"><title>Private storage CORS probe</title>')
  })
  await new Promise((resolve, reject) => fixture.once('error', reject).listen(Number(new URL(origin).port), new URL(origin).hostname, resolve))
  browser = await chromium.launch({ headless: true, executablePath: process.env.CAMPUS_BROWSER_EXECUTABLE })
  const page = await browser.newPage()
  page.on('requestfailed', request => console.error('Browser network status: ' + request.failure()?.errorText))
  page.on('console', message => { if (message.type() === 'error') console.error(message.text().replace(/https?:\/\/[^\s'"<>]+/g, '[URL omitted]')) })
  await page.goto(origin + '/__storage_probe__')
  const result = await page.evaluate(async input => {
    const body = input.payloadBase64 ? Uint8Array.from(atob(input.payloadBase64), ch => ch.charCodeAt(0)) : undefined
    const response = await fetch(input.url, {
      method: input.action === 'upload' ? 'PUT' : 'GET',
      headers: input.action === 'upload' ? { 'Content-Type': input.contentType } : {},
      body, credentials: 'omit', signal: AbortSignal.timeout(10000),
    })
    const bytes = new Uint8Array(await response.arrayBuffer())
    return { status: response.status, size: bytes.length, bodyBase64: response.ok ? btoa(String.fromCharCode(...bytes)) : '' }
  }, input)
  console.log(JSON.stringify(result))
} catch (error) {
  const reason = String(error?.message || 'Unknown browser failure').replace(/https?:\/\/[^\s'"<>]+/g, '[URL omitted]')
  console.error('Live storage browser probe failed: ' + reason)
  process.exitCode = 1
} finally {
  await browser?.close()
  fixture?.closeAllConnections()
  await new Promise(resolve => fixture?.listening ? fixture.close(resolve) : resolve())
}
