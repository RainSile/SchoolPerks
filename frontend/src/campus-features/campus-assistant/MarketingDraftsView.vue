<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useSessionStore } from '../../campus-stores/session'
import { config } from '../../campus-shared/config'
import { myMerchants, type MerchantWorkspace } from '../campus-merchant/workflow-api'
import PaginationBar from '../../campus-shared/PaginationBar.vue'
import * as api from './marketing-api'
const route = useRoute(), session = useSessionStore()
const stores = ref<MerchantWorkspace[]>([]), merchant = ref(''), storePage = ref(1), storeTotal = ref(0)
const capabilities = ref<api.MarketingCapabilities | null>(null), rows = ref<api.MarketingDraft[]>([]), page = ref(1), total = ref(0)
const coupons = ref<{ id: string; title: string }[]>([]), busy = ref(false), message = ref(''), editing = ref<api.MarketingDraft | null>(null)
const form = reactive({ brief: '', tone: 'FRIENDLY', couponId: '' }), editForm = reactive({ title: '', body: '' })
const pendingKey = ref(''), states = { GENERATING: '生成中', DRAFT: '待人工确认', FAILED: '生成失败', ARCHIVED: '已归档' }
const currentStore = computed(() => stores.value.find(store => store.id === merchant.value))
const canManage = computed(() => currentStore.value?.status === 'APPROVED' && ['OWNER', 'MANAGER'].includes(currentStore.value.role))
let active = true, generation = 0, timer: ReturnType<typeof setTimeout> | undefined, controller: AbortController | null = null
watch(() => [form.brief, form.tone, form.couponId], () => { pendingKey.value = '' })
function stopPoll() { if (timer) clearTimeout(timer); timer = undefined }
function poll() {
  stopPoll()
  if (active && rows.value.some(row => row.status === 'GENERATING')) timer = setTimeout(() => { if (!busy.value) void load(page.value, true); else poll() }, config.marketingPollIntervalMs)
}
async function load(next = 1, quiet = false) {
  if (!merchant.value) return
  const epoch = generation, target = merchant.value
  if (!quiet) busy.value = true
  try {
    const result = await api.marketingDrafts(target, next)
    if (!active || epoch !== generation) return
    rows.value = result.items; total.value = result.total; page.value = result.page; poll()
  } catch (error) { if (active && epoch === generation) { message.value = error instanceof Error ? error.message : '草稿加载失败'; poll() } }
  finally { if (active && epoch === generation && !quiet) busy.value = false }
}
async function selectStore() {
  const epoch = ++generation; stopPoll(); controller?.abort()
  rows.value = []; editing.value = null; pendingKey.value = ''; capabilities.value = null; coupons.value = []; message.value = ''; total.value = 0
  if (!merchant.value) return
  busy.value = true
  try {
    const cap = await api.marketingCapabilities(merchant.value)
    if (!active || epoch !== generation) return
    capabilities.value = cap
    const choices = await api.marketingCoupons(merchant.value)
    if (!active || epoch !== generation) return
    coupons.value = choices.items; form.couponId = ''; await load()
  } catch (error) { if (active && epoch === generation) message.value = error instanceof Error ? error.message : '门店能力加载失败' }
  finally { if (active && epoch === generation) busy.value = false }
}
async function loadStores(next = 1) {
  busy.value = true
  try {
    const result = await myMerchants(next)
    if (!active) return
    stores.value = result.items; storePage.value = result.page; storeTotal.value = result.total
    merchant.value = result.items.find(store => store.id === route.query.id)?.id ?? result.items[0]?.id ?? ''; await selectStore()
  } catch (error) { if (active) message.value = error instanceof Error ? error.message : '门店加载失败' }
  finally { if (active) busy.value = false }
}
onMounted(() => loadStores())
onBeforeUnmount(() => { active = false; generation++; stopPoll(); controller?.abort() })
async function generate(row?: api.MarketingDraft) {
  const epoch = generation, target = merchant.value
  busy.value = true; message.value = ''; const pending = new AbortController(); controller = pending
  const abortTimer = setTimeout(() => pending.abort(), config.apiTimeoutMs)
  try {
    if (!pendingKey.value) pendingKey.value = crypto.randomUUID()
    const result = row ? await api.retryMarketingDraft(target, row, pending.signal) : await api.generateMarketingDraft(target, pendingKey.value, { brief: form.brief.trim(), tone: form.tone, couponId: form.couponId || null }, pending.signal)
    if (!active || epoch !== generation) return
    if (result.status === 'DRAFT') { if (!row) pendingKey.value = ''; message.value = '文案已保存为草稿，请核对事实并人工编辑。' }
    else message.value = '任务已受理，正在查询生成状态。'
  } catch (error) { if (active && epoch === generation) message.value = pending.signal.aborted ? '请求等待超时，任务可能仍在生成。列表会更新状态；继续确认使用原请求，不会新建重复任务。' : error instanceof Error ? error.message : '生成暂不可用，请检查草稿状态' }
  finally {
    clearTimeout(abortTimer); controller = null
    if (active && epoch === generation) { await load(page.value); if (active && epoch === generation) { busy.value = false; capabilities.value = await api.marketingCapabilities(target).catch(() => capabilities.value) } }
  }
}
function edit(row: api.MarketingDraft) { editing.value = row; editForm.title = row.title ?? ''; editForm.body = row.body ?? '' }
async function save() {
  const row = editing.value; if (!row) return
  busy.value = true; message.value = ''
  try { await api.updateMarketingDraft(merchant.value, row, editForm.title.trim(), editForm.body.trim()); editing.value = null; await load(page.value); message.value = '人工修改已保存。草稿没有对外发布。' }
  catch (error) { message.value = error instanceof Error ? error.message : '保存失败，请刷新后重试'; await load(page.value) }
  finally { busy.value = false }
}
async function archive(row: api.MarketingDraft) {
  busy.value = true; message.value = ''
  try { await api.archiveMarketingDraft(merchant.value, row); if (editing.value?.id === row.id) editing.value = null; await load(page.value); message.value = '草稿已归档。' }
  catch (error) { message.value = error instanceof Error ? error.message : '归档失败'; await load(page.value) }
  finally { busy.value = false }
}
</script>
<template><section><h1>AI 营销草稿</h1><p class="muted">根据本店公开信息起草文案。生成结果仅供内部使用，请核对金额、有效期与适用条件；优惠活动仍需在优惠管理中完成审核与发布。</p>
  <label for="marketingStore">选择经营门店</label><select id="marketingStore" v-model="merchant" :disabled="busy" @change="selectStore"><option v-for="store in stores" :key="store.id" :value="store.id">{{ store.name }}</option></select>
  <PaginationBar :page="storePage" :size="20" :total="storeTotal" :disabled="busy" @change="loadStores" />
  <p v-if="!stores.length && !busy">暂无可经营门店。</p><p v-if="merchant && !canManage" class="notice">当前门店状态或角色仅允许查看已有草稿。</p>
  <p v-if="canManage && capabilities && !capabilities.enabled" class="notice">营销生成服务尚未启用或配置不完整，已有草稿仍可查看和编辑。</p>
  <p v-if="capabilities">今日模型调用 {{ capabilities.dailyChatUsed }} / {{ capabilities.dailyChatLimit }} 次，与智能助手共享额度，按 UTC 日期重置；失败的供应商调用也计入。</p>
  <form v-if="canManage" class="workspace-card" @submit.prevent="generate()"><h2>生成新文案</h2><fieldset class="plain-fieldset" :disabled="busy || !capabilities?.enabled">
    <label for="marketingBrief">文案要求</label><textarea id="marketingBrief" v-model="form.brief" required :maxlength="capabilities?.maxBriefLength ?? 2000" rows="4" placeholder="例如：面向新同学，突出本店适合朋友聚餐，语气自然简洁。请勿输入顾客隐私。" />
    <label for="marketingTone">文案语气</label><select id="marketingTone" v-model="form.tone"><option value="FRIENDLY">亲切自然</option><option value="CONCISE">简洁直接</option><option value="LIVELY">轻松活泼</option></select>
    <label for="marketingCoupon">参考本店优惠（可选，最近100条）</label><select id="marketingCoupon" v-model="form.couponId"><option value="">仅介绍门店，不生成优惠价格</option><option v-for="coupon in coupons" :key="coupon.id" :value="coupon.id">{{ coupon.title }}</option></select>
    <button type="submit" :disabled="!form.brief.trim()">{{ busy ? '处理中…' : pendingKey ? '继续确认生成' : '生成并保存草稿' }}</button>
  </fieldset></form>
  <p v-if="message" role="status" aria-live="polite">{{ message }}</p>
  <form v-if="editing" class="workspace-card" @submit.prevent="save"><h2>人工编辑文案</h2><fieldset class="plain-fieldset" :disabled="busy"><label for="marketingTitle">文案标题</label><input id="marketingTitle" v-model="editForm.title" required :maxlength="capabilities?.maxTitleLength ?? 100" /><label for="marketingBody">文案正文</label><textarea id="marketingBody" v-model="editForm.body" required :maxlength="capabilities?.maxBodyLength ?? 4000" rows="8" /><div class="button-row"><button>保存人工修改</button><button type="button" class="secondary" @click="editing = null">取消编辑</button></div></fieldset></form>
  <div class="button-row spaced"><h2>本店文案</h2><button class="secondary" :disabled="busy || !merchant" @click="load(page)">刷新草稿</button></div>
  <article v-for="row in rows" :key="row.id" class="workspace-card"><h3>{{ row.title ?? '营销文案生成任务' }}</h3><p>{{ states[row.status] }} · 第 {{ row.attempt }} 次生成 · 版本 {{ row.version }}</p><p class="muted">创建于 {{ new Date(row.createdAt).toLocaleString() }}</p>
    <p v-if="row.status === 'GENERATING'" role="status">正在生成，离开页面后可返回查看结果。</p><p v-if="row.errorCode" class="notice">本次生成未完成（{{ row.errorCode }}）。重试将计入调用额度。</p>
    <p v-if="!row.sourceCurrent" class="notice">参考信息已经变化或不可用，请重新核实当前优惠条件后再使用文案。</p>
    <p v-if="row.body" class="marketing-copy">{{ row.body }}</p><p v-if="row.sources.length" class="muted">参考来源：{{ row.sources.map(source => source.title).join('、') }}</p>
    <div v-if="canManage" class="button-row"><button v-if="row.status === 'DRAFT'" class="secondary" :disabled="busy" @click="edit(row)">编辑文案</button><button v-if="row.status === 'FAILED' && row.createdBy === session.profile?.id" :disabled="busy || !capabilities?.enabled" @click="generate(row)">重试生成</button><button v-if="!['GENERATING','ARCHIVED'].includes(row.status)" class="secondary" :disabled="busy" @click="archive(row)">归档草稿</button></div>
  </article><p v-if="!rows.length && merchant && !busy">暂无营销草稿。</p><PaginationBar :page="page" :size="20" :total="total" :disabled="busy" @change="load" />
</section></template>
<style scoped>section,fieldset{min-width:0}section{overflow-wrap:anywhere}select{max-width:100%}.marketing-copy{white-space:pre-wrap;overflow-wrap:anywhere}</style>
