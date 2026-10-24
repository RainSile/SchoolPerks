<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { auditEvents, type AuditEvent } from './api'
import PaginationBar from '../../campus-shared/PaginationBar.vue'
const rows = ref<AuditEvent[]>([]), resourceId = ref(''), page = ref(1), total = ref(0), busy = ref(false), message = ref('')
async function load(next = 1) { busy.value = true; message.value = ''; try { const data = await auditEvents(next, resourceId.value.trim()); rows.value = data.items; page.value = data.page; total.value = data.total } catch (error) { message.value = error instanceof Error ? error.message : '加载失败' } finally { busy.value = false } }
onMounted(() => load())
</script>
<template><section><RouterLink class="text-link" to="/admin">← 返回管理端</RouterLink><h1>操作审计</h1><form class="inline-form" @submit.prevent="load()"><div><label for="auditResource">资源编号</label><input id="auditResource" v-model="resourceId" maxlength="64" :disabled="busy" /></div><button :disabled="busy">筛选审计</button></form><p v-if="message" role="status">{{ message }}</p><div class="table-scroll"><table><thead><tr><th>时间</th><th>操作者</th><th>操作</th><th>资源</th><th>变更信息</th></tr></thead><tbody><tr v-for="row in rows" :key="row.id"><td>{{ new Date(row.createdAt).toLocaleString() }}</td><td>{{ row.actorId }}</td><td>{{ row.action }}</td><td>{{ row.resourceType }} / {{ row.resourceId }}</td><td>{{ JSON.stringify(row.details) }}</td></tr></tbody></table></div><PaginationBar :page="page" :size="20" :total="total" :disabled="busy" @change="load" /></section></template>
