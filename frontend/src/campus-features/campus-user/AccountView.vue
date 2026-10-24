<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSessionStore } from '../../campus-stores/session'
import { updateProfile } from './api'
const session = useSessionStore(), router = useRouter()
const nickname = ref(session.profile?.nickname ?? ''), message = ref(''), busy = ref(false)
const statuses: Record<string, string> = { UNVERIFIED: '未认证', PENDING: '审核中', APPROVED: '已认证', REJECTED: '未通过' }
async function save() {
  busy.value = true; message.value = ''
  try { session.profile = await updateProfile(nickname.value.trim()); message.value = '资料已保存。' }
  catch (error) { message.value = error instanceof Error ? error.message : '保存失败' }
  finally { busy.value = false }
}
async function logout() {
  busy.value = true
  try { await session.logout(); await router.replace('/login') }
  catch { message.value = '注销未完成，请检查网络后重试。' }
  finally { busy.value = false }
}
</script>
<template>
  <section class="auth-panel" v-if="session.profile">
    <h1>个人资料</h1><p class="muted">管理你的校园身份与账号信息。</p>
    <dl><dt>用户编号</dt><dd>{{ session.profile.id }}</dd><dt>手机号</dt><dd>{{ session.profile.maskedPhone }}</dd><dt>校园认证</dt><dd>{{ statuses[session.profile.campusVerificationStatus] ?? session.profile.campusVerificationStatus }}</dd><dt>已认证学校</dt><dd>{{ session.profile.schoolName ?? '暂无' }}</dd></dl>
    <form @submit.prevent="save"><label for="nickname">昵称</label><input id="nickname" v-model="nickname" required maxlength="64" :disabled="busy" /><button type="submit" :disabled="busy || !nickname.trim()">保存昵称</button></form>
    <p v-if="message" role="status">{{ message }}</p>
    <p><RouterLink class="text-link" to="/account/password">修改密码 →</RouterLink></p>
    <p v-if="session.profile?.accountType === 'USER'"><RouterLink class="text-link" to="/account/campus-verification">查看与提交校园认证 →</RouterLink></p>
    <p v-if="session.profile?.accountType === 'USER'"><RouterLink class="text-link" to="/account/merchant-applications">商家入驻与我的申请 →</RouterLink></p>
    <button class="secondary" :disabled="busy" @click="logout">退出当前账号</button>
  </section>
  <section v-else><p>登录已失效，请重新登录。</p><RouterLink to="/login">前往登录</RouterLink></section>
</template>
