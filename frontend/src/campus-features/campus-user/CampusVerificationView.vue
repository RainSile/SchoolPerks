<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { applyVerification, getSchools, getVerifications, type CampusVerification, type School } from './api'
const schools = ref<School[]>([]), history = ref<CampusVerification[]>([])
const schoolId = ref(''), realName = ref(''), studentNumber = ref(''), message = ref(''), busy = ref(true)
const active = computed(() => history.value.some(item => ['PENDING', 'APPROVED'].includes(item.status)))
const labels: Record<string, string> = { PENDING: '审核中', APPROVED: '已通过', REJECTED: '未通过' }
onMounted(async () => {
  try { [schools.value, history.value] = await Promise.all([getSchools(), getVerifications()]) }
  catch (error) { message.value = error instanceof Error ? error.message : '加载失败' }
  finally { busy.value = false }
})
async function submit() {
  busy.value = true; message.value = ''
  try {
    await applyVerification(schoolId.value, realName.value.trim(), studentNumber.value.trim())
    realName.value = ''; studentNumber.value = ''; history.value = await getVerifications()
    message.value = '申请已提交，审核结果将在此显示。'
  } catch (error) { message.value = error instanceof Error ? error.message : '提交失败' }
  finally { busy.value = false }
}
</script>
<template>
  <section class="auth-panel">
    <RouterLink class="text-link" to="/account">← 返回个人资料</RouterLink><h1>校园认证</h1>
    <p class="muted">提交本人在校信息，审核通过后生效。身份信息仅用于认证。</p>
    <p v-if="!busy && !schools.length" class="notice">暂无可选学校，请联系平台维护学校资料。</p>
    <form v-if="!active" @submit.prevent="submit">
      <label for="school">学校</label><select id="school" v-model="schoolId" required :disabled="busy"><option disabled value="">请选择学校</option><option v-for="school in schools" :key="school.id" :value="school.id">{{ school.name }}</option></select>
      <label for="realName">真实姓名</label><input id="realName" v-model="realName" required maxlength="80" autocomplete="name" :disabled="busy" />
      <label for="studentNumber">学号</label><input id="studentNumber" v-model="studentNumber" required maxlength="64" pattern="[A-Za-z0-9-]+" :disabled="busy" />
      <button :disabled="busy || !schoolId || !realName.trim() || !studentNumber.trim()">提交认证申请</button>
    </form>
    <p v-else class="notice">已有待审核或通过的申请，无需重复提交。</p>
    <p role="status" v-if="message">{{ message }}</p>
    <h2>申请记录</h2><p v-if="!busy && !history.length" class="muted">尚未提交校园认证。</p>
    <article v-for="item in history" :key="item.id" class="verification-record"><h3>{{ item.schoolName }}</h3><p>{{ labels[item.status] ?? item.status }} · {{ new Date(item.submittedAt).toLocaleString() }}</p><p v-if="item.reviewReason">{{ item.reviewReason }}</p></article>
  </section>
</template>
