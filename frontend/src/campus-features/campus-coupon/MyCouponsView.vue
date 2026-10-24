<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { myClaims, redemptionTicket, describeRules, couponStatus, type CouponClaim, type RedemptionTicket } from './api'
import { myNotifications, readNotification, useLiveNotifications, type Notice } from '../campus-notification/live'
import PaginationBar from '../../campus-shared/PaginationBar.vue'
const rows = ref<CouponClaim[]>([]), notices = ref<Notice[]>([]), page = ref(1), total = ref(0), busy = ref(false), message = ref(''), ticket = ref<RedemptionTicket | null>(null), selected = ref(''), now = ref(Date.now())
const remaining = computed(() => ticket.value ? Math.max(0, Math.ceil((Date.parse(ticket.value.expiresAt) - now.value) / 1000)) : 0)
const qr = computed(() => ticket.value ? `data:image/svg+xml;base64,${btoa(ticket.value.qrCodeSvg)}` : '')
const ticker = setInterval(() => { now.value = Date.now(); if (ticket.value && !remaining.value) { ticket.value = null; selected.value = '' } }, 1000)
onUnmounted(() => { clearInterval(ticker); ticket.value = null })
async function load(next = page.value) { busy.value = true; try { const data = await myClaims(next); rows.value = data.items; page.value = data.page; total.value = data.total; const list = await myNotifications(); notices.value = list.items.filter(n => n.type === 'COUPON_REDEEMED') } catch (error) { message.value = error instanceof Error ? error.message : '券包加载失败' } finally { busy.value = false } }
const live = useLiveNotifications(async notice => { if (notice?.payload.claimId === selected.value) { ticket.value = null; selected.value = '' } await load(); if (notice) message.value = '到店核销成功，券状态已更新。' })
onMounted(async () => { await load(); void live.connect() })
async function show(row: CouponClaim) { busy.value = true; message.value = ''; ticket.value = null; selected.value = ''; try { ticket.value = await redemptionTicket(row.id); now.value = Date.now(); selected.value = row.id } catch (error) { message.value = error instanceof Error ? error.message : '凭据生成失败' } finally { busy.value = false } }
async function mark(notice: Notice) { try { await readNotification(notice.id); notices.value = notices.value.filter(n => n.id !== notice.id) } catch (error) { message.value = error instanceof Error ? error.message : '操作失败' } }
</script>
<template><section><div class="section-heading"><h1>我的券包</h1><button class="secondary" :disabled="busy" @click="load()">刷新券包</button></div><p class="muted">优惠先收好，到店再打开核销码。请留意每张券的使用门槛和有效期。</p><p class="wallet-live muted" role="status">{{ live.state.value }}</p><p v-if="message" role="status">{{ message }}</p>
  <section v-if="notices.some(n => !n.readAt)" class="notice"><h2>核销通知</h2><div v-for="notice in notices.filter(n => !n.readAt)" :key="notice.id"><p>优惠券 {{ notice.payload.claimId }} 已核销 · {{ new Date(notice.createdAt).toLocaleString() }}</p><button class="secondary" @click="mark(notice)">标记已读</button></div></section>
  <div v-if="ticket" class="redemption-card" aria-live="polite"><h2>到店核销二维码</h2><img class="redemption-qr" :src="qr" alt="仅供本次到店核销的短时二维码" /><p>剩余 {{ remaining }} 秒，请仅向本店店员出示。</p><code class="redemption-code">{{ ticket.code }}</code><p class="muted">重新生成后旧码失效。</p><button class="secondary" @click="ticket = null; selected = ''">收起核销码</button></div>
  <p v-if="!busy && !rows.length" class="notice">券包还是空的，先去发现优惠吧。</p><div class="wallet-list spaced"><article v-for="row in rows" :key="row.id" class="wallet-entry"><h2>{{ row.title }}</h2><p>{{ row.merchantName }} · <span class="badge">{{ couponStatus[row.status] ?? row.status }}</span></p><p class="coupon-value">{{ describeRules(row.couponType, row.rules) }}</p><p class="muted">有效至 {{ new Date(row.validUntil).toLocaleString() }}</p><button v-if="row.status === 'AVAILABLE'" :disabled="busy || now < Date.parse(row.validFrom) || now >= Date.parse(row.validUntil)" @click="show(row)">出示核销码</button><p v-if="row.redeemedAt">核销于 {{ new Date(row.redeemedAt).toLocaleString() }}</p></article></div>
  <PaginationBar :page="page" :size="20" :total="total" :disabled="busy" @change="load" /><RouterLink class="text-link" to="/app">继续发现优惠 →</RouterLink>
</section></template>
