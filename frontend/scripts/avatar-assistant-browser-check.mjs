// F: real Vue/browser/canvas, local HTTP fixtures; no SMS, model, database or MinIO calls.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import { mkdir, writeFile, readFile } from 'node:fs/promises'
import path from 'node:path'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'
const { chromium } = createRequire(import.meta.url)(process.env.CAMPUS_PLAYWRIGHT_MODULE || 'playwright')
const root = fileURLToPath(new URL('..', import.meta.url))
const output = path.resolve(root, process.env.CAMPUS_AVATAR_UI_OUTPUT || '../.local/avatar-assistant-ui')
await mkdir(output, { recursive: true })
const server = await createServer({ root, configFile: false, cacheDir: '.local/vite-avatar-ui-cache', plugins: [vue()], server: { host: '127.0.0.1', port: 0 } })
let browser, role = 'USER', uploads = [], puts = [], completions = 0, bindings = [], failPut = false, avatarUrl = ''
const errors = [], checks = [], defaults = []
function pass(name) { checks.push(name); console.log('PASS: ' + name) }
try {
  await server.listen()
  browser = await chromium.launch({ headless: true, executablePath: process.env.CAMPUS_BROWSER_EXECUTABLE })
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 2 })
  await context.addInitScript(() => sessionStorage.setItem('campus.refresh-token', 'fixture-refresh'))
  const page = await context.newPage(); page.on('pageerror', error => errors.push(error.message))
  const origin = `http://127.0.0.1:${server.httpServer.address().port}`
  await page.goto(origin)
  const source = await page.evaluate(() => {
    const canvas = document.createElement('canvas'); canvas.width = 2400; canvas.height = 1600
    const ctx = canvas.getContext('2d')
    for (const [index, color] of ['#df3232', '#2da65d', '#1450dc'].entries()) { ctx.fillStyle = color; ctx.fillRect(index * 800, 0, 800, 1600) }
    return canvas.toDataURL('image/png')
  })
  const defaultImage = await page.evaluate(() => {
    const canvas = document.createElement('canvas'); canvas.width = canvas.height = 512
    const ctx = canvas.getContext('2d'); ctx.fillStyle = '#edf3ff'; ctx.fillRect(0, 0, 512, 512)
    ctx.fillStyle = '#2055ce'; ctx.beginPath(); ctx.arc(256, 256, 150, 0, Math.PI * 2); ctx.fill()
    ctx.fillStyle = '#ffdf7e'; ctx.fillRect(190, 215, 130, 82)
    return canvas.toDataURL('image/png')
  })
  avatarUrl = defaultImage
  defaults.push(...['校园·票券', '暖阳·咖啡', '新绿·书本', '星光·陪伴'].map((label, index) => ({ key: 'fixture-' + index, label, imageUrl: defaultImage })))
  if (process.env.CAMPUS_DEFAULT_AVATAR_DIR) {
    for (const [index, key] of ['campus-blue', 'sunshine', 'fresh-green', 'star-purple'].entries()) defaults[index].imageUrl = 'data:image/png;base64,' + (await readFile(path.join(process.env.CAMPUS_DEFAULT_AVATAR_DIR, key + '.png'))).toString('base64')
  }
  await page.route('**/api/v1/**', async route => {
    const req = route.request(), url = new URL(req.url()), p = url.pathname.replace('/api/v1', '')
    const json = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (p === '/session-refreshes') return json({ accessToken: 'fixture', refreshToken: 'fixture-refresh' })
    if (p === '/users/me') return json({ id: role === 'USER' ? '1' : role === 'MERCHANT' ? '2' : '3', nickname: '布告栏同学', accountType: role, homePath: { USER: '/app', MERCHANT: '/merchant', ADMIN: '/admin' }[role], permissions: role === 'ADMIN' ? ['community:moderate'] : ['merchant:read'], campusVerificationStatus: 'UNVERIFIED', maskedPhone: '138****0000', avatar: { imageUrl: avatarUrl, defaultKey: 'fixture-0' } })
    if (p === '/default-avatars') return json(defaults)
    if (p === '/users/me/campus-verifications') return json([])
    if (p === '/users/me/campus-verifications/capabilities') return json({ photoRequired: true, retentionDays: 30 })
    if (p === '/storage-capabilities') return json({ enabled: true, available: true, maxBytes: 10 * 1024 * 1024, avatarMaxBytes: 2 * 1024 * 1024, allowedTypes: ['image/jpeg', 'image/png'] })
    if (p === '/uploads' && req.method() === 'POST') {
      const body = req.postDataJSON(); uploads.push(body)
      return json({ id: 'upload-' + uploads.length, uploadUrl: origin + '/fixture-storage/' + uploads.length, contentType: body.contentType }, 201)
    }
    if (/^\/uploads\/upload-\d+\/completion$/.test(p)) { completions++; return json({ id: p.split('/')[2], status: 'READY' }) }
    if (p === '/users/me/avatar') { bindings.push(req.postDataJSON()); avatarUrl = 'data:' + uploads.at(-1).contentType + ';base64,' + puts.at(-1).toString('base64'); return json({ imageUrl: avatarUrl }) }
    if (p === '/assistant/capabilities') return json({ enabled: true, accountType: role, maxMessageLength: 2000, timeoutSeconds: 10, tools: [] })
    if (p.includes('capabilities')) return json({ enabled: false })
    if (p === '/schools' || p === '/merchant-categories') return json([])
    return json({ items: [], total: 0, page: 1, size: 10 })
  })
  await page.route('**/fixture-storage/*', async route => {
    const req = route.request()
    assert.equal(req.headers().authorization, undefined, 'Storage must not receive platform token')
    assert.equal(req.headers()['content-type'], uploads.at(-1).contentType)
    puts.push(req.postDataBuffer())
    return route.fulfill({ status: failPut ? 403 : 200, body: '' })
  })
  await page.goto(origin + '/account')
  const avatarPicker = page.getByRole('dialog', { name: '换个头像', exact: true })
  async function openPicker() { await page.getByRole('button', { name: '更换头像', exact: true }).click(); await avatarPicker.waitFor() }
  await openPicker()
  assert.equal(await avatarPicker.locator('.avatar-option').count(), 4)
  assert.equal(await avatarPicker.locator('.avatar-option').allInnerTexts().then(values => values.join('')), '')
  pass('Current avatar opens compact modal with four image-only defaults and an adjacent plus button')
  const group = page.getByRole('group', { name: '布告栏上的我', exact: true })
  const picker = group.locator('input[type=file]')
  const file = { name: 'original.png', mimeType: 'image/png', buffer: Buffer.from(source.split(',')[1], 'base64') }
  await page.getByRole('button', { name: '挑张照片做头像' }).waitFor()
  await page.waitForFunction(() => !document.querySelector('.avatar-file-input').disabled)
  await page.getByRole('button', { name: '挑张照片做头像' }).focus()
  await picker.setInputFiles(file)
  const crop = page.getByRole('dialog', { name: '给头像找个好位置' })
  await crop.waitFor(); await page.getByRole('button', { name: '裁好，就用这张' }).waitFor()
  assert.equal(uploads.length, 0, 'Original must not be uploaded before crop confirmation')
  assert.equal(await page.evaluate(() => document.body.style.overflow), 'hidden')
  pass('Photo opens local crop dialog; original bytes stay local')
  await page.getByLabel('缩放照片').fill('2')
  const frame = page.getByRole('group', { name: '头像裁剪区域' }), box = await frame.boundingBox()
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2); await page.mouse.down()
  await page.mouse.move(box.x + box.width / 2 - 400, box.y + box.height / 2, { steps: 10 }); await page.mouse.up()
  await page.screenshot({ path: path.join(output, 'avatar-crop-desktop.png'), fullPage: false })
  await page.getByRole('button', { name: '裁好，就用这张' }).click()
  await avatarPicker.waitFor({ state: 'hidden' }); await page.getByText('头像已更新。', { exact: true }).waitFor()
  assert.equal(uploads.length, 1); assert.equal(uploads[0].purpose, 'USER_AVATAR'); assert.equal(uploads[0].contentType, 'image/png'); assert.equal(uploads[0].size, puts[0].length)
  assert.equal(completions, 1); assert.equal(bindings.length, 1)
  const pixels = await page.evaluate(async base64 => {
    const image = new Image(); image.src = 'data:image/png;base64,' + base64; await image.decode()
    const canvas = document.createElement('canvas'); canvas.width = image.width; canvas.height = image.height
    const ctx = canvas.getContext('2d'); ctx.drawImage(image, 0, 0)
    return { width: image.width, height: image.height, samples: [ctx.getImageData(1, 1, 1, 1).data, ctx.getImageData(256, 256, 1, 1).data].map(a => Array.from(a)) }
  }, puts[0].toString('base64'))
  assert.equal(pixels.width, 512); assert.equal(pixels.height, 512)
  for (const [r, g, b] of pixels.samples) assert.ok(r < 30 && g > 70 && g < 90 && b > 210, 'Uploaded pixels must match the dragged blue crop')
  pass('Drag + zoom crop produces selected region as lossless 512px PNG; signed upload and sealing precede binding')
  assert.deepEqual(bindings[0], { fileId: 'upload-1' })
  await page.locator('.current-avatar img').evaluate(img => img.decode())
  assert.equal(await page.locator('.current-avatar img').evaluate(img => getComputedStyle(img).imageRendering), 'auto')
  assert.equal(await page.locator('.current-avatar .user-avatar').evaluate(el => getComputedStyle(el).maskImage), 'radial-gradient(rgb(255, 255, 255), rgb(255, 255, 255))')
  pass('Explicit avatar confirmation updates profile; smooth thumbnail and circular clipping are active')
  const count = uploads.length
  await openPicker()
  await picker.setInputFiles(file); await crop.waitFor()
  await page.keyboard.press('Escape'); await crop.waitFor({ state: 'hidden' })
  assert.equal(uploads.length, count); assert.equal(await page.evaluate(() => document.body.style.overflow), 'hidden')
  await picker.setInputFiles(file); await crop.waitFor(); await page.getByRole('button', { name: '取消裁剪' }).click()
  assert.equal(uploads.length, count)
  await page.getByRole('button', { name: '关闭头像选择' }).click()
  assert.notEqual(await page.evaluate(() => document.body.style.overflow), 'hidden')
  await openPicker()
  pass('Escape/cancel sends no upload; nested modal scroll lock restores after picker closes')
  await picker.setInputFiles({ name: 'bad.png', mimeType: 'image/png', buffer: Buffer.from('invalid image') })
  await page.getByText('这张图片无法读取，请换一张JPEG或PNG照片。').waitFor()
  assert.equal(await page.getByRole('button', { name: '裁好，就用这张' }).isDisabled(), true)
  await page.getByRole('button', { name: '取消裁剪' }).click()
  failPut = true
  await picker.setInputFiles(file); await crop.waitFor(); await page.getByRole('button', { name: '裁好，就用这张' }).click()
  await avatarPicker.getByText('图片上传失败，请重试', { exact: true }).waitFor()
  assert.equal(completions, 1); assert.equal(bindings.length, 1)
  assert.equal(await page.getByRole('button', { name: '贴上这张头像', exact: true }).count(), 0)
  pass('Unreadable photo is blocked; failed storage write cannot seal or bind an avatar')
  failPut = false
  await page.setViewportSize({ width: 390, height: 844 })
  const jpegSource = await page.evaluate(async src => {
    const img = new Image(); img.src = src; await img.decode()
    const canvas = document.createElement('canvas'); canvas.width = img.width; canvas.height = img.height
    canvas.getContext('2d').drawImage(img, 0, 0); return canvas.toDataURL('image/jpeg', .9)
  }, source)
  await picker.setInputFiles({ name: 'camera-photo.jpg', mimeType: 'image/jpeg', buffer: Buffer.from(jpegSource.split(',')[1], 'base64') }); await crop.waitFor(); await page.getByRole('button', { name: '裁好，就用这张' }).waitFor()
  await frame.focus(); const before = await frame.locator('img').evaluate(el => el.style.left)
  await page.keyboard.press('ArrowLeft'); const after = await frame.locator('img').evaluate(el => el.style.left)
  assert.notEqual(before, after)
  const cdp = await context.newCDPSession(page), mobileFrame = await frame.boundingBox()
  const touchX = mobileFrame.x + mobileFrame.width / 2, touchY = mobileFrame.y + mobileFrame.height / 2
  const touchBefore = await frame.locator('img').evaluate(el => el.style.left)
  await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x: touchX, y: touchY }] })
  await cdp.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x: touchX + 30, y: touchY }] })
  await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] })
  assert.notEqual(await frame.locator('img').evaluate(el => el.style.left), touchBefore)
  await cdp.detach()
  await page.getByRole('button', { name: '取消裁剪' }).focus(); await page.keyboard.press('Shift+Tab')
  assert.equal(await page.getByRole('button', { name: '裁好，就用这张' }).evaluate(el => document.activeElement === el), true)
  await page.screenshot({ path: path.join(output, 'avatar-crop-mobile.png') })
  await page.getByRole('button', { name: '重新居中' }).click(); assert.equal(await page.getByLabel('缩放照片').inputValue(), '1')
  await page.getByRole('button', { name: '裁好，就用这张' }).click(); await avatarPicker.waitFor({ state: 'hidden' }); await page.getByText('头像已更新。', { exact: true }).waitFor()
  assert.equal(completions, 2)
  assert.equal(uploads.at(-1).contentType, 'image/jpeg')
  await page.screenshot({ path: path.join(output, 'avatar-account-mobile.png'), fullPage: true })
  for (const width of [320, 390, 1440]) {
    await page.setViewportSize({ width, height: 900 }); await openPicker()
    const bounds = await avatarPicker.boundingBox()
    assert.ok(bounds.x >= 0 && bounds.x + bounds.width <= width)
    assert.equal(await avatarPicker.evaluate(el => el.scrollWidth > el.clientWidth), false)
    await page.screenshot({ path: path.join(output, `avatar-picker-${width}.png`) })
    if (width === 390) await avatarPicker.screenshot({ path: path.join(output, 'avatar-picker-compact.png') })
    await page.getByRole('button', { name: '关闭头像选择' }).click()
    const inputBounds = await page.getByLabel('昵称', { exact: true }).boundingBox()
    const buttonBounds = await page.getByRole('button', { name: '保存昵称', exact: true }).boundingBox()
    assert.ok(Math.abs(inputBounds.y - buttonBounds.y) < 2, 'Nickname editor and save button share one row')
  }
  pass('Mobile touch drag, JPEG crop, keyboard adjustment, reset, focus loop, nickname row and retry succeed')
  // Student cards deliberately retain their original, uncropped camera/gallery path.
  await page.goto(origin + '/account/campus-verification')
  await page.getByRole('group', { name: '学生证首页照片' }).waitFor()
  assert.equal(await page.locator('input[capture="environment"]').count(), 1)
  assert.equal(await page.getByRole('dialog', { name: '给头像找个好位置' }).count(), 0)
  pass('Student-card camera and gallery remain available without avatar cropping')
  await page.goto(origin + '/account')
  await page.getByRole('button', { name: '打开智能助手' }).click()
  await page.getByRole('button', { name: '今天有什么优惠？' }).click()
  assert.match(await page.getByLabel('向助手提问').inputValue(), /可领取/)
  const compose = await page.locator('.assistant-compose').evaluate(el => ({ background: getComputedStyle(el).backgroundImage, animation: getComputedStyle(el).animationName, border: getComputedStyle(el).borderTopWidth }))
  assert.match(compose.background, /conic-gradient/); assert.match(compose.animation, /^assistant-border-flow/)
  assert.equal(await page.locator('.assistant-compose').evaluate(el => getComputedStyle(el).boxShadow), 'none')
  assert.equal(await page.getByLabel('向助手提问').evaluate(el => getComputedStyle(el).outlineStyle), 'none')
  assert.equal(uploads.length, 3)
  pass('Role-specific quick question fills editor without automatically calling the model')
  // Chinese IME Enter should commit composition, not submit a business request.
  let assistantCalls = 0
  page.on('request', req => { if (req.url().endsWith('/assistant/conversations')) assistantCalls++ })
  await page.getByLabel('向助手提问').dispatchEvent('keydown', { key: 'Enter', code: 'Enter', isComposing: true })
  assert.equal(assistantCalls, 0)
  pass('Chinese input composition Enter does not accidentally send a question')
  for (const width of [320, 390, 768, 1440]) {
    await page.setViewportSize({ width, height: 900 })
    const panel = page.getByRole('dialog', { name: '校园生活助手' })
    const bounds = await panel.boundingBox()
    assert.ok(bounds.x >= 0 && bounds.x + bounds.width <= width && bounds.y >= 0 && bounds.y + bounds.height <= 900)
    assert.equal(await panel.evaluate(el => el.scrollWidth > el.clientWidth), false)
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false)
    await page.screenshot({ path: path.join(output, `assistant-${width}.png`) })
    if (width === 390) await page.locator('.assistant-compose').screenshot({ path: path.join(output, 'assistant-input-cool.png') })
  }
  pass('Assistant stays inside viewport at 320/390/768/1440px with no horizontal overflow')
  for (const next of ['MERCHANT', 'ADMIN']) {
    role = next; await page.goto(origin + '/account'); await page.getByRole('button', { name: '打开智能助手' }).click()
    const name = next === 'MERCHANT' ? '门店经营助手' : '平台管理助手'
    await page.getByRole('dialog', { name }).waitFor()
    await page.getByRole('button', { name: next === 'MERCHANT' ? '我的门店概况' : '待审核的社区内容' }).waitFor()
    assert.equal(await page.getByRole('button', { name: '今天有什么优惠？' }).count(), 0)
  }
  pass('Merchant/admin panel and starter questions follow current server-side account role')
  await page.emulateMedia({ reducedMotion: 'reduce' })
  assert.equal(await page.locator('.assistant-launcher').evaluate(el => getComputedStyle(el).transitionDuration), '0s')
  assert.deepEqual(errors, [])
  pass('Reduced motion respected; browser reports no unhandled runtime errors')
  await writeFile(path.join(output, 'results.json'), JSON.stringify({ evidence: 'F', passed: checks.length, checks, smsSends: 0, modelCalls: 0, liveStorageCalls: 0 }, null, 2))
} finally { await browser?.close(); await server.close() }
