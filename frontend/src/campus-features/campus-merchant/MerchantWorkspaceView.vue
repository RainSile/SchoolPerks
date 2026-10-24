<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { myMerchants, getWorkspace, updateMerchant, listMembers, addMember, removeMember, qualificationDownload, statusLabels, type MerchantWorkspace, type Member } from './workflow-api'
import type { DownloadTicket } from '../campus-file/api'
import PaginationBar from '../../campus-shared/PaginationBar.vue'
const route = useRoute(), stores = ref<MerchantWorkspace[]>([]), selected = ref(''), store = ref<MerchantWorkspace | null>(null), members = ref<Member[]>([])
const description = ref(''), hours = ref(''), userId = ref(''), role = ref('CASHIER'), busy = ref(false), message = ref(''), download = ref<DownloadTicket | null>(null)
const page = ref(1), total = ref(0), owner = computed(() => store.value?.role === 'OWNER'), editable = computed(() => owner.value && store.value?.status === 'APPROVED')
let generation = 0
async function loadStore(id: string) {
  const version = ++generation; store.value = null; members.value = []; download.value = null; message.value = ''; busy.value = true
  try {
    const item = await getWorkspace(id)
    const team = item.role === 'OWNER' ? await listMembers(id) : null
    if (version === generation) { store.value = item; description.value = item.description; hours.value = item.businessHours; members.value = team?.items ?? [] }
  } catch (error) { if (version === generation) message.value = error instanceof Error ? error.message : '门店加载失败' }
  finally { if (version === generation) busy.value = false }
}
async function load(next = 1) {
  busy.value = true
  try { const data = await myMerchants(next); stores.value = data.items; page.value = data.page; total.value = data.total
    if (!selected.value && data.items.length) selected.value = typeof route.query.id === 'string' ? route.query.id : data.items[0]!.id
  } catch (error) { message.value = error instanceof Error ? error.message : '加载失败' }
  finally { busy.value = false }
}
onMounted(() => load()); watch(selected, id => { if (id) void loadStore(id) })
async function save() { const current = store.value; if (!current) return; busy.value = true; message.value = ''; try { const value = await updateMerchant(current.id, current.version, description.value, hours.value); if (selected.value === current.id) store.value = value; message.value = '门店资料已保存。' } catch (error) { message.value = error instanceof Error ? error.message : '保存失败' } finally { busy.value = false } }
async function add() { if (!store.value) return; busy.value = true; message.value = ''; try { await addMember(store.value.id, userId.value, role.value); members.value = (await listMembers(store.value.id)).items; userId.value = ''; message.value = '店员已添加。' } catch (error) { message.value = error instanceof Error ? error.message : '添加失败' } finally { busy.value = false } }
async function remove(member: Member) { if (!store.value) return; busy.value = true; message.value = ''; try { await removeMember(store.value.id, member.userId); members.value = (await listMembers(store.value.id)).items } catch (error) { message.value = error instanceof Error ? error.message : '移除失败' } finally { busy.value = false } }
async function signDownload() { if (!store.value) return; busy.value = true; message.value = ''; try { download.value = await qualificationDownload(store.value.id) } catch (error) { message.value = error instanceof Error ? error.message : '无法获取资质凭据' } finally { busy.value = false } }
</script>
<template><section class="merchant-workspace">
  <div class="section-heading"><div><h1>商家工作台</h1><p class="muted">打理门店资料，和店员一起照顾好到店的同学。</p></div><RouterLink class="text-link" to="/merchant/applications">我的入驻申请 →</RouterLink></div>
  <div class="merchant-shortcuts"><RouterLink to="/merchant/coupons"><strong>优惠活动管理 →</strong><span>草稿、送审与发布</span></RouterLink><RouterLink to="/merchant/redemptions"><strong>到店核销 →</strong><span>核验同学的短时凭据</span></RouterLink><RouterLink to="/merchant/trade"><strong>套餐与交易管理 →</strong><span>套餐、拼团和订单</span></RouterLink><RouterLink to="/merchant/location"><strong>设置门店位置 →</strong><span>让附近的同学找到你</span></RouterLink></div>
  <div class="merchant-selector"><label for="selectedMerchant">选择门店</label><select id="selectedMerchant" v-model="selected" :disabled="busy"><option value="" disabled>请选择门店</option><option v-for="item in stores" :key="item.id" :value="item.id">{{ item.name }} · {{ statusLabels[item.status] ?? item.status }}</option></select></div>
  <PaginationBar v-if="total > 20" :page="page" :size="20" :total="total" :disabled="busy" @change="load" />
  <p v-if="message" role="status" class="notice">{{ message }}</p>
  <div v-if="store" class="merchant-editor">
    <section class="merchant-details"><div class="section-heading"><h2>{{ store.name }}</h2><button class="secondary" :disabled="busy" @click="loadStore(store.id)">刷新门店</button></div><p class="muted">{{ store.address }} · <span class="badge">{{ statusLabels[store.status] ?? store.status }}</span> · {{ { OWNER: '店主', MANAGER: '门店经理', CASHIER: '核销店员' }[store.role] ?? store.role }}</p>
      <p v-if="store.status === 'SUSPENDED'" class="notice">门店已暂停，当前仅可查看资料。</p>
      <form @submit.prevent="save"><label for="storeDescription">门店介绍</label><textarea id="storeDescription" v-model="description" maxlength="1000" rows="3" :disabled="busy || !editable" /><label for="businessHours">营业时间</label><input id="businessHours" v-model="hours" maxlength="255" :disabled="busy || !editable" /><button v-if="owner" type="submit" :disabled="busy || !editable">保存门店资料</button></form>
      <section v-if="owner" class="merchant-qualification"><h2>本店私有资质</h2><p class="muted">资料仅通过短时链接访问，请妥善保管。</p><button class="secondary" :disabled="busy" @click="signDownload">获取资质下载凭据</button><p v-if="download"><a class="text-link" :href="download.url" target="_blank" rel="noopener noreferrer">打开短时资质链接</a> · 有效至 {{ new Date(download.expiresAt).toLocaleTimeString() }}</p></section>
    </section>
    <section v-if="owner" class="merchant-team"><h2>店员管理</h2><p class="muted">让店员在个人资料中查看用户编号。添加店员不会授予平台管理权限。</p>
      <form class="inline-form" @submit.prevent="add"><div><label for="memberUserId">店员用户编号</label><input id="memberUserId" v-model.trim="userId" required pattern="[1-9][0-9]{0,17}" maxlength="18" :disabled="busy || !editable" /></div><div><label for="memberRole">店员角色</label><select id="memberRole" v-model="role" :disabled="busy || !editable"><option value="CASHIER">核销店员</option><option value="MANAGER">门店经理</option></select></div><button type="submit" :disabled="busy || !editable">添加店员</button></form>
      <div class="table-scroll"><table><thead><tr><th>用户编号</th><th>昵称</th><th>角色</th><th>操作</th></tr></thead><tbody><tr v-for="member in members" :key="member.userId"><td>{{ member.userId }}</td><td>{{ member.nickname }}</td><td>{{ member.role }}</td><td><button v-if="member.role !== 'OWNER'" class="secondary" :disabled="busy || !editable" @click="remove(member)">移除店员</button><span v-else>店主不可移除</span></td></tr></tbody></table></div>
    </section>
  </div><p v-else-if="!busy && !stores.length" class="notice">暂无所属门店。</p>
</section></template>
