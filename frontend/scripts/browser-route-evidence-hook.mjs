// Optional Node preload for test-only navigation evidence. Uses public Playwright methods/events;
// never observes page fields, tokens, request bodies or raw dynamic resource identifiers.
import { createRequire } from 'node:module'
import fs from 'node:fs'
import path from 'node:path'
const directory = process.env.CAMPUS_UI_ROUTE_EVIDENCE_DIR
if (!directory) throw new Error('Supply a route evidence directory')
const routes = JSON.parse(fs.readFileSync(path.join(directory, 'frontend-routes.json'), 'utf8'))
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.CAMPUS_PLAYWRIGHT_MODULE || 'playwright')
const originalLaunch = chromium.launch.bind(chromium)
const pages = new WeakSet(), contexts = new WeakSet()
const escape = text => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
const patterns = routes.filter(route => !route.path.includes('pathMatch')).map(route => ({ route: route.path, pattern: new RegExp('^' + route.path.split('/').map(segment => segment.startsWith(':') ? '[^/]+' : escape(segment)).join('/') + '$') }))
function trackPage(page) {
  if (pages.has(page)) return
  pages.add(page)
  page.on('framenavigated', frame => {
    if (frame !== page.mainFrame()) return
    let url
    try { url = new URL(frame.url()) } catch { return }
    if (!['127.0.0.1', 'localhost'].includes(url.hostname)) return
    const route = patterns.find(item => item.route === url.pathname) || patterns.find(item => item.pattern.test(url.pathname)) || routes.find(item => item.path.includes('pathMatch')) && { route: routes.find(item => item.path.includes('pathMatch')).path }
    const size = page.viewportSize()
    fs.appendFileSync(path.join(directory, 'browser-routes.tsv'), `${process.env.CAMPUS_UI_EVIDENCE_SUITE}\t${route?.route || 'UNMAPPED_FRONTEND'}\t${size?.width || 0}\t${size?.height || 0}\n`)
  })
}
function trackContext(context) {
  if (contexts.has(context)) return
  contexts.add(context)
  context.on('page', trackPage)
  for (const page of context.pages()) trackPage(page)
}
chromium.launch = async (...arguments_) => {
  const browser = await originalLaunch(...arguments_)
  const originalContext = browser.newContext.bind(browser), originalPage = browser.newPage.bind(browser)
  browser.newContext = async (...values) => { const context = await originalContext(...values); trackContext(context); return context }
  browser.newPage = async (...values) => { const page = await originalPage(...values); trackContext(page.context()); trackPage(page); return page }
  return browser
}
