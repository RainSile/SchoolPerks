<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { request, type PageResponse } from '../../campus-shared/http'
import PaginationBar from '../../campus-shared/PaginationBar.vue'
interface Event { id: string; actorId: string; action: string; resourceType: string; resourceId: string; outcome: string; httpStatus: number; errorCode: string | null; durationMillis: number; createdAt: string }
const filters = reactive({ action: '', actorId: '', outcome: '', from: '', to: '' })
const rows = ref<Event[]>([]), page = ref(1), total = ref(0), busy = ref(false), message = ref('')
let controller: AbortController | undefined, revision = 0
async function load(next = 1) {
  controller?.abort(); const current = ++revision; controller = new AbortController()
  busy.value = true; message.value = ''; rows.value = []
  try {
    if (!!filters.from !== !!filters.to) throw new Error('开始和结束时间需要同时填写')
    const query = new URLSearchParams({ page: String(next), size: '20' })
    for (const key of ['action', 'actorId', 'outcome'] as const) if (filters[key].trim()) query.set(key, filters[key].trim())
    if (filters.from) { query.set('from', new Date(filters.from).toISOString()); query.set('to', new Date(filters.to).toISOString()) }
    const data = await request<PageResponse<Event>>('/admin/operation-audit-events?' + query, { signal: controller.signal })
    if (current !== revision) return
    rows.value = data.items; total.value = data.total; page.value = data.page
  } catch (error) { if (current === revision) message.value = error instanceof Error ? error.message : '审计加载失败' }
  finally { if (current === revision) busy.value = false }
}
onMounted(() => load())
onBeforeUnmount(() => { ++revision; controller?.abort() })
const outcomes: Record<string, string> = { SUCCESS: '成功', DENIED: '拒绝', FAILURE: '失败' }
</script>
<template><section>
  <h1>操作结果审计</h1><p class="muted">记录已标注接口的调用结果；不包含请求正文、响应正文或异常原文。空时间使用服务端默认区间，最大范围由服务端配置。</p>
  <RouterLink class="text-link" to="/admin/audit-events">查看业务事务审计 →</RouterLink>
  <form class="inline-form" @submit.prevent="load()">
    <div><label for="opActor">操作者编号</label><input id="opActor" v-model="filters.actorId" inputmode="numeric" pattern="[1-9][0-9]{0,17}" maxlength="18" :disabled="busy" /></div>
    <div><label for="opAction">操作代码</label><input id="opAction" v-model="filters.action" pattern="[A-Z0-9_]{1,100}" maxlength="100" :disabled="busy" /></div>
    <div><label for="opOutcome">结果</label><select id="opOutcome" v-model="filters.outcome" :disabled="busy"><option value="">全部</option><option v-for="(label, code) in outcomes" :key="code" :value="code">{{ label }}</option></select></div>
    <div><label for="opFrom">开始时间（本机时区）</label><input id="opFrom" v-model="filters.from" type="datetime-local" :disabled="busy" /></div>
    <div><label for="opTo">结束时间（不含）</label><input id="opTo" v-model="filters.to" type="datetime-local" :disabled="busy" /></div>
    <button :disabled="busy">查询结果</button>
  </form>
  <p v-if="busy" role="status">正在加载…</p><p v-if="message" role="alert">{{ message }}</p><p v-if="!busy && !message && !rows.length">暂无符合条件的操作记录</p>
  <div class="table-scroll"><table><thead><tr><th>时间</th><th>操作者</th><th>操作</th><th>资源</th><th>结果</th><th>HTTP</th><th>错误代码</th><th>耗时（ms）</th></tr></thead><tbody><tr v-for="row in rows" :key="row.id"><td>{{ new Date(row.createdAt).toLocaleString() }}</td><td>{{ row.actorId }}</td><td>{{ row.action }}</td><td>{{ row.resourceType }} / {{ row.resourceId }}</td><td>{{ outcomes[row.outcome] ?? row.outcome }}</td><td>{{ row.httpStatus }}</td><td>{{ row.errorCode ?? '—' }}</td><td>{{ row.durationMillis }}</td></tr></tbody></table></div>
  <PaginationBar :page="page" :size="20" :total="total" :disabled="busy" @change="load" />
</section></template>
