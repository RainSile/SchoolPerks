<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { browseCoupons, claimCoupon, describeRules, type PublicCoupon } from './api'
import { getSchools, type School } from '../campus-user/api'
import { useSessionStore } from '../../campus-stores/session'
import PaginationBar from '../../campus-shared/PaginationBar.vue'
import CampusIcon from '../../campus-shared/CampusIcon.vue'
import CampusOrnament from '../../campus-shared/CampusOrnament.vue'
const session = useSessionStore(), rows = ref<PublicCoupon[]>([]), schools = ref<School[]>([])
const keyword = ref(''), school = ref(''), page = ref(1), total = ref(0), busy = ref(false), message = ref('')
const filter = ref('all'), claimed = ref(''), now = ref(Date.now())
const tearing = ref(''), tearCycle = ref(0)
let tearTimer: ReturnType<typeof setTimeout> | undefined
let alive = true, generation = 0
const ticker = setInterval(() => { now.value = Date.now() }, 30000)
const categories = [{ id: 'all', label: '全部优惠' }, { id: 'AMOUNT_OFF', label: '满减优惠' }, { id: 'DISCOUNT', label: '折扣优惠' }, { id: 'campus', label: '校园专享' }]
const visible = computed(() => rows.value.filter(row => filter.value === 'all' || (filter.value === 'campus' ? row.campusOnly : row.couponType === filter.value)))
const merchantCount = computed(() => new Set(rows.value.map(row => row.merchantId)).size)
const campusCount = computed(() => rows.value.filter(row => row.campusOnly).length)
const currentSchool = computed(() => schools.value.find(s => s.id === school.value)?.name ?? '全部校园')
async function findTopic(value: string) { keyword.value = value; await load() }
function date(value: string) { return new Date(value).toLocaleString('zh-CN', { year: '2-digit', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }) }
function unavailable(row: PublicCoupon) { return row.remainingStock === 0 || now.value < Date.parse(row.validFrom) || now.value >= Date.parse(row.validUntil) }
function state(row: PublicCoupon) { return row.remainingStock === 0 ? '已领完' : now.value < Date.parse(row.validFrom) ? '尚未开始' : now.value >= Date.parse(row.validUntil) ? '已结束' : '领取优惠' }
async function load(next = 1) {
  const current = ++generation; busy.value = true; message.value = ''
  try { const data = await browseCoupons(next, keyword.value.trim(), school.value); if (!alive || current !== generation) return; rows.value = data.items; page.value = data.page; total.value = data.total }
  catch (error) { if (alive && current === generation) message.value = error instanceof Error ? error.message : '优惠加载失败，请重试' }
  finally { if (alive && current === generation) busy.value = false }
}
onMounted(async () => { await load(); try { const data = await getSchools(); if (alive) schools.value = data } catch { /* Optional school filter must not block browsing. */ } })
onBeforeUnmount(() => { alive = false; generation++; clearInterval(ticker); clearTimeout(tearTimer) })
async function claim(row: PublicCoupon) {
  if (busy.value) return
  busy.value = true; message.value = ''
  try {
    await claimCoupon(row.id)
    if (!alive) return
    claimed.value = row.id
    // A receipt animates only after the server confirms the claim. Keep its slot
    // and coupon facts in place, including when refreshed inventory is empty.
    clearTimeout(tearTimer)
    tearCycle.value++
    tearing.value = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? '' : row.id
    tearTimer = setTimeout(() => { tearing.value = '' }, 760)
    await load(page.value)
    if (alive) message.value = '优惠券已放入我的券包，可前往查看并到店使用。'
  }
  catch (error) { if (alive) message.value = error instanceof Error ? error.message : '领取失败，请重试' }
  finally { if (alive) busy.value = false }
}
</script>
<template>
  <section class="coupon-discovery">
    <div class="discovery-intro">
      <CampusOrnament kind="clip" class="intro-fastener" />
      <div class="discovery-copy"><h1>发现校园周边的好优惠</h1><p>从一顿午饭、一杯咖啡开始，<br />把周边生活逛出新发现。</p><div class="discovery-invitation"><CampusIcon name="spark" :size="17" />和同学一起，把日常过得有意思。<CampusOrnament kind="arrow" class="invitation-arrow" /></div></div>
      <nav class="notice-slips" aria-label="课间发现便签">
        <button class="notice-slip coffee-slip" type="button" :disabled="busy" @click="findTopic('咖啡')"><CampusOrnament kind="pin" class="slip-fastener" /><CampusIcon name="coffee" :size="23" /><strong>给课间，加点咖啡香</strong><span>找找咖啡优惠<CampusIcon name="arrow" :size="15" /></span></button>
        <RouterLink class="notice-slip explore-slip" to="/nearby"><CampusOrnament kind="clip" class="slip-fastener" /><CampusIcon name="map" :size="23" /><strong>下课，去周边！</strong><span>发现附近小店<CampusIcon name="arrow" :size="15" /></span></RouterLink>
        <RouterLink class="notice-slip share-slip" to="/community"><CampusOrnament kind="tape" class="slip-fastener" /><CampusIcon name="community" :size="23" /><strong>把发现贴上来</strong><span>看看同学的探店手记<CampusIcon name="arrow" :size="15" /></span></RouterLink>
      </nav>
    </div>
    <div class="discovery-layout">
      <div class="discovery-main">
        <form class="inline-form discovery-filters" @submit.prevent="load()">
          <div><label for="couponKeyword">优惠关键词</label><input id="couponKeyword" v-model="keyword" maxlength="100" placeholder="今天想吃点什么、逛点什么？" /></div>
          <div><label for="couponSchool">校园筛选</label><select id="couponSchool" v-model="school"><option value="">全部校园</option><option v-for="item in schools" :key="item.id" :value="item.id">{{ item.name }}</option></select></div>
          <button :disabled="busy"><CampusIcon name="search" :size="17" />查找优惠</button>
        </form>
        <div class="topic-strip" aria-label="优惠关键词建议"><span><CampusIcon name="search" :size="15" />今天想找</span><button v-for="topic in ['咖啡', '午饭', '打印', '运动']" :key="topic" type="button" :disabled="busy" @click="findTopic(topic)">{{ topic }}<CampusIcon name="arrow" :size="12" /></button><button v-if="keyword" type="button" :disabled="busy" @click="findTopic('')">清除关键词<CampusIcon name="close" :size="12" /></button></div>
        <div class="coupon-toolbar"><div class="coupon-tabs" role="group" aria-label="当前页优惠类型"><button v-for="item in categories" :key="item.id" type="button" :aria-pressed="filter === item.id" :class="{ selected: filter === item.id }" @click="filter = item.id">{{ item.label }}</button></div><span class="result-note">{{ currentSchool }} · 共 {{ total }} 条<span v-if="filter !== 'all'"> · 本页筛选 {{ visible.length }} 条</span></span></div>
        <p v-if="message" role="status" class="notice claim-feedback">{{ message }}</p>
        <div v-if="busy && !rows.length" class="coupon-loading" role="status"><div v-for="i in 4" :key="i"><span /><span /><span /></div><p>正在整理校园周边的优惠…</p></div>
        <p v-if="!busy && !rows.length" class="notice">暂无可领取的优惠，稍后再来看看。</p>
        <p v-if="!busy && rows.length && !visible.length" class="notice">当前页没有这类优惠，试试其他类型或翻到下一页。</p>
        <div v-if="visible.length" class="coupon-tickets" :aria-busy="busy">
          <article v-for="(row, index) in visible" :key="row.id" class="coupon-ticket" :class="{ 'just-claimed': claimed === row.id, 'is-tearing': tearing === row.id }">
            <CampusOrnament v-if="index % 4 === 0" kind="spark" class="ticket-spark" />
            <div class="ticket-value"><span v-if="row.couponType === 'AMOUNT_OFF'" class="ticket-number"><small>¥</small>{{ row.rules.amountOff }}</span><span v-else class="ticket-number">{{ Number((Number(row.rules.discountRate) * 10).toFixed(2)) }}<small>折</small></span><span>{{ row.couponType === 'AMOUNT_OFF' ? '满减券' : '折扣券' }}</span><span class="ticket-stamp">{{ claimed === row.id ? '已领取' : '到店用' }}</span><span v-if="tearing === row.id" :key="tearCycle" class="ticket-tear" aria-hidden="true"><CampusIcon name="check" :size="22" /><strong>已领取</strong><small>装进券包啦</small></span></div>
            <div class="ticket-content"><div class="ticket-merchant"><CampusIcon name="store" :size="14" /><span>{{ row.merchantName }}</span><span v-if="row.campusOnly" class="badge">本校认证专享</span></div><h2>{{ row.title }}</h2><p class="ticket-rule">{{ describeRules(row.couponType, row.rules) }}</p><p class="ticket-validity"><CampusIcon name="clock" :size="13" /><time :datetime="row.validFrom">{{ date(row.validFrom) }}</time><span>至</span><time :datetime="row.validUntil">{{ date(row.validUntil) }}</time></p><details class="ticket-details"><summary>使用说明</summary><p>{{ row.description || '领取后到店出示核销码，请在有效期内使用。' }}</p></details><div class="ticket-actions"><span v-if="claimed === row.id" class="claimed-label"><CampusIcon name="check" :size="14" />已放入券包</span><span v-else>剩余 <strong>{{ row.remainingStock }}</strong> 张</span><button v-if="session.authenticated" :disabled="busy || unavailable(row)" @click="claim(row)">{{ state(row) }}<CampusIcon name="arrow" :size="15" /></button><RouterLink v-else class="text-link" :to="{ path: '/login', query: { redirect: '/app' } }">登录后领取 →</RouterLink></div></div>
          </article>
        </div>
        <PaginationBar :page="page" :size="20" :total="total" :disabled="busy" @change="load" />
      </div>
      <aside class="campus-board" aria-label="校园优惠使用指引">
        <h2 class="board-title"><CampusIcon name="pushpin" :size="21" />校园布告栏<span>有用的小提醒</span></h2>
        <section class="board-note"><CampusOrnament kind="pin" class="board-fastener" /><h2>你的校园生活票夹</h2><p>领取的优惠都会放在券包里。到店之前，先看看使用门槛和有效期。</p><RouterLink class="board-action" to="/account/coupons">我的券包 →</RouterLink><CampusOrnament kind="stamp" class="board-stamp" /></section>
        <section class="board-section"><h2><CampusIcon name="spark" :size="17" />这页有哪些发现</h2><dl class="board-stats"><div><dt>本页优惠</dt><dd>{{ rows.length }}<small>张</small></dd></div><div><dt>来自门店</dt><dd>{{ merchantCount }}<small>家</small></dd></div><div><dt>校园专享</dt><dd>{{ campusCount }}<small>张</small></dd></div></dl><p class="board-caption">根据当前已加载页面统计</p></section>
        <section class="board-section"><h2>领到了，然后呢？</h2><ol class="usage-steps"><li><CampusIcon name="ticket" :size="17" /><span>领一张喜欢的优惠<small>先确认门槛、校园限制与有效期</small></span></li><li><CampusIcon name="user" :size="17" /><span>从券包打开核销码<small>到店时生成，短时凭据妥善保管</small></span></li><li><CampusIcon name="check" :size="17" /><span>交给本店店员核销<small>使用结果以券包状态为准</small></span></li></ol></section>
        <section class="board-section"><h2>校园生活，继续探索</h2><RouterLink class="board-row" to="/nearby"><CampusIcon name="map" :size="18" /><span>看看附近商家<small>从你的位置出发</small></span><CampusIcon name="chevron" :size="15" /></RouterLink><RouterLink class="board-row" to="/community"><CampusIcon name="community" :size="18" /><span>同学的探店分享<small>发现下一站好去处</small></span><CampusIcon name="chevron" :size="15" /></RouterLink><RouterLink class="text-link" to="/account/campus-verification">前往认证</RouterLink><p class="board-caption">校园专享优惠需通过对应学校认证。</p></section>
      </aside>
    </div>
  </section>
</template>
<style scoped>
.discovery-intro {
  display: grid;
  grid-template-columns: minmax(300px, .9fr) minmax(0, 1.1fr);
  align-items: center;
  gap: 32px;
  padding: 25px 28px;
  margin-bottom: 24px;
  position: relative;
  background: #edf3ff;
  border: 1px solid #d8e3f8;
  border-radius: 6px;
}
.intro-fastener { position: absolute; top: -19px; left: 24px; }
.discovery-copy h1 { font-size: 32px; line-height: 1.45; color: var(--campus-blue-strong); margin-bottom: 9px; }
.invitation-arrow { width: 52px; height: 27px; margin-left: 3px; }
.discovery-copy p { color: #455a7a; font-size: 14px; line-height: 1.85; margin: 0; }
.discovery-invitation { display: flex; align-items: center; gap: 7px; color: #71500b; font-size: 13px; margin-top: 14px; }
.notice-slips { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; padding: 10px 0; }
.notice-slip { min-width: 0; min-height: 136px; padding: 17px 12px 12px; position: relative; display: flex; align-items: flex-start; flex-direction: column; gap: 10px; border: 1px solid #e8d08c; border-radius: 2px; background: #fff4cc; color: #71500b; font-size: 13px; text-align: left; transform: rotate(-3deg); transition: transform 220ms var(--campus-ease), background-color 150ms; }
.slip-fastener { position: absolute; top: -12px; right: 13px; }
.notice-slip strong { font-size: 17px; line-height: 1.6; }
.notice-slip > span { display: flex; gap: 4px; align-items: center; font-size: 12px; font-weight: 400; margin-top: auto; }
.notice-slip:hover, .notice-slip:focus-visible { transform: rotate(0) translateY(-3px); }
.notice-slip.coffee-slip:hover:not(:disabled) { background: #ffebac; }
.notice-slip:disabled { background: #fff4cc; color: #71500b; cursor: wait; }
.explore-slip { background: white; border-color: #b9caec; color: var(--campus-blue-strong); transform: rotate(2deg) translateY(-4px); }
.explore-slip .slip-fastener { top: -9px; right: 15px; color: #7795ca; }
.share-slip .slip-fastener { top: -7px; right: calc(50% - 20px); }
.share-slip { background: #fff0e7; border-color: #efcbb5; color: #91421e; transform: rotate(-2deg) translateY(5px); }
.discovery-layout { display: grid; grid-template-columns: minmax(0, 1fr) 250px; gap: 28px; align-items: start; }
.discovery-main { min-width: 0; }
.discovery-filters { margin: 0 0 10px; }
.discovery-filters > div:first-child { flex: 1.7; }
.discovery-filters > div:nth-child(2) { flex: 1; }
.discovery-filters button { padding-inline: 12px; min-height: 40px; }
.topic-strip { display: flex; flex-wrap: wrap; align-items: center; gap: 5px 16px; margin: 0 0 10px; }
.topic-strip > span { display: flex; align-items: center; gap: 5px; color: var(--campus-muted); font-size: 12px; }
.topic-strip button { background: none; color: var(--campus-blue-strong); font-size: 13px; font-weight: 400; padding: 2px 0; min-height: 30px; border: 0; border-radius: 0; }
.topic-strip button:hover:not(:disabled) { background: transparent; color: var(--campus-orange); }
.coupon-toolbar { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; border-bottom: 1px solid var(--campus-line); }
.coupon-tabs { display: flex; gap: 22px; }
.coupon-tabs button { background: none; color: var(--campus-muted); border: 0; border-radius: 0; border-bottom: 2px solid transparent; padding: 9px 0; font-size: 13px; min-height: 42px; font-weight: 500; }
.coupon-tabs button:hover, .coupon-tabs button.selected { color: var(--campus-blue-strong); border-bottom-color: var(--campus-blue); }
.result-note { font-size: 12px; color: var(--campus-muted); padding-bottom: 2px; }
.coupon-tickets { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 22px; background: white; padding: 0 14px; }
.coupon-ticket { display: grid; grid-template-columns: 70px minmax(0, 1fr); gap: 13px; padding: 20px 0 17px; align-items: stretch; border-bottom: 1px dashed #d2dcec; position: relative; }
.ticket-spark { position: absolute; width: 22px; height: 22px; top: 15px; left: 4px; z-index: 3; }
.ticket-value { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; color: var(--campus-blue-strong); background: #eaf1ff; border-right: 1px dashed #9eb8e8; border-radius: 3px; position: relative; padding: 14px 5px; font-size: 12px; isolation: isolate; }
.coupon-ticket:nth-child(4n + 2) .ticket-value { background: #fff4d3; color: #81580d; border-right-color: #d8bd72; }
.ticket-value::before, .ticket-value::after { content: ''; position: absolute; right: -5px; width: 10px; height: 10px; border-radius: 50%; background: white; }
.ticket-value::before { top: -5px; }.ticket-value::after { bottom: -5px; }
.ticket-number { font-size: 28px; font-weight: 750; line-height: 1.2; letter-spacing: -.03em; }
.ticket-number small { font-size: 13px; margin: 0 2px; }
.ticket-stamp { font-size: 12px; padding: 3px 4px; border: 3px double currentColor; border-radius: 50%; line-height: 1.2; margin-top: 7px; transform: rotate(-8deg); opacity: .85; }
.ticket-content { min-width: 0; }
.ticket-content h2 { margin: 6px 0 4px; font-size: 15px; line-height: 1.5; font-weight: 650; }
.ticket-merchant { display: flex; flex-wrap: wrap; gap: 5px; align-items: center; color: var(--campus-muted); font-size: 13px; }
.ticket-merchant .badge { font-size: 12px; padding: 0 4px; }
.ticket-rule { margin: 3px 0; font-size: 13px; color: var(--campus-orange); font-weight: 600; }
.ticket-validity { font-size: 12px; display: flex; align-items: center; flex-wrap: wrap; gap: 3px; color: var(--campus-muted); margin: 7px 0; }
.ticket-details { margin: 5px 0 9px; font-size: 13px; color: var(--campus-muted); }
.ticket-details summary { font-weight: 400; min-height: 25px; }
.ticket-details p { font-size: 13px; color: var(--campus-ink); }
.ticket-actions { display: flex; align-items: center; justify-content: space-between; gap: 6px; font-size: 13px; color: var(--campus-muted); }
.ticket-actions button { min-height: 38px; padding: 6px 9px; font-size: 13px; }
.claimed-label { color: var(--campus-success); display: flex; align-items: center; gap: 3px; font-size: 12px; }
.claim-feedback { margin-block: 12px; }
.board-title { display: flex; align-items: center; gap: 7px; margin: 0 0 16px; font-size: 17px; color: var(--campus-blue-strong); }
.board-title > span { font-size: 12px; color: var(--campus-muted); font-weight: 400; margin-left: auto; }
.board-note { background: var(--campus-yellow-soft); padding: 24px 18px 17px; position: relative; border-bottom: 2px solid #e9dca7; border-radius: 2px; transform: rotate(-1deg); }
.board-note::after { content: ''; position: absolute; bottom: 0; right: 0; width: 16px; height: 16px; background: #f0dfa3; clip-path: polygon(100% 0,100% 100%,0 100%); }
.board-note h2 { font-size: 16px; margin-top: 0; }.board-note p { font-size: 13px; color: #71500b; line-height: 1.8; }
.board-fastener { position: absolute; top: -15px; right: 20px; }
.board-stamp { position: absolute; bottom: 13px; right: 17px; color: #9b6e23; }
.board-action { display: inline-block; font-size: 13px; margin-top: 4px; color: #71500b; font-weight: 650; text-decoration: underline; text-underline-offset: 3px; }
.board-section { padding: 19px 0 17px; border-bottom: 1px solid var(--campus-line); }
.board-section h2 { display: flex; align-items: center; gap: 7px; font-size: 14px; margin: 0 0 12px; }
.board-stats { display: flex; justify-content: space-between; margin: 0; gap: 8px; }
.board-stats dt { font-size: 13px; margin: 0; }.board-stats dd { font-size: 22px; font-weight: 600; margin: 5px 0 0; }
.board-stats small { font-size: 12px; margin-left: 4px; color: var(--campus-muted); }
.board-caption { font-size: 12px; color: var(--campus-muted); margin: 9px 0 0; }
.usage-steps { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 15px; }
.usage-steps li { display: flex; gap: 11px; align-items: flex-start; font-size: 13px; }
.usage-steps .campus-icon { color: var(--campus-blue); margin-top: 2px; }
.usage-steps small, .board-row small { display: block; font-size: 12px; color: var(--campus-muted); margin-top: 3px; }
.board-row { display: flex; gap: 10px; align-items: center; padding: 10px 0; font-size: 13px; }
.board-row > .campus-icon:last-child { margin-left: auto; }.board-row:hover { color: var(--campus-blue); }
.board-section > .text-link { display: inline-block; margin-top: 10px; font-size: 13px; }
.coupon-loading { background: white; padding: 16px; display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
.coupon-loading > div { display: flex; flex-direction: column; gap: 12px; padding: 10px; }
.coupon-loading span { display: block; height: 14px; background: #e7edf6; width: 70%; }.coupon-loading span:first-child { width: 90%; }
.coupon-loading > p { grid-column: 1/-1; font-size: 13px; }
.ticket-tear { position: absolute; inset: 0; background: var(--campus-blue); color: white; display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 7px; padding: 8px 2px; border-radius: 3px; border-right: 1px dashed #d3e1ff; transform-origin: right top; animation: ticket-tear 720ms var(--campus-ease) both; pointer-events: none; z-index: 4; }
.ticket-tear strong { font-size: 14px; }.ticket-tear small { font-size: 11px; }
.is-tearing .ticket-content { animation: ticket-settle 500ms var(--campus-ease); }
@keyframes ticket-tear {
  0%,12% { transform: translate(0,0) rotate(0); opacity: 1; clip-path: inset(0); }
  40% { transform: translate(-5px,4px) rotate(9deg); clip-path: polygon(0 0,96% 0,100% 8%,95% 16%,100% 24%,95% 32%,100% 40%,95% 48%,100% 56%,95% 64%,100% 72%,95% 80%,100% 88%,95% 96%,100% 100%,0 100%); }
  100% { transform: translate(-15px,65px) rotate(18deg); opacity: 0; clip-path: inset(0); }
}
@keyframes ticket-settle { 35% { transform: translateX(2px); } }
@media(max-width: 1160px) {
  .discovery-intro { grid-template-columns: minmax(0, 1fr); gap: 10px; }
  .discovery-copy { display: block; }
  .discovery-copy h1 { font-size: 27px; }.discovery-invitation { max-width: none; margin-top: 10px; }
  .notice-slip { min-height: 108px; gap: 5px; }.notice-slips { gap: 18px; }
  .discovery-layout { grid-template-columns: minmax(0, 1fr) 225px; gap: 20px; }
  .coupon-tickets { grid-template-columns: minmax(0, 1fr); }.coupon-ticket { grid-template-columns: 80px minmax(0, 1fr); }.ticket-content h2 { font-size: 16px; }
}
@media(max-width: 760px) {
  .discovery-layout { grid-template-columns: minmax(0, 1fr); }
  .campus-board { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; }
  .board-title { grid-column: 1/-1; margin-bottom: 0; }.board-note { align-self: start; }
}
@media(max-width: 520px) {
  .discovery-intro { padding: 17px 14px 12px; margin-bottom: 18px; gap: 6px; }
  .discovery-copy { display: block; }.discovery-copy h1 { font-size: 24px; }.discovery-copy p { font-size: 13px; }.discovery-copy p br { display: none; }.discovery-invitation { max-width: none; margin-top: 8px; font-size: 12px; }
  .notice-slips { gap: 8px; padding-top: 13px; }.notice-slip { min-height: 102px; padding: 12px 7px 8px; gap: 5px; }.notice-slip > .campus-icon { width: 20px; height: 20px; }.notice-slip strong { font-size: 12px; }.notice-slip > span { font-size: 11px; line-height: 1.5; }.notice-slip > span .campus-icon { display: none; }
  .discovery-filters { grid-template-columns: minmax(0, 1fr) 110px; gap: 8px; }.discovery-filters input,.discovery-filters select { padding: 7px 8px; }.discovery-filters button { padding-inline: 8px; }
  .topic-strip { gap: 4px 14px; }.topic-strip button { min-height: 32px; }.topic-strip > span { font-size: 12px; }
  .coupon-tabs { width: 100%; justify-content: space-between; gap: 0; }.coupon-tabs button { min-height: 44px; }.result-note { display: block; padding-bottom: 9px; }
  .coupon-tickets { padding-inline: 12px; }.coupon-ticket { grid-template-columns: 64px minmax(0, 1fr); gap: 12px; padding: 18px 0; }.ticket-value { padding-inline: 4px; }.ticket-number { font-size: 26px; }.ticket-content h2 { font-size: 15px; }.ticket-actions button { min-height: 40px; padding: 7px 10px; }.ticket-merchant { font-size: 13px; }
  .campus-board { grid-template-columns: 1fr; gap: 0; }.board-title { margin: 15px 0 20px; }.board-note { margin-top: 0; }.board-section { padding-block: 18px; }.coupon-loading { grid-template-columns: 1fr; }
}
@media(prefers-reduced-motion: reduce) { .ticket-tear,.is-tearing .ticket-content { animation: none; }.notice-slip,.notice-slip:hover,.notice-slip:focus-visible { transform: none; } }
</style>
