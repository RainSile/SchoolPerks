<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSessionStore } from '../../campus-stores/session'
import { updateProfile, getDefaultAvatars, setAvatar, type DefaultAvatar } from './api'
import UserAvatar from '../../campus-shared/UserAvatar.vue'
import AvatarPickerDialog from './AvatarPickerDialog.vue'
import CampusIcon from '../../campus-shared/CampusIcon.vue'
const session = useSessionStore(), router = useRouter()
const nickname = ref(session.profile?.nickname ?? ''), message = ref(''), busy = ref(false)
const defaults = ref<DefaultAvatar[]>([]), avatarDialog = ref(false)
onMounted(async () => { try { defaults.value = await getDefaultAvatars() } catch { message.value = '默认头像暂未加载，请稍后刷新。' } })
async function avatar(selection: { fileId: string } | { defaultKey: string }) {
  busy.value = true; message.value = ''
  try { await setAvatar(selection); await session.loadProfile(); avatarDialog.value = false; message.value = '头像已更新。' }
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
    <section class="avatar-settings"><div class="current-avatar"><button type="button" class="current-avatar-button" aria-label="更换头像" :disabled="busy" @click="message = ''; avatarDialog = true"><UserAvatar :image-url="session.profile.avatar?.imageUrl" :nickname="session.profile.nickname" :size="72" /><span class="avatar-edit-badge" aria-hidden="true"><CampusIcon name="plus" :size="13" /></span></button><div><h2>我的头像</h2><p class="muted">在社区分享和评论中展示。</p></div></div></section>
    <AvatarPickerDialog v-if="avatarDialog" :defaults="defaults" :current-key="session.profile.avatar?.defaultKey" :busy="busy" :message="message" @select="avatar" @close="avatarDialog = false" />
    <dl><dt>用户编号</dt><dd>{{ session.profile.id }}</dd><dt>手机号</dt><dd>{{ session.profile.maskedPhone }}</dd><dt>校园认证</dt><dd>{{ statuses[session.profile.campusVerificationStatus] ?? session.profile.campusVerificationStatus }}</dd><dt>已认证学校</dt><dd>{{ session.profile.schoolName ?? '暂无' }}</dd></dl>
    <form class="nickname-form" @submit.prevent="save"><label for="nickname">昵称</label><div class="nickname-row"><input id="nickname" v-model="nickname" required maxlength="64" :disabled="busy" /><button type="submit" :disabled="busy || !nickname.trim()">保存昵称</button></div></form>
    <p v-if="message" role="status">{{ message }}</p>
    <p><RouterLink class="text-link" to="/account/password">修改密码 →</RouterLink></p>
    <p v-if="session.profile?.accountType === 'USER'"><RouterLink class="text-link" to="/account/campus-verification">查看与提交校园认证 →</RouterLink></p>
    <p v-if="session.profile?.accountType === 'USER'"><RouterLink class="text-link" to="/account/merchant-applications">商家入驻与我的申请 →</RouterLink></p>
    <button class="secondary" :disabled="busy" @click="logout">退出当前账号</button>
  </section>
  <section v-else><p>登录已失效，请重新登录。</p><RouterLink to="/login">前往登录</RouterLink></section>
</template>
<style scoped>
.avatar-settings { margin:20px 0;padding-bottom:20px;border-bottom:1px solid var(--campus-line); }.current-avatar { display:flex;align-items:center;gap:16px; }.current-avatar h2 { margin:0; }
.current-avatar-button { position:relative;padding:0;border:0;border-radius:50%;background:none;flex:none; }.current-avatar-button:hover:not(:disabled) { background:none; }.avatar-edit-badge { display:grid;place-items:center;position:absolute;right:-2px;bottom:0;width:23px;height:23px;border:3px solid white;border-radius:50%;background:var(--campus-blue);color:white; }
.nickname-row { display:flex;gap:10px;align-items:center; }.nickname-row input { flex:1;min-width:0; }.nickname-row button { flex:none;white-space:nowrap;min-height:40px;margin:0; }
</style>
