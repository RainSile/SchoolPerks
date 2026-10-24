<script setup lang="ts">
import { onMounted, ref } from 'vue'
import PaginationBar from '../../campus-shared/PaginationBar.vue'
import { useLiveNotifications } from '../campus-notification/live'
import * as api from './api'
const items = ref<api.Order[]>([]), page = ref(1), total = ref(0), busy = ref(false), message = ref('')
const capability = ref<api.Capabilities | null>(null), teams = ref<Record<string, api.Group>>({}), refunds = ref<Record<string, api.Refund>>({})
const checkout = ref<{ orderId: string; url: string } | null>(null), code = ref<{ orderId: string; code: string; expiresAt: string } | null>(null)
async function load(target = page.value) {
  busy.value = true; message.value = ''
  try {
    const result = await api.orders(target); items.value = result.items; page.value = result.page; total.value = result.total; capability.value = await api.capabilities()
    const nextTeams: Record<string, api.Group> = {}, nextRefunds: Record<string, api.Refund> = {}
    for (const o of items.value) {
      if (o.groupId && !nextTeams[o.groupId]) nextTeams[o.groupId] = await api.group(o.groupId)
      if (['REFUND_PENDING', 'REFUNDED'].includes(o.status)) { const values = await api.refunds(o.id); if (values[0]) nextRefunds[o.id] = values[0] }
    }
    teams.value = nextTeams; refunds.value = nextRefunds
    if (checkout.value && !items.value.some(o => o.id === checkout.value?.orderId && o.status === 'PENDING_PAYMENT')) checkout.value = null
    if (code.value && !items.value.some(o => o.id === code.value?.orderId && o.status === 'PAID')) code.value = null
  } catch (e) { message.value = e instanceof Error ? e.message : '订单加载失败' }
  finally { busy.value = false }
}
const live = useLiveNotifications(async () => { await load() }, api.orderEvents)
async function action(o: api.Order, operation: 'pay' | 'cancel' | 'refund' | 'code' | 'retry') {
  busy.value = true; message.value = ''; checkout.value = null; code.value = null
  try {
    if (operation === 'pay') { const p = await api.pay(o.id); checkout.value = { orderId: o.id, url: api.sandboxCheckoutUrl(p.checkoutUrl) }; message.value = '沙箱支付地址已准备，请打开支付宝沙箱完成付款。' }
    else if (operation === 'code') code.value = await api.redemptionCode(o.id)
    else { if (operation === 'cancel') await api.cancel(o.id); else if (operation === 'refund') await api.refund(o.id); else { const r = refunds.value[o.id]; if (r) await api.retryRefund(o.id, r.id) }; await load() }
  } catch (e) { message.value = e instanceof Error ? e.message : '操作失败，请刷新确认订单状态' }
  finally { busy.value = false }
}
onMounted(async () => { await load(); await live.connect() })
</script>
<template><section>
  <div class="section-heading"><h1>我的订单</h1><RouterLink to="/trade" class="text-link">浏览套餐 →</RouterLink><button class="secondary" :disabled="busy" @click="load()">刷新订单</button></div>
  <p class="muted">{{ live.state.value }} · 支付结果以服务器确认为准，沙箱跳转页面不代表付款成功。</p>
  <p v-if="capability && !capability.paymentAvailable" class="notice">支付宝沙箱尚未配置，付款暂不可用。</p>
  <p v-if="message" role="status">{{ message }}</p>
  <article v-for="o in items" :key="o.id" class="workspace-card"><div class="section-heading"><h2>{{ o.offerSnapshot.title }}</h2><span class="badge">{{ api.stateLabels[o.status] ?? o.status }}</span></div>
    <p>订单 {{ o.id }} · {{ o.orderNo }} · ¥{{ o.amount }} {{ o.currency }}</p><p>{{ o.offerSnapshot.description }}</p>
    <p v-if="o.status === 'PENDING_PAYMENT'">支付截止：{{ new Date(o.expiresAt).toLocaleString() }}</p>
    <p v-if="o.groupId && teams[o.groupId]">团号 {{ o.groupId }} · {{ teams[o.groupId]!.status === 'FAILED' ? '未成团' : api.stateLabels[teams[o.groupId]!.status] }} · 已付款 {{ teams[o.groupId]!.paidCount }}/{{ teams[o.groupId]!.targetSize }} · 截止 {{ new Date(teams[o.groupId]!.expiresAt).toLocaleString() }}</p>
    <div class="button-row"><template v-if="o.status === 'PENDING_PAYMENT'"><button :disabled="busy || !capability?.paymentAvailable" @click="action(o, 'pay')">准备沙箱支付</button><button class="secondary" :disabled="busy" @click="action(o, 'cancel')">取消订单</button></template>
      <template v-if="o.status === 'PAID'"><button :disabled="busy" @click="action(o, 'code')">生成到店核销码</button><button class="secondary" :disabled="busy" @click="action(o, 'refund')">申请全额退款</button></template>
    </div>
    <p v-if="checkout?.orderId === o.id"><a class="text-link" :href="checkout.url" target="_blank" rel="noopener noreferrer">打开支付宝沙箱收银台 ↗</a> · 付款后刷新此页</p>
    <div v-if="code?.orderId === o.id" class="notice"><p>向本店店员出示订单编号和核销码，有效至 {{ new Date(code.expiresAt).toLocaleTimeString() }}</p><code class="trade-code">{{ code.code }}</code></div>
    <p v-if="refunds[o.id]">退款 {{ api.refundStateLabels[refunds[o.id]!.status] ?? refunds[o.id]!.status }} · ¥{{ refunds[o.id]!.amount }} · 尝试 {{ refunds[o.id]!.attempts }} 次 <button v-if="refunds[o.id]!.status === 'FAILED'" class="secondary" :disabled="busy" @click="action(o, 'retry')">重试退款</button></p>
  </article><p v-if="!busy && !items.length">暂无订单。</p><PaginationBar :page="page" :size="20" :total="total" :disabled="busy" @change="load" />
</section></template>
