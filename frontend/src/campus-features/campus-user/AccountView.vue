<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSessionStore } from '../../campus-stores/session'
import { updateProfile, getDefaultAvatars, setAvatar, type DefaultAvatar } from './api'
import UserAvatar from '../../campus-shared/UserAvatar.vue'
import BusinessImageUpload from '../campus-file/BusinessImageUpload.vue'
const session = useSessionStore(), router = useRouter()
const nickname = ref(session.profile?.nickname ?? ''), message = ref(''), busy = ref(false)
const defaults = ref<DefaultAvatar[]>([]), avatarFile = ref(''), uploading = ref(false)
onMounted(async () => { try { defaults.value = await getDefaultAvatars() } catch { message.value = '默认头像暂未加载，请稍后刷新。' } })
async function avatar(selection: { fileId: string } | { defaultKey: string }) {
  busy.value = true; message.value = ''
  try { await setAvatar(selection); await session.loadProfile(); avatarFile.value = ''; message.value = '头像已更新。' }
  catch (error) { message.value = error instanceof Error ? error.message : '头像更新失败' }
  finally { busy.value = false }
}
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
    <section class="avatar-settings"><div class="current-avatar"><UserAvatar :image-url="session.profile.avatar?.imageUrl" :nickname="session.profile.nickname" :size="72" /><div><h2>我的头像</h2><p class="muted">在社区分享和评论中展示。</p></div></div>
      <div class="default-avatar-options"><button v-for="item in defaults" :key="item.key" type="button" class="secondary avatar-option" :aria-label="'选择' + item.label" :aria-pressed="session.profile.avatar?.defaultKey === item.key" :disabled="busy || uploading" @click="avatar({ defaultKey: item.key })"><UserAvatar :image-url="item.imageUrl" :nickname="item.label" :size="44" /><span>{{ item.label }}</span></button></div>
      <BusinessImageUpload v-model="avatarFile" purpose="USER_AVATAR" label="上传自定义头像" @busy="uploading = $event" />
      <button type="button" :disabled="busy || uploading || !avatarFile" @click="avatar({ fileId: avatarFile })">使用上传的头像</button>
    </section>
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
<style scoped>
.avatar-settings { margin:20px 0;padding-bottom:20px;border-bottom:1px solid var(--border); }.current-avatar { display:flex;align-items:center;gap:16px; }.current-avatar h2 { margin:0; }
.default-avatar-options { display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;margin-top:16px; }.avatar-option { display:flex;align-items:center;gap:8px;text-align:left;padding:8px;font-size:12px; }.avatar-option[aria-pressed=true] { outline:2px solid var(--primary); }
@media(max-width:360px){.default-avatar-options { grid-template-columns:1fr; }}
</style>
