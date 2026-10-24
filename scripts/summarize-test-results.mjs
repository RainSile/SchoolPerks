// Export only suite metadata; never export XML properties, environment, stack traces or logs.
import { readdir, readFile, writeFile, mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
const root = fileURLToPath(new URL('..', import.meta.url)), backend = path.join(root, 'backend')
const suites = []
for (const module of await readdir(backend, { withFileTypes: true })) {
  if (!module.isDirectory() || !module.name.startsWith('campus-')) continue
  const folder = path.join(backend, module.name, 'target', 'surefire-reports')
  let files
  try { files = await readdir(folder) } catch (error) { if (error.code === 'ENOENT') continue; throw error }
  for (const file of files.filter(f => /^TEST-.*\.xml$/.test(f))) {
    const xml = await readFile(path.join(folder, file), 'utf8')
    const opening = xml.match(/<testsuite\s[^>]*>/)?.[0]
    if (!opening) throw new Error('Missing suite metadata: ' + file)
    const attrs = Object.fromEntries([...opening.matchAll(/([A-Za-z]+)="([^"]*)"/g)].map(m => [m[1], m[2]]))
    const record = { module: module.name, name: attrs.name, tests: +attrs.tests, failures: +attrs.failures, errors: +attrs.errors, skipped: +attrs.skipped, suiteSeconds: +attrs.time }
    if (!record.name || [record.tests, record.failures, record.errors, record.skipped, record.suiteSeconds].some(n => !Number.isFinite(n))) throw new Error('Invalid suite metadata: ' + file)
    suites.push(record)
  }
}
if (!suites.length) throw new Error('Run Maven verify first')
suites.sort((a,b) => a.name.localeCompare(b.name))
const totals = suites.reduce((t,s) => ({ tests: t.tests+s.tests, passed: t.passed+s.tests-s.failures-s.errors-s.skipped, failures: t.failures+s.failures, errors: t.errors+s.errors, skipped: t.skipped+s.skipped }), { tests: 0, passed: 0, failures: 0, errors: 0, skipped: 0 })
const result = { generatedAt: new Date().toISOString(), note: 'Local Surefire functional-test metadata. Not request latency, throughput, load-test evidence or provider availability. Remove stale target reports before comparing different selections.', totals, suites }
await mkdir(path.join(root, '.local'), { recursive: true })
await writeFile(path.join(root, '.local', 'stage7-test-summary.json'), JSON.stringify(result,null,2)+'\n')
console.log(JSON.stringify(totals))
if (totals.failures || totals.errors) process.exitCode = 1
