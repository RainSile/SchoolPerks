<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import CampusIcon from '../../campus-shared/CampusIcon.vue'
import CampusOrnament from '../../campus-shared/CampusOrnament.vue'
import { useRoute, useRouter } from 'vue-router'
import { loginDestination } from '../../campus-router'
import { useSessionStore } from '../../campus-stores/session'
import { createChallenge, createSession, passwordLogin, register, resetPassword, getCapabilities, type Capabilities } from './api'
const session = useSessionStore(), route = useRoute(), router = useRouter()
const mode = computed(() => route.path === '/register' ? 'register' : route.path === '/forgot-password' ? 'reset' : 'login')
const smsLogin = ref(false), phone = ref(''), code = ref(''), password = ref(''), confirmation = ref(''), nickname = ref('')
const challengeId = ref(''), expiresAt = ref(''), capabilities = ref<Capabilities | null>(null), message = ref(''), busy = ref(false)
const resendAt = ref(0), now = ref(Date.now())
const needsCode = computed(() => mode.value !== 'login' || smsLogin.value)
const title = computed(() => mode.value === 'register' ? '注册账号' : mode.value === 'reset' ? '找回密码' : '登录')
const countdown = computed(() => Math.max(0, Math.ceil((resendAt.value - now.value) / 1000)))
const validPhone = computed(() => /^1[3-9][0-9]{9}$/.test(phone.value))
const timer = setInterval(() => { now.value = Date.now() }, 1000)
onUnmounted(() => { clearInterval(timer); password.value = ''; confirmation.value = ''; code.value = '' })
watch(phone, () => { challengeId.value = ''; code.value = ''; expiresAt.value = '' })
watch([mode, smsLogin], () => { password.value = ''; confirmation.value = ''; code.value = ''; challengeId.value = ''; expiresAt.value = ''; message.value = '' })
onMounted(async () => {
  try { capabilities.value = await getCapabilities() }
  catch { message.value = '暂时无法连接登录服务，请稍后重试。' }
  if (route.query.reason) message.value = '暂时无法确认登录状态，请检查网络或重新登录。'
  if (route.query.passwordChanged) message.value = '密码已修改，请重新登录。'
})
async function send() {
  busy.value = true; message.value = ''
  try {
    const purpose = mode.value === 'register' ? 'REGISTER' : mode.value === 'reset' ? 'RESET_PASSWORD' : 'LOGIN'
    const challenge = await createChallenge(phone.value, purpose)
    challengeId.value = challenge.id; expiresAt.value = challenge.expiresAt
    resendAt.value = Date.now() + (capabilities.value?.resendAfterSeconds ?? 60) * 1000
    message.value = capabilities.value?.smsSimulated ? '本地模拟验证码已生效，请使用本机配置的测试验证码。' : '验证码已发送，请查看手机短信。'
  } catch (error) { message.value = error instanceof Error ? error.message : '发送失败，请稍后重试' }
  finally { busy.value = false }
}
async function submit() {
  message.value = ''
  if (mode.value !== 'login') {
    if (password.value !== confirmation.value) { message.value = '两次输入的密码不一致。'; return }
    if ([...password.value].length < (capabilities.value?.passwordMinLength ?? 10) || new TextEncoder().encode(password.value).length > (capabilities.value?.passwordMaxBytes ?? 72)) { message.value = '密码长度不符合下方要求。'; return }
  }
  busy.value = true
  try {
    if (mode.value === 'reset') {
      await resetPassword({ phone: phone.value, challengeId: challengeId.value, code: code.value, password: password.value })
      password.value = ''; confirmation.value = ''; code.value = ''
      await router.replace('/login')
      smsLogin.value = false; await nextTick(); message.value = '密码已重置，旧会话已退出，请使用新密码登录。'
      return
    }
    const tokens = mode.value === 'register'
      ? await register({ phone: phone.value, challengeId: challengeId.value, code: code.value, nickname: nickname.value.trim(), password: password.value })
      : smsLogin.value ? await createSession(phone.value, challengeId.value, code.value) : await passwordLogin(phone.value, password.value)
    await session.accept(tokens)
    password.value = ''; confirmation.value = ''; code.value = ''
    await router.replace(loginDestination(route.query.redirect, session.profile!.accountType))
  } catch (error) { message.value = error instanceof Error ? error.message : '操作失败，请重试' }
  finally { busy.value = false }
}
</script>
<template>
  <div class="auth-layout"><aside class="auth-poster" aria-label="校园生活介绍"><CampusIcon name="book" :size="34" /><h2>下课之后，<br />也有好去处。</h2><p>喜欢的小店、刚好的优惠，还有同学们的探店分享。</p><div class="poster-note"><CampusIcon name="ticket" :size="20" />给校园生活，多一点期待。</div><nav class="auth-note-board" aria-label="校园生活入口"><RouterLink to="/app"><CampusOrnament kind="pin" class="paper-fastener" /><CampusIcon name="ticket" :size="24" /><span><strong>给日常，留一张优惠</strong><small>看规则 · 领进券包 · 到店使用</small></span><CampusIcon name="arrow" :size="17" /></RouterLink><RouterLink to="/community"><CampusOrnament kind="clip" class="paper-fastener" /><CampusIcon name="community" :size="24" /><span><strong>把探店发现，贴在这里</strong><small>读一篇同学的真实分享</small></span><CampusIcon name="arrow" :size="17" /></RouterLink><RouterLink to="/nearby"><CampusOrnament kind="spark" class="paper-fastener" /><CampusIcon name="map" :size="24" /><span><strong>下课后，顺路逛一逛</strong><small>找找校园周边的小店</small></span><CampusIcon name="arrow" :size="17" /></RouterLink></nav><p class="poster-footer">发现优惠 · 领进券包 · 到店使用</p></aside><section class="auth-panel">
    <h1>{{ title }}</h1><p class="muted">{{ mode === 'login' ? '欢迎回来，把周边好优惠装进日常。' : mode === 'register' ? '从这里，开始发现校园周边。' : '验证手机号，重新设置你的密码。' }}</p>
    <div v-if="mode === 'login'" class="login-tabs" role="group" aria-label="登录方式">
      <button type="button" :class="{ secondary: smsLogin }" :aria-pressed="!smsLogin" :disabled="busy" @click="smsLogin = false">密码登录</button>
      <button type="button" :class="{ secondary: !smsLogin }" :aria-pressed="smsLogin" :disabled="busy" @click="smsLogin = true">短信登录</button>
    </div>
    <p v-if="mode === 'register'" class="muted">注册普通用户账号；商家可在登录后申请入驻。</p>
    <p v-if="mode === 'reset'" class="muted">验证手机号后设置新密码，所有旧会话将退出。尚未设置过密码的账号也可通过此流程设置。</p>
    <p v-if="needsCode && capabilities?.smsSimulated" class="notice" role="status">当前为本地短信模拟模式，不会发送真实短信。请使用本机配置的测试验证码。</p>
    <p v-if="needsCode && capabilities && !capabilities.smsAvailable" class="notice" role="status">短信服务暂不可用，请稍后重试。</p>
    <form @submit.prevent="submit">
      <label for="phone">手机号</label>
      <input id="phone" v-model.trim="phone" type="tel" inputmode="numeric" autocomplete="tel" maxlength="11" required pattern="1[3-9][0-9]{9}" :disabled="busy" />
      <template v-if="mode === 'register'"><label for="nickname">昵称</label><input id="nickname" v-model.trim="nickname" autocomplete="nickname" required maxlength="64" :disabled="busy" /></template>
      <div v-if="needsCode" class="code-row">
        <div><label for="code">验证码</label><input id="code" v-model.trim="code" inputmode="numeric" autocomplete="one-time-code" :maxlength="capabilities?.codeLength ?? 6" required :disabled="busy" /></div>
        <button type="button" :disabled="busy || !validPhone || !capabilities?.smsAvailable || !capabilities.enabled || countdown > 0" @click="send">{{ countdown > 0 ? countdown + ' 秒后重发' : '获取验证码' }}</button>
      </div>
      <p v-if="needsCode && expiresAt" class="muted">验证码有效至 {{ new Date(expiresAt).toLocaleTimeString() }}</p>
      <template v-if="!smsLogin || mode !== 'login'">
        <label for="password">{{ mode === 'login' ? '密码' : '新密码' }}</label>
        <input id="password" v-model="password" type="password" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" maxlength="72" required :disabled="busy" />
        <template v-if="mode !== 'login'">
          <label for="confirmation">确认密码</label><input id="confirmation" v-model="confirmation" type="password" autocomplete="new-password" maxlength="72" required :disabled="busy" />
          <p class="muted">至少 {{ capabilities?.passwordMinLength ?? 10 }} 个字符，UTF-8 编码不超过 {{ capabilities?.passwordMaxBytes ?? 72 }} 字节。建议使用字母、数字和符号组合。</p>
        </template>
      </template>
      <p v-if="message" role="status" aria-live="polite">{{ message }}</p>
      <button class="full-width" type="submit" :disabled="busy || !validPhone || !capabilities?.enabled || (needsCode && (!challengeId || !code || !capabilities.smsAvailable))">{{ busy ? '处理中…' : title }}</button>
    </form>
    <nav class="auth-links" aria-label="账号操作"><RouterLink v-if="mode !== 'login'" to="/login">返回登录</RouterLink><RouterLink v-if="mode !== 'register'" to="/register">注册账号</RouterLink><RouterLink v-if="mode !== 'reset'" to="/forgot-password">忘记密码</RouterLink></nav>
  </section></div>
</template>
<style scoped>
.login-tabs,.auth-links{display:flex;gap:16px;margin:20px 0;flex-wrap:wrap}.auth-links a{text-decoration:underline}</style>
