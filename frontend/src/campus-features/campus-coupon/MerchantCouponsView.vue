<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { myMerchants, type MerchantWorkspace } from '../campus-merchant/workflow-api'
import { merchantCoupons, createCoupon, updateCoupon, submitCouponReview, changeCouponStatus, describeRules, couponStatus, reviewStatus, type MerchantCoupon, type CouponInput } from './api'
import PaginationBar from '../../campus-shared/PaginationBar.vue'
const route = useRoute(), stores = ref<MerchantWorkspace[]>([]), merchant = ref(''), storePage = ref(1), storeTotal = ref(0), rows = ref<MerchantCoupon[]>([]), page = ref(1), total = ref(0), busy = ref(false), message = ref(''), editing = ref<MerchantCoupon | null>(null), reason = ref('')
const current = computed(() => stores.value.find(s => s.id === merchant.value)), canManage = computed(() => current.value?.status === 'APPROVED' && ['OWNER', 'MANAGER'].includes(current.value.role))
const form = reactive({ title: '', couponType: 'AMOUNT_OFF', threshold: 0, amountOff: 0, discountRate: 0, totalStock: 1, validFrom: '', validUntil: '', campusOnly: false, description: '' })
async function load(next = 1) { if (!merchant.value) return; busy.value = true; message.value = ''; try { const data = await merchantCoupons(merchant.value, next); rows.value = data.items; page.value = data.page; total.value = data.total } catch (error) { message.value = error instanceof Error ? error.message : '加载失败' } finally { busy.value = false } }
async function loadStores(next = 1) { busy.value = true; try { const data = await myMerchants(next); stores.value = data.items; storePage.value = data.page; storeTotal.value = data.total; const requested = typeof route.query.id === 'string' ? route.query.id : ''; merchant.value = data.items.find(s => s.id === requested)?.id ?? data.items[0]?.id ?? ''; editing.value = null; if (merchant.value) await load() } catch (error) { message.value = error instanceof Error ? error.message : '门店加载失败' } finally { busy.value = false } }
onMounted(() => loadStores())
const localDate = (value: string) => { const date = new Date(value); return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0,16) }
function edit(row: MerchantCoupon) { editing.value = row; Object.assign(form, row, { threshold: row.rules.threshold, amountOff: row.rules.amountOff ?? 0, discountRate: row.rules.discountRate ?? 0, validFrom: localDate(row.validFrom), validUntil: localDate(row.validUntil) }); message.value = '' }
async function save() { busy.value = true; message.value = ''; try { const content: CouponInput = { title: form.title.trim(), couponType: form.couponType, rules: { threshold: form.threshold, amountOff: form.couponType === 'AMOUNT_OFF' ? form.amountOff : null, discountRate: form.couponType === 'DISCOUNT' ? form.discountRate : null }, totalStock: form.totalStock, validFrom: new Date(form.validFrom).toISOString(), validUntil: new Date(form.validUntil).toISOString(), campusOnly: form.campusOnly, description: form.description }; if (editing.value) await updateCoupon(editing.value.id, editing.value.version, content); else await createCoupon(merchant.value, content); editing.value = null; form.title = ''; await load(); message.value = '优惠草稿已保存，请送交内容审核。' } catch (error) { message.value = error instanceof Error ? error.message : '保存失败' } finally { busy.value = false } }
async function submit(row: MerchantCoupon) { busy.value = true; try { await submitCouponReview(row.id, row.version); await load(page.value); message.value = '已提交内容审核。' } catch (error) { message.value = error instanceof Error ? error.message : '送审失败' } finally { busy.value = false } }
async function change(row: MerchantCoupon, status: string) { busy.value = true; try { await changeCouponStatus(row.id, row.version, status, reason.value.trim()); await load(page.value); reason.value = ''; message.value = status === 'PUBLISHED' ? '优惠已发布。' : '优惠已下架，已领取的券在有效期内仍可使用。' } catch (error) { message.value = error instanceof Error ? error.message : '操作失败' } finally { busy.value = false } }
</script>
<template><section><RouterLink class="text-link" to="/merchant">← 返回商家工作台</RouterLink><h1>优惠活动管理</h1><label for="couponMerchant">选择经营门店</label><select id="couponMerchant" v-model="merchant" :disabled="busy" @change="editing = null; load()"><option v-for="store in stores" :key="store.id" :value="store.id">{{ store.name }}</option></select><PaginationBar :page="storePage" :size="20" :total="storeTotal" :disabled="busy" @change="loadStores" />
  <p v-if="!canManage && merchant" class="notice">当前门店状态或角色仅允许查看活动。</p><p v-if="message" role="status">{{ message }}</p>
  <article v-if="canManage"><h2>{{ editing ? '修改优惠草稿' : '创建优惠草稿' }}</h2><form class="coupon-form" @submit.prevent="save"><fieldset class="plain-fieldset" :disabled="busy"><label for="offerTitle">优惠标题</label><input id="offerTitle" v-model="form.title" required maxlength="160" /><label for="offerType">优惠类型</label><select id="offerType" v-model="form.couponType"><option value="AMOUNT_OFF">满减</option><option value="DISCOUNT">折扣</option></select><div class="field-grid"><div><label for="threshold">最低消费金额（元）</label><input id="threshold" v-model.number="form.threshold" type="number" required min="0" step="0.01" /></div><div v-if="form.couponType === 'AMOUNT_OFF'"><label for="amountOff">减免金额（元）</label><input id="amountOff" v-model.number="form.amountOff" type="number" min="0.01" step="0.01" required /></div><div v-else><label for="discountRate">支付比例（0.8 即八折）</label><input id="discountRate" v-model.number="form.discountRate" type="number" min="0.0001" max="0.9999" step="0.0001" required /></div><div><label for="offerStock">发放数量</label><input id="offerStock" v-model.number="form.totalStock" type="number" required min="1" step="1" /></div><div><label for="offerFrom">有效期开始</label><input id="offerFrom" v-model="form.validFrom" type="datetime-local" required /></div><div><label for="offerUntil">有效期结束</label><input id="offerUntil" v-model="form.validUntil" type="datetime-local" required /></div></div><label for="offerDescription">活动说明</label><textarea id="offerDescription" v-model="form.description" maxlength="1000" rows="3" /><label class="checkbox-label"><input v-model="form.campusOnly" type="checkbox" />仅限本校已认证用户领取</label><div class="button-row"><button type="submit">保存优惠草稿</button><button v-if="editing" type="button" class="secondary" @click="editing = null">取消编辑</button></div></fieldset></form></article>
  <section class="spaced">
    <h2>本店活动</h2>
    <label v-if="canManage" for="couponActionReason">发布或下架说明</label>
    <textarea v-if="canManage" id="couponActionReason" v-model="reason" maxlength="255" rows="2" />
    <div class="record-grid spaced">
      <article v-for="row in rows" :key="row.id">
        <h2>{{ row.title }}</h2>
        <p class="coupon-value">{{ describeRules(row.couponType, row.rules) }}</p>
        <p>{{ couponStatus[row.status] }} · {{ reviewStatus[row.reviewStatus] }}</p>
        <p v-if="row.reviewReason" class="notice">审核意见：{{ row.reviewReason }}</p>
        <p>剩余 {{ row.remainingStock }} / {{ row.totalStock }} 张</p>
        <p class="muted">有效至 {{ new Date(row.validUntil).toLocaleString() }}</p>
        <div v-if="canManage" class="button-row">
          <template v-if="row.status === 'DRAFT' && row.reviewStatus !== 'PENDING'">
            <button class="secondary" :disabled="busy" @click="edit(row)">编辑草稿</button>
            <button v-if="row.reviewStatus !== 'APPROVED'" :disabled="busy" @click="submit(row)">提交优惠审核</button>
            <button v-else :disabled="busy || !reason.trim()" @click="change(row, 'PUBLISHED')">发布优惠</button>
          </template>
          <button v-if="row.status === 'PUBLISHED'" class="secondary" :disabled="busy || !reason.trim()" @click="change(row, 'OFFLINE')">下架优惠</button>
        </div>
      </article>
    </div>
    <PaginationBar :page="page" :size="20" :total="total" :disabled="busy" @change="load" />
  </section>
</section></template>
