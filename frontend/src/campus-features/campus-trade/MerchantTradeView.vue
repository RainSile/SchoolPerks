<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { myMerchants, type MerchantWorkspace } from '../campus-merchant/workflow-api'
import PaginationBar from '../../campus-shared/PaginationBar.vue'
import * as api from './api'
const stores = ref<MerchantWorkspace[]>([]), selected = ref(''), storePage = ref(1), storeTotal = ref(0)
const offers = ref<api.Offer[]>([]), orders = ref<api.Order[]>([]), offerPage = ref(1), orderPage = ref(1), offerTotal = ref(0), orderTotal = ref(0)
const busy = ref(false), message = ref(''), orderId = ref(''), code = ref('')
const current = computed(() => stores.value.find(s => s.id === selected.value))
const publisher = computed(() => current.value?.status === 'APPROVED' && ['OWNER', 'MANAGER'].includes(current.value.role))
const form = ref({ title: '', description: '', kind: 'REGULAR', price: '', stock: 1, startsAt: '', endsAt: '', groupSize: 2, groupDurationSeconds: 600 })
async function loadStores(target = 1) {
  busy.value = true; message.value = ''
  try { const result = await myMerchants(target); stores.value = result.items; storePage.value = result.page; storeTotal.value = result.total; if (!stores.value.some(s => s.id === selected.value)) selected.value = stores.value[0]?.id ?? '' }
  catch (e) { message.value = e instanceof Error ? e.message : '门店加载失败' }
  finally { busy.value = false }
}
async function load() {
  if (!selected.value) { offers.value = []; orders.value = []; return }
  const id = selected.value; busy.value = true; message.value = ''
  try {
    const [a, b] = await Promise.all([api.merchantOffers(id, offerPage.value), api.merchantOrders(id, orderPage.value)])
    if (id !== selected.value) return
    offers.value = a.items; offerTotal.value = a.total; orders.value = b.items; orderTotal.value = b.total
  } catch (e) { message.value = e instanceof Error ? e.message : '交易工作台加载失败' }
  finally { busy.value = false }
}
async function create() {
  if (!selected.value) return; busy.value = true; message.value = ''
  try {
    await api.createOffer(selected.value, { ...form.value, startsAt: new Date(form.value.startsAt).toISOString(), endsAt: new Date(form.value.endsAt).toISOString(), groupSize: form.value.kind === 'GROUP' ? form.value.groupSize : 1, groupDurationSeconds: form.value.kind === 'GROUP' ? form.value.groupDurationSeconds : 0 })
    form.value.title = ''; form.value.description = ''; form.value.price = ''; await load(); message.value = '套餐草稿已保存。核对价格和内容后再发布，发布后不可修改。'
  } catch (e) { message.value = e instanceof Error ? e.message : '创建失败' }
  finally { busy.value = false }
}
async function change(o: api.Offer, status: string) {
  busy.value = true; message.value = ''
  try { await api.changeOffer(o, status); await load() }
  catch (e) { message.value = e instanceof Error ? e.message : '状态修改失败，请刷新' }
  finally { busy.value = false }
}
async function redeem() {
  busy.value = true; message.value = ''
  try { const result = await api.redeem(selected.value, orderId.value, code.value); code.value = ''; await load(); message.value = `订单 ${result.id} 核销成功。` }
  catch (e) { message.value = e instanceof Error ? e.message : '核销失败' }
  finally { busy.value = false }
}
watch(selected, () => { offers.value = []; orders.value = []; code.value = ''; orderId.value = ''; offerPage.value = 1; orderPage.value = 1; void load() })
onMounted(() => loadStores())
</script>
<template><section>
  <div class="section-heading"><h1>套餐与交易管理</h1><button class="secondary" :disabled="busy || !selected" @click="load">刷新工作台</button></div>
  <label for="tradeStore">选择门店</label><select id="tradeStore" v-model="selected" :disabled="busy"><option value="" disabled>请选择门店</option><option v-for="s in stores" :key="s.id" :value="s.id">{{ s.name }} · {{ s.role }}</option></select>
  <PaginationBar :page="storePage" :size="20" :total="storeTotal" :disabled="busy" @change="loadStores" /><p v-if="message" role="status">{{ message }}</p>
  <p v-if="current?.status === 'SUSPENDED'" class="notice">门店已暂停，当前只能查看交易记录。</p>
  <form v-if="publisher" class="workspace-card" @submit.prevent="create"><h2>创建套餐草稿</h2><p class="muted">一单一份。发布后价格、内容、库存总量和拼团规则不可修改；变更请创建新套餐。</p>
    <label for="tradeTitle">套餐标题</label><input id="tradeTitle" v-model.trim="form.title" required maxlength="120" :disabled="busy" />
    <label for="tradeDescription">套餐内容</label><textarea id="tradeDescription" v-model="form.description" required maxlength="2000" rows="3" :disabled="busy" />
    <div class="trade-fields"><div><label for="tradeKind">售卖方式</label><select id="tradeKind" v-model="form.kind" :disabled="busy"><option value="REGULAR">普通套餐</option><option value="SECKILL">限时秒杀</option><option value="GROUP">拼团套餐</option></select></div>
      <div><label for="tradePrice">每份价格（元）</label><input id="tradePrice" v-model="form.price" inputmode="decimal" pattern="[0-9]+(\.[0-9]{1,2})?" required :disabled="busy" /></div>
      <div><label for="tradeStock">库存总量</label><input id="tradeStock" v-model.number="form.stock" type="number" min="1" step="1" required :disabled="busy" /></div>
      <div><label for="tradeStart">开始时间</label><input id="tradeStart" v-model="form.startsAt" type="datetime-local" required :disabled="busy" /></div><div><label for="tradeEnd">结束时间</label><input id="tradeEnd" v-model="form.endsAt" type="datetime-local" required :disabled="busy" /></div>
      <template v-if="form.kind === 'GROUP'"><div><label for="tradeGroupSize">成团人数</label><input id="tradeGroupSize" v-model.number="form.groupSize" type="number" min="2" step="1" required :disabled="busy" /></div><div><label for="tradeDuration">拼团有效期（秒）</label><input id="tradeDuration" v-model.number="form.groupDurationSeconds" type="number" min="1" step="1" required :disabled="busy" /></div></template>
    </div><button :disabled="busy" type="submit">保存套餐草稿</button>
  </form>
  <section v-if="selected"><h2>本店套餐</h2><article v-for="o in offers" :key="o.id" class="workspace-card"><h3>{{ o.title }}</h3><p>{{ api.kindLabels[o.kind] }} · ¥{{ o.price }} · 库存 {{ o.stockAvailable }}/{{ o.stockTotal }} · {{ api.stateLabels[o.status] }}</p><p>{{ o.description }}</p><p>{{ new Date(o.startsAt).toLocaleString() }} 至 {{ new Date(o.endsAt).toLocaleString() }}</p><p v-if="o.kind === 'GROUP'">{{ o.groupSize }} 人成团 · {{ o.groupDurationSeconds }} 秒</p>
    <button v-if="publisher && o.status === 'DRAFT'" :disabled="busy" @click="change(o, 'PUBLISHED')">发布套餐</button><button v-if="publisher && o.status === 'PUBLISHED'" class="secondary" :disabled="busy" @click="change(o, 'OFFLINE')">下架套餐</button></article>
    <p v-if="!offers.length && !busy">暂无套餐。</p><PaginationBar :page="offerPage" :size="20" :total="offerTotal" :disabled="busy" @change="offerPage = $event; load()" />
    <form v-if="current?.status === 'APPROVED'" class="workspace-card" @submit.prevent="redeem"><h2>到店订单核销</h2><label for="tradeOrderId">顾客订单编号</label><input id="tradeOrderId" v-model.trim="orderId" pattern="[1-9][0-9]{0,17}" required :disabled="busy" /><label for="tradeCode">顾客出示的短期核销码</label><input id="tradeCode" v-model.trim="code" autocomplete="off" maxlength="64" required :disabled="busy" /><button type="submit" :disabled="busy">确认到店核销</button></form>
    <h2>本店订单</h2><div class="table-scroll"><table><thead><tr><th>订单编号</th><th>套餐</th><th>金额</th><th>状态</th></tr></thead><tbody><tr v-for="o in orders" :key="o.id"><td>{{ o.id }}</td><td>{{ o.offerSnapshot.title }}</td><td>¥{{ o.amount }}</td><td>{{ api.stateLabels[o.status] }}</td></tr></tbody></table></div><PaginationBar :page="orderPage" :size="20" :total="orderTotal" :disabled="busy" @change="orderPage = $event; load()" />
  </section><p v-if="!stores.length && !busy">暂无所属门店。</p>
</section></template>
