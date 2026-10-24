// Explicit opt-in: uses the configured live AMap JS service, no application accounts.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'node:url'
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.CAMPUS_PLAYWRIGHT_MODULE || 'playwright')
assert.ok(process.env.CAMPUS_MAP_TEST_TARGET, 'Set CAMPUS_MAP_TEST_TARGET to the temporary loopback backend')
const target = new URL(process.env.CAMPUS_MAP_TEST_TARGET)
assert.equal(target.hostname, '127.0.0.1')
const server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false, plugins: [vue()], server: { host: '127.0.0.1', port: 0, proxy: { '/api': { target: target.origin } } } })
let browser
const errors = [], proxyResults = []
try {
  await server.listen()
  browser = await chromium.launch({ headless: true, executablePath: process.env.CAMPUS_BROWSER_EXECUTABLE })
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 } })
  page.on('pageerror', e => errors.push(e.message))
  page.on('response', async response => {
    const path = new URL(response.url()).pathname
    if (path.includes('/maps/_AMapService/')) {
      const body = await response.text().catch(() => '')
      proxyResults.push({ path, status: response.status(), success: body.includes('"status":"1"'), error: body.match(/"(?:info|code)"\s*:\s*"([A-Z_]+)"/)?.[1] })
    }
  })
  await page.goto(`http://127.0.0.1:${server.httpServer.address().port}/nearby`)
  await page.getByLabel('WGS84 经度').fill('116.4')
  await page.getByLabel('WGS84 纬度').fill('39.9')
  // Entering /nearby must load the SDK and map without any map-loading click.
  await page.waitForFunction(() => document.querySelector('.amap-maps'), { timeout: 30000 })
  await page.waitForFunction(() => document.querySelector('.amap-marker'), { timeout: 30000 })
  const converted = await page.evaluate(() => new Promise(resolve => window.AMap.convertFrom([[116.4, 39.9]], 'gps', (status, result) => resolve({ status, longitude: result.locations?.[0]?.getLng(), latitude: result.locations?.[0]?.getLat() }))))
  assert.equal(converted.status, 'complete')
  assert.ok(Math.abs(converted.longitude - 116.4) > 0.001, 'The official SDK must convert the display coordinate from WGS84 to GCJ02')
  assert.ok(proxyResults.every(r => r.status === 200), 'Official SDK initialization proxy requests must succeed')
  assert.deepEqual(errors, [])
  if (process.env.CAMPUS_UI_SCREENSHOT) await page.screenshot({ path: process.env.CAMPUS_UI_SCREENSHOT })
  console.log(JSON.stringify({ result: 'PASS', proxyResults }))
} catch (error) {
  // Never log provider URLs or configuration; JS key and security code stay out of output.
  console.error('Live AMap check failed:', error.name, error.message.split('\n')[0])
  console.error(JSON.stringify({ errors, proxyResults }))
  throw new Error('Live AMap browser verification failed')
} finally { await browser?.close(); await server.close() }
