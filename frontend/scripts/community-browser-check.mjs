// Test-only HTTP fixtures. Production pages always use the real REST APIs.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.CAMPUS_PLAYWRIGHT_MODULE || 'playwright')
const server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)), configFile: false, plugins: [vue()], server: { host: '127.0.0.1', port: 0 } })
let browser
try {
  await server.listen(); const port = server.httpServer.address().port, origin = `http://127.0.0.1:${port}`
  browser = await chromium.launch({ headless: true, ...(process.env.CAMPUS_BROWSER_EXECUTABLE ? { executablePath: process.env.CAMPUS_BROWSER_EXECUTABLE } : {}) })
  const context = await browser.newContext({ viewport: { width: 1280, height: 960 }, geolocation: { longitude: 116.4, latitude: 39.9 } })
  await context.grantPermissions(['geolocation'], { origin })
  await context.addInitScript(() => { if (location.protocol === 'http:' || location.protocol === 'https:') sessionStorage.setItem('campus.refresh-token', 'test-refresh') })
  const page = await context.newPage(), errors = [], apiErrors = []
  page.on('pageerror', e => errors.push(e.message))
  let role = 'author', post = null, review = null, comments = [], report = null, liked = false, location = null, jobs = [], nearbyCalls = 0
  const now = new Date().toISOString(), expires = new Date(Date.now() + 3600000).toISOString()
  const xss = '<img src=x onerror="window.__communityXss=1"> 面馆探店体验'
  const permission = () => role === 'admin' ? ['community:moderate', 'search:manage'] : role === 'owner' ? ['merchant:read'] : []
  const paged = (items, url) => ({ items, total: items.length, page: Number(url.searchParams.get('page') || 1), size: 20 })
  await context.route('**/api/v1/**', async route => {
    try {
      const req = route.request(), url = new URL(req.url()), path = url.pathname.replace('/api/v1', ''), method = req.method()
      const respond = (value, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(value) })
      if (path === '/assistant/capabilities') return respond({ configured: false })
      if (path === '/map-capabilities') return respond({ configured: false })
      if (path === '/session-refreshes') return respond({ sessionId: 'test', tokenType: 'Bearer', accessToken: 'test-access', refreshToken: 'test-refresh', accessExpiresAt: expires, refreshExpiresAt: expires }, 201)
      if (path === '/users/me') return respond({ id: role === 'admin' ? '2' : role === 'owner' ? '3' : '1', accountType: role === 'admin' ? 'ADMIN' : role === 'owner' ? 'MERCHANT' : 'USER', homePath: role === 'admin' ? '/admin' : role === 'owner' ? '/merchant' : '/app', nickname: '第五阶段验收用户', maskedPhone: '139****0000', permissions: permission() })
      if (path === '/coupons') return respond(paged([], url))
      if (path === '/schools') return respond([{ id: '1', name: '测试校园' }])
      if (path === '/community-capabilities') return respond({ enabled: true, persistence: 'MYSQL', mongoAvailable: false, automaticModerationAvailable: false, maxImages: 6, maxBodyLength: 6000, maxCommentLength: 2000 })
      if (path === '/storage-capabilities') return respond({ enabled: false, available: false, maxBytes: 10485760, allowedTypes: ['image/png', 'image/jpeg'], uploadTtlSeconds: 300, downloadTtlSeconds: 120 })
      if (path === '/uploads') return respond(paged([], url))
      if (path === '/merchant-categories') return respond(['DINING'])
      if (path === '/merchants') return respond(paged([{ id: '11', name: '校园面馆' }], url))
      if (path === '/users/me/merchants') return respond(paged([{ id: '11', name: '校园面馆', role: 'OWNER', status: 'APPROVED' }], url))
      if (path === '/posts' && method === 'POST') {
        const input = req.postDataJSON(); assert.equal(input.schoolId, '1'); assert.equal(input.merchantId, '11'); assert.deepEqual(input.content.imageIds, [])
        post = { id: '21', schoolId: '1', merchantId: '11', ...input.content, status: 'DRAFT', version: 0, likeCount: 0, commentCount: 0, createdAt: now, publishedAt: null, reviewReason: null }; return respond(post, 201)
      }
      if (path === '/users/me/posts') return respond(paged(post ? [post] : [], url))
      if (path === '/users/me/post-comments') return respond(paged(comments, url))
      if (path === '/posts') return respond(paged(post?.status === 'PUBLISHED' ? [post] : [], url))
      if (path === '/posts/21' && method === 'GET') return post?.status === 'PUBLISHED' ? respond(post) : respond({ status: 404, detail: '帖子已隐藏' }, 404)
      if (path === '/posts/21/review-requests') { assert.equal(req.postDataJSON().version, post.version); review = { id: 'review-1', postId: post.id, contentVersion: post.version, snapshot: structuredClone(post), status: 'PENDING', reason: null, createdAt: now }; post.status = 'PENDING'; post.version++; return respond(review, 201) }
      if (path === '/admin/post-review-requests') return respond(paged(review?.status === url.searchParams.get('status') ? [review] : [], url))
      if (path === '/admin/post-review-requests/review-1/reviews') { assert.equal(role, 'admin'); const input = req.postDataJSON(); assert.ok(input.reason); review.status = input.decision; review.reason = input.reason; post.status = input.decision === 'APPROVED' ? 'PUBLISHED' : 'REJECTED'; post.version++; post.publishedAt = now; return respond(review, 201) }
      if (path === '/posts/21/likes/me') { if (method !== 'GET') liked = method === 'PUT'; post.likeCount = liked ? 1 : 0; return respond({ liked, count: post.likeCount }) }
      if (path === '/posts/21/comments') {
        if (method === 'POST') { assert.match(req.headers()['idempotency-key'], /^[a-z0-9-]{36}$/); const c = { id: '31', postId: '21', body: req.postDataJSON().body, status: 'PENDING', version: 0, createdAt: now }; comments.push(c); return respond(c, 201) }
        return respond(paged(comments.filter(c => c.status === 'APPROVED'), url))
      }
      if (path === '/admin/post-comments') return respond(paged(comments.filter(c => c.status === url.searchParams.get('status')), url))
      if (path === '/admin/post-comments/31') { assert.equal(role, 'admin'); const input = req.postDataJSON(); assert.equal(input.version, comments[0].version); comments[0].status = input.status; comments[0].version++; post.commentCount = comments.filter(c => c.status === 'APPROVED').length; return respond(comments[0]) }
      if (path === '/posts/21/reports') { report = { id: '41', postId: '21', reason: req.postDataJSON().reason, status: 'OPEN', version: 0, resolution: null }; return respond(report, 201) }
      if (path === '/admin/post-reports') return respond(paged(report?.status === url.searchParams.get('status') ? [report] : [], url))
      if (path === '/admin/post-reports/41') { assert.equal(role, 'admin'); const input = req.postDataJSON(); assert.equal(input.version, report.version); report.status = input.status; report.resolution = input.reason; report.version++; return respond(report) }
      if (path === '/admin/posts/21') { assert.equal(role, 'admin'); assert.equal(req.postDataJSON().version, post.version); post.status = 'HIDDEN'; post.reviewReason = req.postDataJSON().reason; post.version++; return respond(post) }
      if (path === '/search-capabilities') return respond({ enabled: true, backend: 'MYSQL', elasticsearchConfigured: false, mapSdkAvailable: false, coordinateSystem: 'WGS84', maxWindow: 1000, maxRadiusMeters: 20000 })
      if (path === '/search-results') { const items = post?.status === 'PUBLISHED' ? [{ id: post.id, resourceType: 'POST', schoolId: '1', merchantId: '11', title: post.title, excerpt: xss, titleSegments: [{ text: post.title, matched: true }], excerptSegments: [{ text: xss, matched: true }] }] : []; return respond({ backend: 'MYSQL', ...paged(items, url), windowLimited: false }) }
      if (path === '/nearby-merchants') { nearbyCalls++; assert.equal(url.searchParams.get('coordinateSystem'), 'WGS84'); assert.ok(Math.abs(Number(url.searchParams.get('longitude')) - 116.4) < 0.00001); return respond(paged([{ id: '11', name: '校园面馆', category: 'DINING', address: '东门', longitude: 116.4, latitude: 39.9, coordinateSystem: 'WGS84', distanceMeters: 0 }, { id: '12', name: '校园茶铺', category: 'DINING', address: '南门', longitude: 116.41, latitude: 39.9, coordinateSystem: 'WGS84', distanceMeters: 852 }], url)) }
      if (path === '/merchants/11/location') { if (method === 'PUT') { const input = req.postDataJSON(); assert.equal(input.coordinateSystem, 'WGS84'); assert.equal(input.version, location?.version ?? 0); location = { ...input, merchantId: '11', version: (location?.version ?? 0) + 1 }; return respond(location) }; return location ? respond(location) : respond({ status: 404, code: 'LOCATION_NOT_SET', detail: '尚未设置位置' }, 404) }
      if (path === '/admin/search-index-rebuilds') { if (method === 'POST') { assert.equal(role, 'admin'); jobs.push({ id: 'job-1', status: 'PENDING', processed: 0, cursorType: '', cursorId: '0', errorCode: null, createdAt: now, completedAt: null }); return respond(jobs[0], 201) }; return respond(paged(jobs, url)) }
      apiErrors.push(`${method} ${path}`); return respond({ status: 404, detail: 'Unexpected test request' }, 404)
    } catch (e) { apiErrors.push(e.message); return route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ status: 500, detail: e.message }) }) }
  })
  const visit = path => page.goto(origin + path)
  await visit('/account/posts'); await page.getByLabel('关联门店（须属于所选校园）').selectOption('11'); await page.getByLabel('帖子标题').fill('校园面馆体验'); await page.getByLabel('探店体验').fill(xss)
  assert.equal(await page.getByLabel('上传 PNG/JPEG 图片').isDisabled(), true)
  await page.getByRole('button', { name: '保存帖子草稿' }).click(); await page.getByText('草稿已保存，提交审核通过后才会公开。').waitFor()
  await page.getByRole('button', { name: '提交帖子审核' }).click(); await page.getByText(/已提交审核/).waitFor()
  role = 'admin'; await visit('/admin/community'); await page.getByRole('button', { name: '选择审核此帖' }).click(); await page.getByLabel('处理原因 / 结论').fill('内容已核验'); await page.getByRole('button', { name: '通过并公开帖子' }).click(); await page.getByText('帖子审核决定已保存。').waitFor()
  role = 'author'; await visit('/community'); await page.getByRole('link', { name: '校园面馆体验', exact: true }).click(); await page.getByRole('button', { name: '点赞', exact: true }).click(); await page.getByRole('button', { name: '取消点赞', exact: true }).waitFor()
  await page.getByLabel('分享评论').fill('面馆评论 <script>window.__communityXss=2</script>'); await page.getByRole('button', { name: '提交评论审核' }).click(); await page.getByText('评论已提交，审核通过后公开。').waitFor()
  await page.getByText('举报不当内容', { exact: true }).click(); await page.getByLabel('举报原因').fill('请核查真实性'); await page.getByRole('button', { name: '提交举报', exact: true }).click(); await page.getByText('举报已登记，我们会核查处理。').waitFor()
  await visit('/account/posts'); await page.getByRole('heading', { name: '本人评论' }).waitFor(); await page.getByText('面馆评论 <script>window.__communityXss=2</script>', { exact: true }).waitFor()
  role = 'admin'; await visit('/admin/community'); await page.getByLabel('处理队列').selectOption('comments'); await page.getByLabel('处理原因 / 结论').fill('评论核验通过'); await page.getByRole('button', { name: '通过评论', exact: true }).click(); await page.getByText('评论处理已保存。').waitFor()
  role = 'author'; await visit('/community/posts/21'); await page.getByText('面馆评论 <script>window.__communityXss=2</script>', { exact: true }).waitFor(); assert.equal(await page.evaluate(() => window.__communityXss), undefined)
  await page.setViewportSize({ width: 390, height: 844 }); assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'community must fit mobile')
  if (process.env.CAMPUS_UI_SCREENSHOT) await page.screenshot({ path: resolve(process.env.CAMPUS_UI_SCREENSHOT), fullPage: true })
  await visit('/search'); await page.getByLabel('关键词', { exact: true }).fill('面馆'); await page.getByRole('button', { name: '搜索', exact: true }).click(); await page.getByRole('link', { name: '查看帖子 →' }).waitFor(); assert.ok(await page.locator('mark').count() > 0); assert.equal(await page.evaluate(() => window.__communityXss), undefined)
  role = 'admin'; await visit('/admin/community'); await page.getByLabel('处理队列').selectOption('reports'); await page.getByRole('button', { name: '查看被举报帖子' }).click(); await page.getByLabel('处理原因 / 结论').fill('隐藏并核查'); await page.getByRole('button', { name: '隐藏违规帖子' }).click(); await page.getByText('帖子已隐藏，公开页面和搜索将停止展示。').waitFor(); await page.getByRole('button', { name: '登记已处理' }).click(); await page.getByText('举报处理结论已保存。').waitFor()
  role = 'author'; await visit('/search'); await page.getByLabel('关键词', { exact: true }).fill('面馆'); await page.getByRole('button', { name: '搜索', exact: true }).click(); await page.getByText('找到 0 条结果', { exact: true }).waitFor()
  await visit('/nearby'); await page.getByRole('button', { name: '使用当前位置寻找商家' }).waitFor(); assert.equal(nearbyCalls, 0); await page.getByRole('button', { name: '使用当前位置寻找商家' }).click(); await page.getByText('直线距离 852 米', { exact: true }).waitFor()
  role = 'owner'; await visit('/merchant/location'); await page.getByLabel('门店 WGS84 经度').fill('116.4000000'); await page.getByLabel('门店 WGS84 纬度').fill('39.9000000'); await page.getByRole('button', { name: '保存门店位置' }).click(); await page.getByText('门店位置已保存。').waitFor()
  role = 'admin'; await visit('/admin/search-index'); await page.getByRole('button', { name: '创建索引重建任务' }).click(); await page.getByText('任务已登记，后台将分批处理，请刷新查看进度。').waitFor(); jobs[0].status = 'COMPLETED'; jobs[0].processed = 3; await page.getByRole('button', { name: '刷新重建进度' }).click(); await page.getByRole('heading', { name: '已完成', exact: true }).waitFor()
  role = 'author'; await visit('/admin/community'); await page.getByRole('heading', { name: '发现校园周边的好优惠' }).waitFor()
  assert.deepEqual(errors, []); assert.deepEqual(apiErrors, [])
  console.log('PASS: draft/review/publication, unavailable image upload, idempotent request key, comments/likes/reports, moderation/hiding, private opinion, safe text highlighting/XSS, opt-in WGS84 location, merchant location, rebuild progress, permission guard and mobile layout. HTTP mocked only in this script.')
} finally { await browser?.close(); await server.close() }
