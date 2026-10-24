<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { myApplications, statusLabels, type MerchantApplication } from './workflow-api'
import PaginationBar from '../../campus-shared/PaginationBar.vue'
const records = ref<MerchantApplication[]>([]), page = ref(1), total = ref(0), busy = ref(false), message = ref('')
async function load(next = page.value) { busy.value = true; message.value = ''; try { const data = await myApplications(next); records.value = data.items; page.value = data.page; total.value = data.total } catch (error) { message.value = error instanceof Error ? error.message : '加载失败' } finally { busy.value = false } }
onMounted(() => load())
</script>
<template><section><div class="section-heading"><h1>我的入驻申请</h1><RouterLink class="text-link" to="/merchant/applications/new">申请入驻 →</RouterLink></div>
  <p v-if="message" role="status">{{ message }}</p><p v-if="!busy && !records.length" class="muted">还没有提交入驻申请。</p>
  <div class="record-grid"><article v-for="record in records" :key="record.id"><h2>{{ record.name }}</h2><p><span class="badge">{{ statusLabels[record.status] }}</span> · {{ record.schoolName }}</p><p>{{ record.address }}</p><p class="muted">提交时间：{{ new Date(record.submittedAt).toLocaleString() }}</p><p v-if="record.reviewReason">审核意见：{{ record.reviewReason }}</p><RouterLink v-if="record.merchantId" class="text-link" :to="{ path: '/merchant', query: { id: record.merchantId } }">进入门店工作台 →</RouterLink></article></div>
  <PaginationBar :page="page" :size="20" :total="total" :disabled="busy" @change="load" /></section></template>
