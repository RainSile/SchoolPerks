<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { categories, categoryLabels, submitApplication } from './workflow-api'
import { getSchools, type School } from '../campus-user/api'
import QualificationUpload from '../campus-file/QualificationUpload.vue'
const router = useRouter(), schools = ref<School[]>([]), kinds = ref<string[]>([]), busy = ref(true), message = ref('')
const form = reactive({ schoolId: '', name: '', category: '', address: '', description: '', qualificationFileId: '' })
onMounted(async () => { try { [schools.value, kinds.value] = await Promise.all([getSchools(), categories()]) } catch (error) { message.value = error instanceof Error ? error.message : '加载失败' } finally { busy.value = false } })
async function submit() {
  busy.value = true; message.value = ''
  try { await submitApplication({ ...form, name: form.name.trim(), address: form.address.trim() }); await router.replace('/merchant/applications') }
  catch (error) { message.value = error instanceof Error ? error.message : '申请提交失败' }
  finally { busy.value = false }
}
</script>
<template>
  <section class="auth-panel"><RouterLink class="text-link" to="/merchant/applications">查看我的申请 →</RouterLink><h1>商家入驻申请</h1><p class="muted">请填写真实门店资料并上传本人资质。审核通过后才会公开展示。</p>
    <p v-if="!busy && !schools.length" class="notice">暂无可选学校，请联系平台维护真实学校资料。</p>
    <form @submit.prevent="submit"><fieldset :disabled="busy" class="plain-fieldset">
      <label for="merchantSchool">服务校园</label><select id="merchantSchool" v-model="form.schoolId" required><option value="" disabled>请选择学校</option><option v-for="school in schools" :key="school.id" :value="school.id">{{ school.name }}</option></select>
      <label for="merchantName">门店名称</label><input id="merchantName" v-model="form.name" required maxlength="120" />
      <label for="merchantCategory">商家分类</label><select id="merchantCategory" v-model="form.category" required><option value="" disabled>请选择分类</option><option v-for="kind in kinds" :key="kind" :value="kind">{{ categoryLabels[kind] ?? kind }}</option></select>
      <label for="merchantAddress">门店地址</label><input id="merchantAddress" v-model="form.address" required maxlength="255" />
      <label for="merchantDescription">门店介绍</label><textarea id="merchantDescription" v-model="form.description" maxlength="1000" rows="4" />
      <QualificationUpload v-model="form.qualificationFileId" />
      <button type="submit" :disabled="busy || !form.qualificationFileId">提交入驻申请</button>
    </fieldset></form><p v-if="message" role="status">{{ message }}</p>
  </section>
</template>
