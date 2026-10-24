// Visual regression fixture server and screenshots. Never connects to a database or model.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { createServer, preview } from 'vite'
import vue from '@vitejs/plugin-vue'
import { spawn } from 'node:child_process'
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.CAMPUS_PLAYWRIGHT_MODULE || 'playwright')
const root = fileURLToPath(new URL('..', import.meta.url))
const output = path.resolve(root, process.env.CAMPUS_STYLE_OUTPUT || '../.local/frontend-redesign/after')
await mkdir(output, { recursive: true })
const built = process.env.CAMPUS_UI_BUILD === 'true'
const inspecting = process.env.CAMPUS_STYLE_INSPECT === 'true'
const now = new Date(), expires = new Date(now.getTime() + 86400000 * 7).toISOString(), started = new Date(now.getTime() - 86400000).toISOString()
const coupons = [
  ['101', '下课来碗热汤面', '东门小馆', 30, 6, 42, false],
  ['102', '给午后加一点咖啡香', '半杯咖啡', 25, 5, 18, true],
  ['103', '晚饭一起，双人更划算', '同学食堂', 60, 12, 67, false],
  ['104', '周末球场见', '校园运动站', 50, 10, 24, true],
  ['105', '打印装订，交稿前的最后一站', '学友文印', 20, 4, 80, false],
  ['106', '刚出炉的面包，留给下课的你', '麦田烘焙', 35, 7, 36, false],
].map(([id, title, merchantName, threshold, amountOff, remainingStock, campusOnly], i) => ({
  id, title, merchantName, merchantId: String(200+i), schoolId: '1', couponType: 'AMOUNT_OFF', rules: { threshold, amountOff, discountRate: null },
  remainingStock, campusOnly, description: '到店消费前出示优惠券，使用条件以活动规则为准。', validFrom: started, validUntil: expires,
}))
const permissions = ['merchant:review','campus:review','coupon:review','community:moderate','search:manage','audit:read','rbac:manage','analytics:read']
const profile = portal => ({ id: portal === 'ADMIN' ? '3' : portal === 'MERCHANT' ? '2' : '1', accountType: portal, homePath: { USER:'/app', MERCHANT:'/merchant', ADMIN:'/admin' }[portal], nickname: portal === 'ADMIN' ? '校园运营' : portal === 'MERCHANT' ? '东门小馆' : '林同学', maskedPhone:'138****0000', schoolId:'1', schoolName:'知行大学', campusVerificationStatus:'APPROVED', permissions: portal === 'ADMIN' ? permissions : portal === 'MERCHANT' ? ['merchant:read','merchant:analytics:read'] : [] })
const store = { id:'201', name:'东门小馆', schoolId:'1', category:'DINING', address:'校园东门生活街', status:'APPROVED', version:0, description:'一碗热面，一顿踏实的午饭。', businessHours:'10:00—21:00', role:'OWNER' }
const paged = items => ({ items, total:items.length, page:1, size:20 })
async function fixture(request, portal) {
  const url = new URL(request.url()), p = url.pathname.replace('/api/v1','')
  if (p === '/session-refreshes') return { sessionId:'fixture', tokenType:'Bearer', accessToken:'fixture', refreshToken:'fixture-refresh', accessExpiresAt:expires, refreshExpiresAt:expires }
  if (p === '/users/me') return profile(portal)
  if (p === '/assistant/capabilities') return { enabled:false, maxMessageLength:2000 }
  if (p === '/authentication-capabilities') return { enabled:true, smsAvailable:false, smsSimulated:false, codeLength:6, resendAfterSeconds:60, passwordMinLength:10, passwordMaxBytes:72 }
  if (p === '/schools') return [{ id:'1',name:'知行大学' }, { id:'2',name:'青禾学院' }]
  if (p === '/coupons') { const q=url.searchParams.get('keyword') || ''; return paged(coupons.filter(c => (c.title+c.merchantName).includes(q))) }
  if (p === '/users/me/merchants') return paged([store])
  if (p === '/merchants/201/workspace') return store
  if (p === '/merchants/201/members') return paged([{ userId:'2',nickname:'东门小馆',role:'OWNER',userStatus:'ACTIVE',createdAt:started }])
  if (p === '/notification-capabilities') return { enabled:false }
  if (p === '/users/me/notifications') return paged([])
  if (p === '/users/me/coupon-claims') return paged(coupons.slice(0,3).map((c,i) => ({ ...c,id:String(400+i),couponId:c.id,status:'AVAILABLE',claimedAt:started,redeemedAt:null,redemptionId:null })))
  if (p === '/posts') return paged([{ id:'501', title:'东门这碗热汤面，值得下课绕个弯', body:'从东门走过去，点一碗热汤面。记得提前看看优惠规则，领券后到店出示就好。', likeCount:7,commentCount:2,imageIds:[] }, { id:'502',title:'自习结束，去校园周边走一走',body:'把这一周的小事装进周末：和同学喝杯咖啡，再去球场活动一下。',likeCount:4,commentCount:1,imageIds:[] }])
  if (p === '/posts/503') return { id:'503',title:'山麓小馆的馥郁茶香',body:'探店手记的标题包含不在静态字形子集里的汉字，仍应使用清晰统一的正文无衬线字体。',likeCount:1,commentCount:0,imageIds:[] }
  if (p === '/posts/503/comments') return paged([])
  if (p === '/posts/503/likes/me') return { liked:false,count:1 }
  if (p === '/search-results') return { backend:'MYSQL',items:[],total:0,page:1,size:20,windowLimited:false }
  if (p === '/storage-capabilities') return { enabled:false,available:false,maxBytes:10485760,allowedTypes:[],uploadTtlSeconds:60,downloadTtlSeconds:60 }
  if (p === '/uploads') return paged([])
  if (p === '/map-capabilities') return { configured:false }
  if (p === '/trade-offers') return paged([{ id:'601',merchantId:'201',title:'热汤面与小食双人套餐',description:'和同学吃顿踏实的饭，一单一份套餐。',kind:'GROUP',price:'39.80',stockAvailable:20,stockTotal:30,startsAt:started,endsAt:expires,groupSize:2,groupDurationSeconds:1800,status:'PUBLISHED',version:0 }])
  if (p === '/trade-capabilities') return { enabled:true,paymentAvailable:false,paymentEnvironment:'ALIPAY_SANDBOX' }
  throw new Error('Unmocked visual fixture: '+p)
}
const fixturePlugin = { name:'visual-fixture-api', configureServer(server) {
  if (!inspecting) return
  server.middlewares.use(async (req,res,next) => {
    if (!req.url?.startsWith('/api/v1/')) return next()
    try { const result=await fixture({url:()=> 'http://fixture'+req.url},'USER'); res.setHeader('Content-Type','application/json');res.end(JSON.stringify(result)) }
    catch { res.statusCode=404;res.end('{}') }
  })
} }
// Keep this fixture configuration separate from the original business checks.
const server = built ? await preview({ root, configFile:false, preview:{host:'127.0.0.1',port:0} }) : await createServer({root,configFile:false,cacheDir:path.resolve(root,'../.local/vite-style-cache'),plugins:[vue(),fixturePlugin],server:{host:'127.0.0.1',port:0}})
let browser
try {
  if (!built) await server.listen()
  const origin = 'http://127.0.0.1:'+server.httpServer.address().port
  if (inspecting) {
    const inspector = path.resolve(root,'../.local/design-skills/taste/scripts/inspect.mjs')
    const child = spawn(process.execPath,[inspector,origin+'/app','--app','--out',output],{stdio:'inherit',env:process.env,cwd:path.resolve(root,'..')})
    process.exitCode = await new Promise((resolve,reject) => { child.on('error',reject);child.on('exit',code=>resolve(code ?? 1)) })
  } else {
  browser = await chromium.launch({headless:true,executablePath:process.env.CAMPUS_BROWSER_EXECUTABLE})
  const errors=[], measurements=[]
  async function checkFooter(page, width, capture = false) {
    const footer = page.locator('.campus-footer')
    await footer.scrollIntoViewIfNeeded()
    await footer.locator('img').evaluateAll(imgs => Promise.all(imgs.map(img => img.decode())))
    const state = await footer.evaluate(el => {
      const img=el.querySelector('.campus-footer-art'), ground=el.querySelector('.campus-footer-landscape')
      const art=img.getBoundingClientRect(), scene=el.querySelector('.campus-footer-scene').getBoundingClientRect()
      return { loaded:[img,ground].every(i=>i.complete && i.naturalWidth>0),separate:img.currentSrc!==ground.currentSrc,cover:getComputedStyle(ground).objectFit,ratio:art.width/art.height,naturalRatio:img.naturalWidth/img.naturalHeight,opacity:Number(getComputedStyle(img).opacity),landscapeOpacity:Number(getComputedStyle(ground).opacity),blend:getComputedStyle(el.querySelector('.campus-footer-scene'),'::after').backgroundImage,groundWidth:ground.getBoundingClientRect().width,groundHeight:ground.getBoundingClientRect().height,left:art.left,right:art.right,top:art.top,bottom:art.bottom,sceneTop:scene.top,sceneBottom:scene.bottom,footerHeight:el.getBoundingClientRect().height,contentHeight:el.querySelector('.site-footer').getBoundingClientRect().height }
    })
    assert.ok(state.loaded,'Footer illustration loads')
    assert.ok(state.separate,'Landscape and people must use independent images')
    assert.equal(state.cover,'cover','Landscape fills the footer instead of shrinking into the middle')
    assert.ok(state.opacity>0 && state.opacity<=.35,'People remain a quiet footer background layer')
    assert.equal(state.landscapeOpacity,.13,'Use the user-selected 13% landscape opacity')
    assert.equal(state.opacity,.24,'Preserve the people layer at 24%')
    assert.ok(state.blend.startsWith('linear-gradient'),'Blend the background into the light main surface')
    assert.ok(Math.abs(state.groundHeight-state.footerHeight)<1,'Landscape covers the entire footer height')
    assert.ok(Math.abs(state.footerHeight-state.contentHeight)<1,'Background artwork adds no separate footer band')
    assert.ok(Math.abs(state.ratio-state.naturalRatio)<.01,'Keep the original people proportions')
    assert.ok(Math.abs(state.groundWidth-width)<1,'Only the ground stretches across the screen')
    assert.ok(state.left>=0 && state.right<=width && state.top>=state.sceneTop && state.bottom<=state.sceneBottom,'Keep the entire illustration visible')
    assert.equal(await footer.getByRole('link').count(),3,'Preserve the three footer destinations')
    if (capture) await footer.screenshot({path:path.join(output,'footer-'+width+'.png')})
    await page.evaluate(()=>scrollTo(0,0))
  }
  for (const [portal, route, name] of [['USER','/app','coupons'],['USER','/login','login'],['USER','/community','community'],['USER','/community/posts/503','post-detail'],['USER','/trade','trade'],['USER','/account/coupons','wallet'],['MERCHANT','/merchant','merchant'],['ADMIN','/admin','admin']]) {
    const context = await browser.newContext()
    if (route !== '/login') await context.addInitScript(() => sessionStorage.setItem('campus.refresh-token','fixture-refresh'))
    await context.route('**/api/v1/**', async r => {
      try { await r.fulfill({contentType:'application/json',body:JSON.stringify(await fixture(r.request(),portal))}) }
      catch (error) { errors.push(error.message); await r.fulfill({status:500,body:'{}'}) }
    })
    const page=await context.newPage()
    page.on('pageerror', e=>errors.push(e.message))
    for (const width of [1440,768,390]) {
      await page.setViewportSize({width,height:960})
      await page.goto(origin+route); await page.locator('h1').waitFor()
      if (route === '/app') await page.getByRole('heading',{name:'下课来碗热汤面',exact:true}).waitFor()
      if (route === '/merchant') await page.getByLabel('营业时间', { exact: true }).waitFor()
      if (route === '/community') await page.getByRole('heading',{ name:'东门这碗热汤面，值得下课绕个弯', exact:true }).waitFor()
      if (name === 'post-detail') await page.getByRole('heading',{name:'山麓小馆的馥郁茶香',exact:true}).waitFor()
      if (route === '/trade') await page.getByRole('heading',{ name:'热汤面与小食双人套餐', exact:true }).waitFor()
      if (route === '/account/coupons') await page.getByRole('heading',{ name:'下课来碗热汤面', exact:true }).waitFor()
      if (portal === 'USER') await checkFooter(page,width,route==='/app')
      await page.evaluate(() => document.fonts.ready)
      if (portal === 'USER') {
        assert.ok(await page.evaluate(()=>[...document.fonts].some(f=>f.family.includes('Campus Notice Round') && f.status==='loaded')),'Self-hosted rounded headline font must load')
        assert.ok(!(await page.evaluate(()=>getComputedStyle(document.body).fontFamily)).includes('Campus Notice Round'),'Body copy keeps its readable sans-serif face')
      }
      if (route === '/app' && width === 1440) {
        const cdp=await context.newCDPSession(page)
        await cdp.send('DOM.enable'); await cdp.send('CSS.enable')
        const {root:documentRoot}=await cdp.send('DOM.getDocument')
        const evidence={}
        for (const [name,selector] of [['heading','.discovery-copy h1'],['body','.ticket-rule']]) {
          const {nodeId}=await cdp.send('DOM.querySelector',{nodeId:documentRoot.nodeId,selector})
          evidence[name]=(await cdp.send('CSS.getPlatformFontsForNode',{nodeId})).fonts
        }
        await writeFile(path.join(output,'font-evidence.json'),JSON.stringify(evidence,null,2))
        await cdp.detach()
      }
      if (name === 'post-detail') {
        const family=await page.locator('h1').evaluate(el=>getComputedStyle(el).fontFamily)
        assert.ok(!family.includes('Campus Notice Round'),'Authored titles must not use a static decorative subset')
        const cdp=await context.newCDPSession(page)
        await cdp.send('DOM.enable'); await cdp.send('CSS.enable')
        const {root:documentRoot}=await cdp.send('DOM.getDocument')
        const {nodeId}=await cdp.send('DOM.querySelector',{nodeId:documentRoot.nodeId,selector:'h1'})
        const evidence=await cdp.send('CSS.getPlatformFontsForNode',{nodeId})
        assert.ok(evidence.fonts.every(f=>!f.isCustomFont),'Outside-subset title characters stay on native sans')
        await writeFile(path.join(output,'post-font-'+width+'.json'),JSON.stringify({family,...evidence},null,2))
        await cdp.detach()
      }
      await page.evaluate(() => Promise.all([...document.images].map(img => img.decode().catch(() => {}))))
      await page.evaluate(async () => {
        const finite = document.getAnimations().filter(a => Number.isFinite(a.effect?.getTiming().iterations ?? 1))
        await Promise.allSettled(finite.map(a => a.finished))
      })
      const stray = await page.evaluate(() => {
        const nodes = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT)
        let count = 0, node
        while ((node = nodes.nextNode())) if (node.textContent.trim() === '+') count++
        return count
      })
      assert.equal(stray,0,'No patch markers may appear as rendered content')
      await page.screenshot({path:path.join(output,name+'-'+width+'.png'),fullPage:true})
      if (route === '/app') await page.screenshot({path:path.join(output,'coupons-fold-'+width+'.png')})
      const state=await page.evaluate(() => ({width:innerWidth,scrollWidth:document.documentElement.scrollWidth,h1:document.querySelector('h1')?.textContent,articles:document.querySelectorAll('article').length}))
      measurements.push({portal,route,...state})
      assert.ok(state.scrollWidth <= width, route+' overflows at '+width)
    }
    if (route === '/app') {
      for (const width of [320,1920,2560]) {
        await page.setViewportSize({width,height:960})
        await checkFooter(page,width,true)
        assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'No footer overflow at '+width)
      }
      await page.setViewportSize({width:390,height:960})
      let releaseClaim, receiveClaim, claimFailure = false
      await page.route('**/api/v1/coupons/101/claims', async r => {
        await new Promise(resolve => { releaseClaim = resolve; receiveClaim() })
        await r.fulfill({ status:claimFailure ? 409 : 201,contentType:'application/json',body:JSON.stringify(claimFailure ? { status:409,detail:'测试领取失败，请重试' } : { id:'401',couponId:'101' }) })
      })
      const ticket = page.locator('.coupon-ticket').first()
      const initialHeight = (await ticket.boundingBox()).height
      const submitClaim = async () => {
        const received = new Promise(resolve=>{ receiveClaim=resolve })
        await ticket.getByRole('button',{name:'领取优惠',exact:true}).click()
        await received
      }
      await submitClaim()
      assert.equal(await page.locator('.ticket-tear').count(),0,'No success animation before server acknowledgement')
      assert.equal(await ticket.getByRole('button').isDisabled(),true)
      assert.ok(releaseClaim)
      releaseClaim()
      await ticket.locator('.ticket-tear').waitFor()
      await page.screenshot({path:path.join(output,'coupon-tear-390.png')})
      assert.equal((await ticket.boundingBox()).height,initialHeight,'Tearing must not shift the coupon layout')
      await page.getByText('优惠券已放入我的券包，可前往查看并到店使用。',{exact:true}).waitFor()
      await ticket.locator('.ticket-tear').waitFor({state:'detached'})
      claimFailure = true; releaseClaim = undefined
      await submitClaim()
      await page.waitForFunction(() => document.querySelector('.coupon-ticket button')?.disabled)
      releaseClaim()
      await page.getByText('测试领取失败，请重试',{exact:true}).waitFor()
      assert.equal(await page.locator('.ticket-tear').count(),0,'A failed claim must never tear a ticket')
      await page.emulateMedia({ reducedMotion:'reduce' })
      claimFailure=false; releaseClaim=undefined
      await submitClaim()
      releaseClaim()
      await page.getByText('优惠券已放入我的券包，可前往查看并到店使用。',{exact:true}).waitFor()
      assert.equal(await page.locator('.ticket-tear').count(),0,'Reduced motion uses static confirmation')
      await page.getByRole('button',{name:'给课间，加点咖啡香 找找咖啡优惠'}).click()
      await page.getByRole('heading',{name:'给午后加一点咖啡香',exact:true}).waitFor()
      await page.waitForFunction(() => document.querySelectorAll('.coupon-ticket').length === 1)
      assert.equal(await page.locator('.coupon-ticket').count(),1,'Discovery note must query actual coupon data')
      assert.equal(await page.getByLabel('优惠关键词',{exact:true}).inputValue(),'咖啡')
      await page.getByRole('button',{name:'清除关键词',exact:true}).click()
      await page.getByRole('heading',{name:'下课来碗热汤面',exact:true}).waitFor()
      await page.getByRole('button', { name:'校园专享', exact:true }).click()
      assert.equal(await page.locator('.coupon-ticket').count(),2)
      await page.getByRole('button', { name:'折扣优惠', exact:true }).click()
      await page.getByText('当前页没有这类优惠，试试其他类型或翻到下一页。', { exact:true }).waitFor()
      await page.getByRole('button', { name:'全部优惠', exact:true }).click()
      await page.getByRole('button', { name:'切换用户端菜单' }).click()
      assert.equal(await page.getByRole('link',{name:'我的订单',exact:true}).isVisible(),true)
      await page.emulateMedia({ reducedMotion:'reduce' })
      assert.ok(await page.evaluate(() => matchMedia('(prefers-reduced-motion: reduce)').matches))
      await page.setViewportSize({width:1440,height:960})
      await page.getByLabel('站内搜索',{exact:true}).fill('咖啡')
      await page.getByRole('button',{name:'提交站内搜索',exact:true}).click()
      await page.waitForURL('**/search?q=*')
      await page.getByLabel('关键词',{exact:true}).waitFor()
      assert.equal(await page.getByLabel('关键词',{exact:true}).inputValue(),'咖啡')
    }
    if (portal !== 'USER') {
      await page.getByRole('button', { name:'展开菜单',exact:true }).click()
      assert.equal(await page.getByRole('navigation', {name: portal === 'ADMIN' ? '管理端导航' : '商家端导航'}).isVisible(),true)
    }
    await context.close()
  }
  assert.deepEqual(errors,[])
  await writeFile(path.join(output,'measurements.json'),JSON.stringify(measurements,null,2))
  console.log('PASS: eight real routes at 1440/768/390px, six footer widths, native authored titles and loaded rounded static headings; explicit fixtures only, no overflow/browser errors.')
  }
} finally { await browser?.close(); if(built) await new Promise(resolve=>server.httpServer.close(resolve)); else await server.close() }
