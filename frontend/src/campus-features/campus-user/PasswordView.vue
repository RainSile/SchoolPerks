<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSessionStore } from '../../campus-stores/session'
import { changePassword, getCapabilities, type Capabilities } from './api'
const session = useSessionStore(), router = useRouter()
const current = ref(''), password = ref(''), confirmation = ref(''), busy = ref(false), message = ref(''), capabilities = ref<Capabilities | null>(null)
onMounted(async () => { try { capabilities.value = await getCapabilities() } catch { message.value = '暂时无法获取密码规则，请稍后重试。' } })
onUnmounted(() => { current.value = ''; password.value = ''; confirmation.value = '' })
async function save() {
  if (password.value !== confirmation.value) { message.value = '两次输入的密码不一致。'; return }
  busy.value = true; message.value = ''
  try {
    await changePassword(current.value, password.value)
    session.clear()
    await router.replace({ path: '/login', query: { passwordChanged: 'true' } })
  } catch (error) { message.value = error instanceof Error ? error.message : '密码修改失败' }
  finally { busy.value = false }
}
</script>
<template><section class="auth-panel"><h1>修改密码</h1><p class="muted">修改成功后所有设备都需要重新登录。尚未设置密码时，请退出后通过“忘记密码”验证手机号并设置。</p>
  <form @submit.prevent="save">
    <label for="current-password">当前密码</label><input id="current-password" v-model="current" type="password" autocomplete="current-password" required maxlength="72" :disabled="busy" />
    <label for="new-password">新密码</label><input id="new-password" v-model="password" type="password" autocomplete="new-password" required :minlength="capabilities?.passwordMinLength ?? 10" maxlength="72" :disabled="busy" />
    <label for="confirm-password">确认密码</label><input id="confirm-password" v-model="confirmation" type="password" autocomplete="new-password" required maxlength="72" :disabled="busy" />
    <p class="muted">至少 {{ capabilities?.passwordMinLength ?? 10 }} 个字符，UTF-8 编码不超过 {{ capabilities?.passwordMaxBytes ?? 72 }} 字节。</p>
    <p v-if="message" role="status">{{ message }}</p><button type="submit" :disabled="busy || !capabilities?.enabled">{{ busy ? '处理中…' : '修改密码并退出登录' }}</button>
  </form><p><RouterLink to="/account">返回个人资料</RouterLink></p>
</section></template>
