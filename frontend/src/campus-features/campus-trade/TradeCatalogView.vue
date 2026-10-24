<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSessionStore } from '../../campus-stores/session'
import PaginationBar from '../../campus-shared/PaginationBar.vue'
import CampusIcon from '../../campus-shared/CampusIcon.vue'
import * as api from './api'
const router = useRouter(), session = useSessionStore()
const items = ref<api.Offer[]>([]), active = ref<api.Offer | null>(null), teams = ref<api.Group[]>([])
const page = ref(1), total = ref(0), groupPage = ref(1), groupTotal = ref(0), busy = ref(false), message = ref('')
const capability = ref<api.Capabilities | null>(null), keys = new Map<string, string>()
async function load(target = page.value) {
  busy.value = true; message.value = ''
  try { const result = await api.offers(target); items.value = result.items; page.value = result.page; total.value = result.total; capability.value = await api.capabilities() }
  catch (e) { message.value = e instanceof Error ? e.message : '套餐加载失败' }
  finally { busy.value = false }
}
async function showGroups(o: api.Offer, target = 1) {
  busy.value = true; message.value = ''; active.value = o; teams.value = []
  try { const result = await api.groups(o.id, target); teams.value = result.items; groupPage.value = result.page; groupTotal.value = result.total }
  catch (e) { message.value = e instanceof Error ? e.message : '拼团加载失败' }
  finally { busy.value = false }
}
async function buy(o: api.Offer, groupId: string | null = null) {
  if (!session.authenticated) { await router.push({ path: '/login', query: { redirect: '/trade' } }); return }
  busy.value = true; message.value = ''; const identity = `${o.id}/${groupId ?? 'new'}`
  let key = keys.get(identity); if (!key) { key = crypto.randomUUID(); keys.set(identity, key) }
  try { await api.createOrder(o.id, groupId, key); keys.delete(identity); await router.push('/account/orders') }
  catch (e) { message.value = e instanceof Error ? e.message : '下单失败，可使用原请求重试' }
  finally { busy.value = false }
}
onMounted(() => load())
</script>
<template><section>
  <div class="section-heading"><h1><CampusIcon name="team" :size="28" />校园套餐</h1><RouterLink to="/account/orders" class="text-link">我的订单 →</RouterLink><button class="secondary" :disabled="busy" @click="load()">刷新套餐</button></div>
  <p class="muted">每单一份，价格以订单快照为准。拼团须所有名额完成付款后成团；到店优惠券暂不叠加结算。</p>
  <p v-if="capability && !capability.paymentAvailable" class="notice">支付宝沙箱尚未配置。可查看套餐和创建待支付订单，当前无法付款。</p>
  <p v-if="message" role="status">{{ message }}</p>
  <div class="offer-list"><article v-for="o in items" :key="o.id" class="workspace-card offer-row">
    <span class="badge">{{ api.kindLabels[o.kind] }}</span><h2>{{ o.title }}</h2><p>{{ o.description }}</p><p class="offer-price"><strong>¥{{ o.price }}</strong><span>每份 · 剩余 {{ o.stockAvailable }} 份</span></p>
    <p class="muted">{{ new Date(o.startsAt).toLocaleString() }} 至 {{ new Date(o.endsAt).toLocaleString() }}</p>
    <p v-if="o.kind === 'GROUP'">{{ o.groupSize }} 人成团 · 最长 {{ Math.round(o.groupDurationSeconds / 60) }} 分钟</p>
    <div class="button-row"><button :disabled="busy || o.stockAvailable === 0" @click="buy(o)">{{ o.kind === 'GROUP' ? '发起拼团' : '创建订单' }}</button><button v-if="o.kind === 'GROUP'" class="secondary" :disabled="busy" @click="showGroups(o)">查看可加入的团</button></div>
  </article></div><p v-if="!busy && !items.length" class="muted">暂无已发布套餐。</p>
  <div class="trade-note"><h2>领券和套餐，有什么不同？</h2><p>优惠券用于到店消费时享受优惠；套餐会生成独立订单，价格以订单快照为准，两者暂不叠加。</p><RouterLink class="text-link" to="/app">去看看优惠券 →</RouterLink></div>
  <PaginationBar :page="page" :size="20" :total="total" :disabled="busy" @change="load" />
  <section v-if="active" class="workspace-card"><h2>{{ active.title }} · 正在拼团</h2>
    <article v-for="g in teams" :key="g.id"><p>团号 {{ g.id }} · 已付款 {{ g.paidCount }}/{{ g.targetSize }} · 已占位 {{ g.reservedCount }} · {{ new Date(g.expiresAt).toLocaleString() }} 截止</p><button :disabled="busy || g.reservedCount >= g.targetSize" @click="buy(active!, g.id)">加入此团</button></article>
    <p v-if="!teams.length">暂无可参与拼团，可以发起新团。</p><PaginationBar :page="groupPage" :size="20" :total="groupTotal" :disabled="busy" @change="showGroups(active!, $event)" />
  </section>
</section></template>
