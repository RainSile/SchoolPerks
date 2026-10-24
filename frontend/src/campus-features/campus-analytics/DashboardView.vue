<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { dashboard, type Dashboard } from './api'
import { myMerchants, type MerchantWorkspace } from '../campus-merchant/workflow-api'
import TrendChart from './TrendChart.vue'
const platform = useRoute().path.startsWith('/admin/')
const stores = ref<MerchantWorkspace[]>([]), merchant = ref(''), report = ref<Dashboard>()
const dates = reactive({ from: '', to: '', zone: '' })
const busy = ref(false), message = ref(''), initialized = ref(false)
let controller: AbortController | undefined, revision = 0, disposed = false
const rate = computed(() => report.value?.metrics.cohortRedemptionRate == null ? '— 无领券' : (Number(report.value.metrics.cohortRedemptionRate) * 100).toFixed(2) + '%')
async function load(refresh = false) {
  controller?.abort()
  const current = ++revision
  report.value = undefined; message.value = ''; busy.value = true
  if (!platform && !merchant.value) { busy.value = false; return }
  controller = new AbortController()
  try {
    const result = await dashboard(platform ? null : merchant.value, dates, refresh, controller.signal)
    if (current !== revision || disposed) return
    report.value = result
    dates.from = result.from; dates.to = result.to; dates.zone = result.zone
  } catch (error) {
    if (current === revision && !disposed && !(error instanceof DOMException && error.name === 'AbortError'))
      message.value = error instanceof Error ? error.message : '统计加载失败，请重试'
  } finally { if (current === revision && !disposed) busy.value = false }
}
onMounted(async () => {
  busy.value = true
  try {
    if (!platform) {
      let page = 1, total = 0
      // The server paginates memberships; never silently omit eligible stores after the first page.
      do {
        const data = await myMerchants(page++)
        if (disposed) return
        stores.value.push(...data.items.filter(m => ['OWNER', 'MANAGER'].includes(m.role) && ['APPROVED', 'SUSPENDED'].includes(m.status)))
        total += data.items.length
        if (!data.items.length || total >= data.total) break
      } while (true)
      merchant.value = stores.value[0]?.id ?? ''
    }
    initialized.value = true
    await load()
  } catch (error) { message.value = error instanceof Error ? error.message : '门店加载失败'; busy.value = false }
})
function retry() { if (initialized.value) void load(true); else window.location.reload() }
onBeforeUnmount(() => { disposed = true; ++revision; controller?.abort() })
const orderLabels: Record<string, string> = { PENDING_PAYMENT: '待支付', PAID: '已支付', CANCELLED: '已取消', REFUND_PENDING: '退款中', REFUNDED: '已退款', REDEEMED: '已核销', GROUP_PENDING: '待成团' }
</script>
<template>
  <section class="dashboard">
    <h1>{{ platform ? '平台经营看板' : '门店经营看板' }}</h1>
    <p class="muted">沙箱交易统计 · 日期含首尾 · 权限与门店范围由服务端校验</p>
    <form class="inline-form" @submit.prevent="load()">
      <div v-if="!platform"><label for="dashboardStore">门店</label><select id="dashboardStore" v-model="merchant" :disabled="busy" @change="load()"><option v-for="store in stores" :key="store.id" :value="store.id">{{ store.name }}{{ store.status === 'SUSPENDED' ? '（已暂停）' : '' }}</option></select></div>
      <div><label for="dashboardFrom">开始日期</label><input id="dashboardFrom" v-model="dates.from" type="date" :disabled="busy" /></div>
      <div><label for="dashboardTo">结束日期</label><input id="dashboardTo" v-model="dates.to" type="date" :disabled="busy" /></div>
      <div><label for="dashboardZone">统计时区</label><select id="dashboardZone" v-model="dates.zone" :disabled="busy || !report"><option v-for="zone in report?.allowedZones ?? [dates.zone]" :key="zone" :value="zone">{{ zone || '服务器默认时区' }}</option></select></div>
      <button :disabled="busy || (!platform && !merchant)">查询</button><button type="button" :disabled="busy || (!platform && !merchant)" @click="load(true)">重新计算</button>
    </form>
    <p v-if="busy" role="status">正在汇总统计…</p>
    <p v-if="message" role="alert">{{ message }} <button type="button" :disabled="busy" @click="retry">重试</button></p>
    <p v-if="initialized && !platform && !stores.length && !busy">暂无可查看统计的门店。仅店主和店长可查看所属门店。</p>
    <template v-if="report">
      <p>{{ report.merchantName ?? '全平台' }} · {{ report.from }} 至 {{ report.to }} · {{ report.zone }} · 最多 {{ report.maxDays }} 天</p>
      <p class="muted">计算时间 {{ new Date(report.calculatedAt).toLocaleString() }} · 缓存截止 {{ new Date(report.cacheExpiresAt).toLocaleString() }}</p>
      <div class="record-grid metrics">
        <article><h2>沙箱支付额（CNY）</h2><strong>{{ report.metrics.paidAmount }}</strong><p>{{ report.metrics.paidOrderCount }} 笔成功支付</p></article>
        <article><h2>成功退款额（CNY）</h2><strong>{{ report.metrics.refundAmount }}</strong><p>{{ report.metrics.refundCount }} 笔成功退款</p></article>
        <article><h2>净收款变动（CNY）</h2><strong>{{ report.metrics.netCashFlow }}</strong><p>支付额减退款额，可为负数</p></article>
        <article><h2>领券量</h2><strong>{{ report.metrics.claimCount }}</strong><p>{{ report.metrics.couponRedemptionCount }} 次优惠券核销</p></article>
        <article><h2>领取批次核销率</h2><strong>{{ rate }}</strong><p>{{ report.metrics.cohortRedemptionCount }} 张本窗口领取的券已核销</p></article>
        <article><h2>参与人数</h2><strong>{{ report.metrics.participatingUserCount }}</strong><p>领券或创建订单的去重账号</p></article>
        <article><h2>套餐订单</h2><strong>{{ report.metrics.createdOrderCount }}</strong><p>{{ report.metrics.orderRedemptionCount }} 次订单核销</p></article>
        <article><h2>浏览 UV</h2><strong>{{ report.pageViewUvAvailable ? report.pageViewUv : '— 未采集' }}</strong><p>未使用参与人数代替浏览 UV</p></article>
        <article v-if="platform"><h2>新增账号 / 门店</h2><strong>{{ report.newAccounts }} / {{ report.newMerchants }}</strong><p>按创建时间计数，账号包含各端</p></article>
      </div>
      <p v-if="!report.metrics.claimCount && !report.metrics.createdOrderCount && !report.metrics.paidOrderCount && !report.metrics.refundCount">所选日期暂无领券、新订单、支付或退款，可调整日期。</p>
      <div class="charts"><article><h2>每日领券与核销</h2><TrendChart :rows="report.daily" /></article><article><h2>每日沙箱金额（CNY）</h2><TrendChart :rows="report.daily" money /></article></div>
      <details><summary>每日完整明细（图表的文本版本）</summary><div class="table-scroll"><table><thead><tr><th>日期</th><th>领券</th><th>券核销</th><th>新订单</th><th>支付笔数</th><th>订单核销</th><th>退款笔数</th><th>支付额</th><th>退款额</th></tr></thead><tbody><tr v-for="day in report.daily" :key="day.date"><td>{{ day.date }}</td><td>{{ day.claimCount }}</td><td>{{ day.couponRedemptionCount }}</td><td>{{ day.createdOrderCount }}</td><td>{{ day.paidOrderCount }}</td><td>{{ day.orderRedemptionCount }}</td><td>{{ day.refundCount }}</td><td>{{ day.paidAmount }}</td><td>{{ day.refundAmount }}</td></tr></tbody></table></div></details>
      <h2>热门优惠券（按本窗口领取量）</h2><p v-if="!report.topCoupons.length">暂无领取记录</p><div v-else class="table-scroll"><table><thead><tr><th>优惠券</th><th>领取数</th><th>该批次已核销</th></tr></thead><tbody><tr v-for="coupon in report.topCoupons" :key="coupon.couponId"><td>{{ coupon.title }}</td><td>{{ coupon.claimCount }}</td><td>{{ coupon.cohortRedemptionCount }}</td></tr></tbody></table></div>
      <h2>本窗口创建订单的当前状态</h2><p v-if="!report.orderStatuses.length">暂无订单</p><ul v-else><li v-for="state in report.orderStatuses" :key="state.status">{{ orderLabels[state.status] ?? state.status }}：{{ state.count }}</li></ul>
      <details open><summary>统计口径与数据范围</summary><ul><li v-for="definition in report.definitions" :key="definition">{{ definition }}</li></ul><p>UTC 查询区间：[{{ report.startsAt }}, {{ report.endsAt }})</p></details>
    </template>
  </section>
</template>
<style scoped>
.dashboard { min-width: 0; }.metrics strong { font-size: 1.65rem; overflow-wrap: anywhere; }.metrics h2 { font-size: 1rem; }
.charts { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 380px), 1fr)); gap: 1rem; margin: 1.5rem 0; }.charts article { min-width: 0; }details { margin: 1rem 0; } summary { cursor: pointer; font-weight: 600; }
</style>
