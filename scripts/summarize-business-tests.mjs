// Sanitized evidence export. Never copies credentials, XML properties, request bodies or raw logs.
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { routeInventory } from '../frontend/scripts/route-inventory.mjs'

const root = fileURLToPath(new URL('..', import.meta.url))
const phase = process.argv[2]
if (phase && !/^[a-z0-9-]+$/.test(phase)) throw new Error('Invalid evidence phase')
const evidence = path.join(root, '.local/business-tests', phase || '')
const minimumReportTime = phase ? (await fs.stat(path.join(evidence, 'database-before.tsv'))).mtimeMs : 0
const reportDate = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())
const selected = new Set(['AlipaySandboxAdapterTest', 'ElasticsearchSearchAdapterTest', 'SmsConfigurationTest', 'JwtValidationTest', 'RedemptionQrTest', 'ScaffoldContractTest', 'MySqlReadTest', 'AuthenticationMySqlTest', 'PasswordAuthenticationMySqlTest', 'RbacIsolationMySqlTest', 'MerchantStageTwoMySqlTest', 'CouponStageThreeMySqlTest', 'TradeStageFourMySqlTest', 'CommunityStageFiveMySqlTest', 'AnalyticsStageSevenMySqlTest', 'BusinessEndpointInventoryTest', 'BusinessReadChainsMySqlTest', 'BusinessExternalServicesTest', 'BusinessLiveJourneyMySqlTest', 'BusinessLiveMapMySqlTest'])
const attributes = text => Object.fromEntries([...text.matchAll(/([A-Za-z]+)="([^"]*)"/g)].map(match => [match[1], match[2]]))
const suites = []
selected.add('CurrentActorBusinessPermissionTest')
for (const module of await fs.readdir(path.join(root, 'backend'), { withFileTypes: true })) {
  if (!module.isDirectory() || !module.name.startsWith('campus-')) continue
  const archived = path.join(evidence,'junit',module.name)
  const folder = await fs.stat(archived).then(()=>archived).catch(()=>path.join(root, 'backend', module.name, 'target/surefire-reports'))
  let files
  try { files = await fs.readdir(folder) } catch (error) { if (error.code === 'ENOENT') continue; throw error }
  for (const file of files.filter(name => /^TEST-.*\.xml$/.test(name))) {
    if ((await fs.stat(path.join(folder, file))).mtimeMs < minimumReportTime) continue
    if (!selected.has(file.slice(5, -4).split('.').at(-1))) continue
    const xml = await fs.readFile(path.join(folder, file), 'utf8')
    const header = attributes(xml.match(/<testsuite\b[^>]*>/)?.[0] || '')
    const cases = [...xml.matchAll(/<testcase\b([^>]*?)(?:\/>|>([\s\S]*?)<\/testcase>)/g)].map(match => ({ name: attributes(match[1]).name, status: /<failure\b/.test(match[2] || '') ? 'FAIL' : /<error\b/.test(match[2] || '') ? 'ERROR' : /<skipped\b/.test(match[2] || '') ? 'SKIP' : 'PASS' }))
    suites.push({ module: module.name, name: header.name, tests: +header.tests, failures: +header.failures, errors: +header.errors, skipped: +header.skipped, cases })
  }
}
suites.sort((a, b) => a.name.localeCompare(b.name))
const totals = suites.reduce((sum, suite) => ({ tests: sum.tests + suite.tests, passed: sum.passed + suite.tests - suite.failures - suite.errors - suite.skipped, failures: sum.failures + suite.failures, errors: sum.errors + suite.errors, skipped: sum.skipped + suite.skipped }), { tests: 0, passed: 0, failures: 0, errors: 0, skipped: 0 })
const extraPhases=process.argv.slice(3).map(value=>{if(!/^[a-z0-9-]+$/.test(value))throw new Error('Invalid extra phase');return path.join(root,'.local/business-tests',value)})
const phaseFolders = [evidence, ...extraPhases, ...(await fs.readdir(evidence, { withFileTypes: true })).filter(item => item.isDirectory()).map(item => path.join(evidence, item.name))]
const phases = [], requests = []
for (const folder of phaseFolders) {
  let log
  try { log = await fs.readFile(path.join(folder, 'backend.log'), 'utf8') } catch (error) { if (error.code === 'ENOENT') continue; throw error }
  const classes = [...log.matchAll(/Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+), Time elapsed: ([\d.]+) s(?: <<< FAILURE!)? -- in ([\w.$]+)/g)].map(match => ({ name: match[6], tests: +match[1], failures: +match[2], errors: +match[3], skipped: +match[4] }))
  let dataIsolationMatchesBaseline = null
  try {
    const before = await fs.readFile(path.join(folder, 'database-before.tsv'), 'utf8')
    const after = await fs.readFile(path.join(folder, 'database-after.tsv'), 'utf8')
    dataIsolationMatchesBaseline = before.trim() === after.trim()
  } catch (error) { if (error.code !== 'ENOENT') throw error }
  const mavenSuccessful = log.includes('BUILD SUCCESS')
  const record = { name: folder === evidence ? 'baseline' : path.basename(folder), mavenSuccessful, dataIsolationMatchesBaseline, successful: mavenSuccessful && dataIsolationMatchesBaseline !== false, classes }
  phases.push(record)
  try {
    const text = await fs.readFile(path.join(folder, 'backend-requests.tsv'), 'utf8')
    requests.push(...text.trim().split(/\r?\n/).filter(Boolean).map(line => { const [method, route, status, handled, test] = line.split('\t'); return { method, route, status: +status, handled: handled === 'true', test, phase: record.name } }))
  } catch (error) { if (error.code !== 'ENOENT') throw error }
}
const inventory = JSON.parse(await fs.readFile(path.join(evidence, 'api-inventory.json'), 'utf8'))
const business = inventory.filter(item => item.scope === 'BUSINESS')
const operations = business.map(operation => {
  const observed = requests.filter(request => request.method === operation.method && request.route === operation.path)
  return { ...operation, observed: observed.length, statuses: [...new Set(observed.map(request => request.status))].sort((a, b) => a - b), successfulHandler: observed.some(request => request.handled && request.status >= 200 && request.status < 300), tests: [...new Set(observed.filter(request => request.test !== 'fixture-or-background').map(request => request.test))] }
})
const browser = JSON.parse(await fs.readFile(path.join(evidence, 'browser-results.json'), 'utf8'))
const result = { generatedAt: new Date().toISOString(), totals, suites, phases, browser, operations: { registered: inventory.length, business: operations.length, excludedAi: inventory.length - operations.length, observed: operations.filter(operation => operation.observed).length, normalHandlerObserved: operations.filter(operation => operation.successfulHandler).length, requestsObserved: requests.length }, exhaustiveBranchCoverageClaimed: false }
await fs.writeFile(path.join(evidence, 'summary.json'), JSON.stringify(result, null, 2))
await fs.writeFile(path.join(evidence, 'api-coverage.json'), JSON.stringify(operations, null, 2))

const escape = text => String(text).replaceAll('|', '\\|').replaceAll('\n', ' ')
let markdown = '# 业务接口逐项覆盖记录\n\n'
markdown += '日期：2026-10-25。自动来源：实际 Spring 注册映射、仅测试环境的过滤器调用轨迹、明确选定的 JUnit 套件。未记录请求体、令牌、手机号、核销码或供应商密钥。\n\n'
markdown += `已注册 ${inventory.length} 个 HTTP 操作；业务 ${operations.length} 个，按约定排除 AI ${inventory.length - operations.length} 个。观察到业务接口调用 ${result.operations.observed}/${operations.length}，其中正常成功处理分支 ${result.operations.normalHandlerObserved}/${operations.length}。\n\n`
markdown += '**接口命中、2xx 与一个完整功能的所有分支通过不同。本表不证明每个字段边界、所有状态组合、所有并发交错或全部浏览器操作均已穷尽。** 正常/失败/归属/状态机/落库的具体断言见用例索引，真实供应商验收见结果报告。\n\n'
markdown += 'U=单元/协议夹具，D=实际 MySQL+MockMvc，F=浏览器+HTTP/WS夹具，E=真实前后端+MySQL旅程，X=真实外部服务。轨迹中共享组件/异步线程可能没有单个 JUnit 方法标识；其证据保留在对应阶段的私有记录中。\n\n'
markdown += '| 编号 | 方法与资源 | 功能 | 观察到的 HTTP 状态 | 正常处理分支 | JUnit 调用证据（最多三条） |\n| --- | --- | --- | --- | --- | --- |\n'
operations.forEach((operation, index) => {
  const normal = operation.successfulHandler ? '已观察；完整边界见用例' : '未观察正常成功；地图实际加载待验收'
  markdown += `| API-${String(index + 1).padStart(3, '0')} | ${escape(operation.method)} \`${escape(operation.path)}\` | ${escape(operation.summary)} | ${operation.statuses.join(' / ') || '未调用'} | ${normal} | ${escape(operation.tests.slice(0, 3).join('；')) || '见阶段旅程记录'} |\n`
})
markdown += '\n## 按约定排除的接口\n\n'
for (const operation of inventory.filter(item => item.scope !== 'BUSINESS')) markdown += `- ${operation.method} \`${operation.path}\`：${operation.summary}。从本业务表排除，助手实测另见 ASSISTANT-TEST-RESULTS.md。\n`
await fs.writeFile(path.join(root, 'docs/BUSINESS-API-COVERAGE.md'), markdown.replaceAll('2026-10-25', reportDate))

let cases = '# 业务测试用例执行索引\n\n日期：2026-10-25。只收录本轮白名单套件的最新结果，不将 target 中历史 AI 测试报告混入统计。先前新测试的前置条件/断言问题和重跑保留在私有分阶段日志，不能当作产品 bug 或隐藏的首次通过。\n\n'
cases += `最新唯一用例：${totals.tests}，通过 ${totals.passed}，失败 ${totals.failures}，错误 ${totals.errors}，跳过 ${totals.skipped}。该条数不等于业务分支数、接口数或页面覆盖率。\n\n`
for (const suite of suites) {
  const name = suite.name.split('.').at(-1)
  cases += `## ${name}\n\n模块：${suite.module}；${suite.tests} 项，失败 ${suite.failures}，错误 ${suite.errors}，跳过 ${suite.skipped}。\n\n`
  for (const test of suite.cases) cases += `- ${test.status}：\`${test.name}\`。\n`
  cases += '\n'
}
await fs.writeFile(path.join(root, 'docs/BUSINESS-TEST-CASES.md'), cases.replaceAll('2026-10-25', reportDate))

const routes = await routeInventory(root)
await fs.writeFile(path.join(evidence, 'frontend-routes.json'), JSON.stringify(routes, null, 2))
let navigation = []
try {
  navigation = (await fs.readFile(path.join(evidence, 'browser-routes.tsv'), 'utf8')).trim().split(/\r?\n/).filter(Boolean).map(line => { const [suite, route, width, height] = line.split('\t'); return { suite, route, width: +width, height: +height } })
} catch (error) { if (error.code !== 'ENOENT') throw error }
const frontendCoverage = { registered: routes.length, excludedAi: routes.filter(route => route.excluded).length, business: routes.filter(route => !route.excluded).length, observedBusiness: routes.filter(route => !route.excluded && navigation.some(row => row.route === route.path)).length, events: navigation.length }
await fs.writeFile(path.join(evidence, 'frontend-coverage.json'), JSON.stringify(frontendCoverage, null, 2))
let pageMatrix = '# 前端路由逐项清点与验收差额\n\n日期：2026-10-25。S 层源清点：从 TypeScript AST 读取实际路由，包含别名和错误页。**路由清点不代表每个页面、按钮或响应式尺寸已完成实测。** 本轮八组 F 层脚本与两个 E 层旅程的操作证据见测试用例和结果；未被这些证据覆盖的操作保留待补测。\n\n'
pageMatrix += `实际 F 层导航轨迹已观察 ${frontendCoverage.observedBusiness}/${frontendCoverage.business} 条业务/基础路由；另 ${frontendCoverage.excludedAi} 条 AI 营销路由排除。导航经过不等于该页面全部操作通过，角色/表单/状态断言仍以相应脚本为准。\n\n`
pageMatrix += '| 路由 | 组件 / 重定向 | 声明权限 | 实际导航证据（F脚本 / 宽度） |\n| --- | --- | --- | --- |\n'
for (const route of routes) {
  const visits = [...new Set(navigation.filter(row => row.route === route.path).map(row => `${row.suite} / ${row.width}`))]
  pageMatrix += `| \`${route.path}\` | ${escape(route.component || route.redirect || '错误页')} | ${route.permission || '登录/所属端由守卫判定；公开页按路由'} | ${route.excluded ? '按约定排除 AI 营销' : escape(visits.join('；')) || '尚无导航轨迹'} |\n`
}
pageMatrix += '\n后续逐页补测需分别登记：入口/直链、角色、加载/空/错误态、字段约束与提交、重复点击、筛选/分页、键盘、刷新/后退、桌面/手机、对应 REST 响应和 SQL 不变量。不能以页面能打开代替这些维度。\n'
await fs.writeFile(path.join(root, 'docs/BUSINESS-FRONTEND-COVERAGE.md'), pageMatrix.replaceAll('2026-10-25',reportDate).replace('八组 F 层脚本与两个 E 层旅程','F 层脚本与 E 层旅程'))
console.log(JSON.stringify({ totals, operations: result.operations, browserSuites: browser.length, browserPassed: browser.filter(suite => suite.exitCode === 0).length, routes: routes.length }))
if (totals.failures || totals.errors || browser.some(suite => suite.exitCode !== 0)) process.exitCode = 1
