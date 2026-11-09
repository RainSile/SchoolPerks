// E+X: real Vue/HTTP/MySQL and real MinIO. Uses synthetic accounts/material supplied by the test harness.
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'
const { chromium } = createRequire(import.meta.url)(process.env.CAMPUS_PLAYWRIGHT_MODULE || 'playwright')
const root = fileURLToPath(new URL('..', import.meta.url))
const server = await createServer({ root, configFile: false, plugins: [vue()], server: { host: '127.0.0.1', port: 5173, strictPort: true, proxy: { '/api': { target: 'http://127.0.0.1:' + process.env.CAMPUS_MEDIA_BACKEND_PORT, changeOrigin: true } } } })
const image = { name: 'synthetic-pixel.png', mimeType: 'image/png', buffer: Buffer.from(process.env.CAMPUS_MEDIA_PNG_BASE64, 'base64') }
let browser
const errors = [], sessions = [], checks = []
try {
  await server.listen()
  const origin = 'http://127.0.0.1:5173'
  browser = await chromium.launch({ headless: true, executablePath: process.env.CAMPUS_BROWSER_EXECUTABLE })
  async function login(role, home) {
    const context = await browser.newContext({ viewport: { width: 390, height: 844 } })
    const page = await context.newPage(), session = { context, page, token: null }; sessions.push(session)
    page.on('pageerror', error => errors.push(error.name))
    page.on('request', request => {
      const token = request.headers().authorization
      if (request.url().startsWith(origin + '/api/') && token?.startsWith('Bearer ')) session.token = token
      if (!request.url().startsWith(origin) && request.method() === 'PUT') assert.equal(token, undefined, 'Storage direct upload must not receive the platform JWT')
    })
    await page.goto(origin + '/login')
    await page.getByLabel('手机号', { exact: true }).fill(process.env['CAMPUS_MEDIA_' + role + '_PHONE'])
    await page.getByLabel('密码', { exact: true }).fill(process.env.CAMPUS_MEDIA_PASSWORD)
    const [response] = await Promise.all([page.waitForResponse(response => response.url().endsWith('/api/v1/password-sessions')), page.getByRole('button', { name: '登录', exact: true }).click()])
    assert.equal(response.status(), 201, 'Real password-login HTTP status')
    await page.waitForURL(origin + home)
    return page
  }
  const student = await login('USER', '/app')
  await student.goto(origin + '/account')
  await student.getByRole('button', { name: '更换头像', exact: true }).click()
  await student.getByRole('button', { name: '选择暖阳·咖啡', exact: true }).click()
  await student.getByText('头像已更新。', { exact: true }).waitFor()
  await student.locator('.current-avatar img').evaluate(image => image.decode())
  await student.getByRole('button', { name: '更换头像', exact: true }).click()
  await student.waitForFunction(() => document.querySelector('.avatar-file-input')?.disabled === false)
  await student.getByRole('group', { name: '布告栏上的我' }).locator('input[type=file]').setInputFiles(image)
  await student.getByRole('button', { name: '裁好，就用这张', exact: true }).click()
  await student.getByRole('dialog', { name: '换个头像', exact: true }).waitFor({ state: 'hidden' })
  await student.getByText('头像已更新。', { exact: true }).waitFor()
  await student.locator('.current-avatar img').evaluate(image => image.decode())
  checks.push('Default selection and custom avatar upload/binding render actual MinIO bytes')
  await student.goto(origin + '/account/campus-verification')
  const group = student.getByRole('group', { name: '学生证首页照片' })
  assert.equal(await group.locator('input[capture]').getAttribute('capture'), 'environment')
  await student.getByLabel('学校', { exact: true }).selectOption(process.env.CAMPUS_MEDIA_SCHOOL_ID)
  await student.getByLabel('真实姓名', { exact: true }).fill('合成测试同学')
  await student.getByLabel('学号', { exact: true }).fill('SYNTHETIC-MEDIA-2026')
  assert.equal(await student.getByRole('button', { name: '提交认证申请', exact: true }).isDisabled(), true)
  await student.waitForFunction(() => document.querySelector('input[capture]')?.disabled === false)
  await group.locator('input[type=file]').last().setInputFiles(image)
  await student.getByText('图片已上传并完成核验。', { exact: true }).waitFor()
  await student.getByRole('button', { name: '提交认证申请', exact: true }).click()
  await student.getByText('申请已提交，审核结果将在此显示。', { exact: true }).waitFor()
  await student.getByRole('button', { name: '查看已提交的学生证照片', exact: true }).click()
  await student.getByAltText('本人提交的学生证首页').evaluate(image => image.decode())
  checks.push('Mobile camera input / photo upload / verification submission / applicant private preview')
  const admin = await login('ADMIN', '/admin')
  await admin.goto(origin + '/admin/campus-verifications')
  await admin.getByRole('button', { name: '查看认证申请', exact: true }).click()
  await admin.getByRole('button', { name: '查看私有学生证照片', exact: true }).click()
  await admin.getByAltText('仅供本次审核的学生证首页').evaluate(image => image.decode())
  await admin.getByLabel('校园审核原因', { exact: true }).fill('合成图片，仅验证业务链路')
  await admin.getByRole('button', { name: '通过校园认证', exact: true }).click()
  await admin.getByText('校园认证审核已保存。', { exact: true }).waitFor()
  await student.reload()
  await student.locator('.verification-record').filter({ hasText: '已通过' }).waitFor()
  assert.equal(await student.evaluate(() => document.documentElement.scrollWidth > innerWidth), false)
  checks.push('Authorized reviewer sees actual private photo and approves; applicant reload shows approved state')
  assert.deepEqual(errors, [])
  await fs.writeFile(path.join(process.env.CAMPUS_BUSINESS_EVIDENCE_DIR, 'media-browser-result.json'), JSON.stringify({ passed: true, evidence: 'E+X', checks, businessHttpFixtures: false, realMinio: true, smsSends: 0, syntheticStudentCard: true }, null, 2))
  console.log('PASS: actual Vue student-card and avatar journey with actual MinIO; no business HTTP mocks.')
} finally {
  for (const session of sessions) if (session.token) await session.context.request.delete('http://127.0.0.1:5173/api/v1/sessions/current', { headers: { Authorization: session.token } })
  await browser?.close()
  await server.close()
}
