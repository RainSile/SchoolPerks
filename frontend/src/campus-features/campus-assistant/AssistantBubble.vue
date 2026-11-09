<script setup lang="ts">
import CampusIcon from '../../campus-shared/CampusIcon.vue'
import AssistantMarkdown from './AssistantMarkdown.vue'
import { assistantStream } from './assistant-stream'
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useSessionStore } from '../../campus-stores/session'
import { request, ApiError } from '../../campus-shared/http'
import { transport } from '../../campus-shared/transport'
withDefaults(defineProps<{ placement?: 'left' | 'right' }>(), { placement: 'right' })
interface Capabilities { enabled: boolean; accountType: string | null; maxMessageLength: number; timeoutSeconds: number; tools: string[] }
interface Message { id: string; conversationId: string; status: string; question: string; reply: string | null; tools: string[]; sources: { type: string; id: string; title: string; excerpt: string }[]; errorCode: string | null }
const session = useSessionStore(), open = ref(false), busy = ref(false), message = ref(''), input = ref(''), conversation = ref('')
const history = ref<Message[]>([]), capabilities = ref<Capabilities | null>(null), editor = ref<HTMLTextAreaElement | null>(null), timeline = ref<HTMLElement | null>(null)
const allowWrites = ref(false), draft = ref(''), draftQuestion = ref(''), step = ref(''), interrupted = ref(false)
const identity = computed(() => session.profile ? `${session.profile.id}:${session.profile.accountType}` : '')
const label = computed(() => ({ USER: '校园生活助手', MERCHANT: '门店经营助手', ADMIN: '平台管理助手' })[session.profile?.accountType ?? 'USER'])
const welcome = computed(() => ({ USER: ['把问题交给我，', '把时间留给校园生活。'], MERCHANT: ['店里的大小事，', '一起有条理地处理。'], ADMIN: ['让待办更清楚，', '让处理更有条理。'] })[session.profile?.accountType ?? 'USER'])
const suggestions = computed(() => ({
  USER: [{ icon: 'ticket', title: '今天有什么优惠？', question: '帮我找找当前可领取的优惠券，说明使用条件。' }, { icon: 'store', title: '逛逛校园周边', question: '帮我查找校园周边的商家。' }, { icon: 'clock', title: '看看我的订单', question: '查询我的订单和当前处理状态。' }],
  MERCHANT: [{ icon: 'store', title: '我的门店概况', question: '查询我有权管理的门店信息。' }, { icon: 'ticket', title: '整理店内优惠', question: '查询我的门店优惠券及其发布状态。' }, { icon: 'clock', title: '查看经营订单', question: '查询我的门店订单和当前处理状态。' }],
  ADMIN: [{ icon: 'community', title: '待审核的社区内容', question: '查询待审核的社区帖子和评论。' }, { icon: 'store', title: '商家入驻进度', question: '查询待审核的商家入驻申请。' }, { icon: 'ticket', title: '优惠审核情况', question: '查询待审核的优惠券。' }],
})[session.profile?.accountType ?? 'USER'])
async function chooseQuestion(question: string) { input.value = question; await nextTick(); editor.value?.focus() }
function enterToSend(event: KeyboardEvent) { if (!event.isComposing) { event.preventDefault(); void send() } }
let epoch = 0, controller: AbortController | null = null, pending: { text: string; key: string; allowWrites: boolean } | null = null
const storageKey = (id: string) => `campus.assistant.conversation:${id}`
async function scroll() { await nextTick(); timeline.value?.scrollTo({ top: timeline.value.scrollHeight, behavior: 'smooth' }) }
async function load() {
  const generation = epoch
  try {
    const cap = session.authenticated ? await request<Capabilities>('/assistant/capabilities') : await transport<Capabilities>('/assistant/capabilities')
    if (generation !== epoch) return; capabilities.value = cap
    if (conversation.value && session.authenticated && cap.enabled) {
      try { const rows = await request<Message[]>(`/assistant/conversations/${conversation.value}/messages`); if (generation === epoch) { history.value = rows; await scroll() } }
      catch (e) { if (e instanceof ApiError && e.problem.status === 404 && generation === epoch) { conversation.value = ''; sessionStorage.removeItem(storageKey(identity.value)) } else throw e }
    }
  } catch (e) { if (generation === epoch) message.value = e instanceof Error ? e.message : '暂时无法连接助手' }
}
watch(identity, (id, previous) => {
  epoch++; controller?.abort(); controller = null; busy.value = false; history.value = []; input.value = ''; message.value = ''; pending = null
  allowWrites.value = false; draft.value = ''; draftQuestion.value = ''; step.value = ''; interrupted.value = false
  if (previous) sessionStorage.removeItem(storageKey(previous))
  conversation.value = id ? sessionStorage.getItem(storageKey(id)) ?? '' : ''; void load()
}, { immediate: true })
watch(open, async value => { if (value) { await load(); await nextTick(); editor.value?.focus(); await scroll() } })
async function send() {
  const text = input.value.trim(), cap = capabilities.value
  if (busy.value || !text || !session.authenticated || !cap?.enabled) return
  const generation = epoch, id = identity.value; busy.value = true; message.value = ''; controller = new AbortController()
  draft.value = ''; draftQuestion.value = text; interrupted.value = false; step.value = '正在核对信息…'
  if (!pending || pending.text !== text || pending.allowWrites !== allowWrites.value) pending = { text, key: crypto.randomUUID(), allowWrites: allowWrites.value }
  const current = pending, signal = AbortSignal.any([controller.signal, AbortSignal.timeout(Math.max(cap.timeoutSeconds, 10) * 1000)])
  try {
    if (!conversation.value) { const c = await request<{ id: string }>('/assistant/conversations', { method: 'POST', body: '{}', signal }); if (generation !== epoch) return; conversation.value = c.id; sessionStorage.setItem(storageKey(id), c.id) }
    let completed: Message | null = null
    await assistantStream(`/assistant/conversations/${conversation.value}/message-streams`, current.key, { message: text, allowWrites: current.allowWrites }, signal, (event, data) => {
      if (generation !== epoch) return
      if (event === 'round') { draft.value = ''; step.value = '正在组织回答…' }
      if (event === 'delta') { draft.value += (data as { text: string }).text; step.value = '正在输出…'; void scroll() }
      if (event === 'tool') { draft.value = ''; step.value = '正在调用业务接口并核实结果…' }
      if (event === 'completed') completed = data as Message
    })
    let row: Message = completed!
    if (!row) throw new Error('未收到处理结果，请重试同一问题以查询状态。')
    while (['PENDING', 'RUNNING'].includes(row.status)) {
      if (signal.aborted || generation !== epoch) return
      await new Promise(resolve => setTimeout(resolve, 1000))
      row = await request<Message>(`/assistant/conversations/${conversation.value}/messages/${row.id}`, { signal })
    }
    if (generation !== epoch) return
    if (row.status !== 'COMPLETED') throw new Error('本次回答未完成，可重试同一问题。')
    const index = history.value.findIndex(m => m.id === row.id); if (index < 0) history.value.push(row); else history.value[index] = row
    input.value = ''; pending = null; draft.value = ''; draftQuestion.value = ''; allowWrites.value = false; await scroll()
  } catch (e) { if (generation === epoch) { interrupted.value = true; message.value = signal.aborted ? '输出已停止。已提交的业务操作不会撤回，请先查询结果，再重试同一问题。' : e instanceof Error ? e.message : '提问失败，请重试' } }
  finally { if (generation === epoch) { busy.value = false; controller = null } }
}
function closeOnEscape(event: KeyboardEvent) { if (event.key === 'Escape') open.value = false }
window.addEventListener('keydown', closeOnEscape)
onBeforeUnmount(() => { controller?.abort(); window.removeEventListener('keydown', closeOnEscape) })
</script>
<template>
  <div class="assistant-widget" :class="{ 'assistant-widget--left': placement === 'left' }">
    <Transition name="assistant-panel"><section v-if="open" id="campus-assistant-panel" class="assistant-panel" role="dialog" :aria-label="label" aria-modal="false">
      <header class="assistant-header"><span class="assistant-emblem" aria-hidden="true"><CampusIcon name="spark" :size="24" /></span><div class="assistant-heading"><span class="assistant-kicker">布告栏 · 随身问答</span><strong>{{ label }}</strong><small>仅处理当前账号有权访问的信息</small></div><button type="button" aria-label="收起助手" @click="open = false"><CampusIcon name="close" :size="19" /></button></header>
      <div ref="timeline" class="assistant-timeline" aria-live="polite">
        <div v-if="!history.length && !draftQuestion" class="assistant-welcome"><span class="assistant-welcome-tag"><CampusIcon name="pushpin" :size="14" />随时来问</span><h2>{{ welcome[0] }}<br />{{ welcome[1] }}</h2><p>查信息、理进度，或一起办点事。<br />从下面一个小问题开始吧。</p><div class="assistant-suggestions"><button v-for="item in suggestions" :key="item.title" type="button" :disabled="!session.authenticated || !capabilities?.enabled || busy" @click="chooseQuestion(item.question)"><CampusIcon :name="item.icon" :size="18" /><span>{{ item.title }}</span><CampusIcon name="arrow" :size="15" /></button></div><span class="assistant-welcome-footnote"><CampusIcon name="book" :size="14" />回答供参考，业务结果以系统记录为准</span></div>
        <template v-for="row in history" :key="row.id"><div class="assistant-question">{{ row.question }}</div><span class="assistant-reply-label"><CampusIcon name="spark" :size="13" />{{ label }}</span><div class="assistant-answer"><AssistantMarkdown :text="row.reply ?? ''" /></div><div v-if="row.sources.length" class="assistant-sources"><details v-for="source in row.sources" :key="source.type + source.id"><summary>参考：{{ source.title }}</summary><p>{{ source.excerpt }}</p></details></div></template>
        <div v-if="draftQuestion" class="assistant-question">{{ draftQuestion }}</div>
        <div v-if="draft" class="assistant-answer assistant-live" :class="{ 'is-streaming': busy }"><AssistantMarkdown :text="draft" /><small v-if="interrupted">输出未完成</small></div>
        <p v-if="busy" class="assistant-thinking"><span aria-hidden="true">●</span> {{ step }}</p>
      </div>
      <p v-if="message" class="assistant-notice" role="status">{{ message }}</p>
      <p v-if="!session.authenticated" class="assistant-notice">登录后即可使用助手。<RouterLink to="/login">前往登录</RouterLink></p>
      <p v-else-if="!capabilities?.enabled" class="assistant-notice">助手暂不可用，请稍后重试。</p>
      <form v-else class="assistant-form" @submit.prevent="send"><label class="assistant-consent"><input v-model="allowWrites" type="checkbox" :disabled="busy" /><span>允许本次业务操作<small>领取、创建、修改等；仅对本次提问有效</small></span></label><div class="assistant-compose"><label class="sr-only" for="assistant-input">向助手提问</label><textarea id="assistant-input" ref="editor" v-model="input" rows="2" :maxlength="capabilities.maxMessageLength" :disabled="busy" placeholder="想找什么优惠，或需要我帮什么忙？" @keydown.enter.exact="enterToSend" /><button v-if="busy" type="button" class="assistant-send" @click="controller?.abort()"><span class="assistant-stop-icon" aria-hidden="true" />停止</button><button v-else class="assistant-send" :disabled="!input.trim()">发送<CampusIcon name="arrow" :size="16" /></button></div><div class="assistant-compose-hint"><span>Enter 发送 · Shift + Enter 换行</span><span>{{ input.length }}/{{ capabilities.maxMessageLength }}</span></div></form>
    </section></Transition>
    <button class="assistant-launcher" type="button" :aria-expanded="open" aria-controls="campus-assistant-panel" :aria-label="open ? '收起智能助手' : '打开智能助手'" @click="open = !open"><CampusIcon :name="open ? 'close' : 'spark'" :size="20" /><span>{{ open ? '收起助手' : '智能助手' }}</span></button>
  </div>
</template>
<style scoped>
@property --assistant-flow-angle { syntax:'<angle>';initial-value:0deg;inherits:false; }
.assistant-widget { position: fixed; right: 20px; bottom: max(20px,env(safe-area-inset-bottom)); z-index: 1000; display: flex; align-items: flex-end; flex-direction: column; gap: 12px; max-width: calc(100vw - 32px); }
.assistant-widget--left { right: auto; left: 20px; align-items: flex-start; }
.assistant-launcher { border-radius: 30px; padding: 11px 18px; display: flex; gap: 8px; align-items: center; min-height: 46px;box-shadow:0 4px 18px #2055ce30;border:1px solid #ffffff80; }
.assistant-launcher:hover:not(:disabled) { transform: translateY(-2px); }
.assistant-panel { width: 416px; max-width: 100%; height: min(680px,calc(100dvh - 104px)); display: flex; flex-direction: column; background: white; border:1px solid #d8e3f5;border-radius: 22px; box-shadow: 0 16px 60px #23365326; overflow: hidden; }
.assistant-panel .assistant-header { display:flex;gap:12px;align-items:center;background:linear-gradient(115deg,#edf3ff,#f8faff);padding:20px 18px 18px;border-bottom:1px solid var(--campus-line);flex-shrink:0;position:relative; }
.assistant-header::after { content:'';position:absolute;bottom:-1px;left:20px;width:46px;height:3px;border-radius:3px;background:var(--campus-yellow); }
.assistant-emblem { width:44px;height:44px;display:grid;place-items:center;background:white;border:1px solid #dbe5fa;box-shadow:0 3px 8px #2055ce0a;border-radius:14px;color:var(--campus-blue);flex:none;transform:rotate(-6deg); }
.assistant-heading { min-width:0;flex:1; }.assistant-kicker { display:block;color:var(--campus-blue);font-size:10px;letter-spacing:.12em;margin-bottom:3px; }.assistant-heading strong { display:block;font-size:17px;font-family:var(--campus-font-display);color:var(--campus-ink); }
.assistant-panel header small { display: block; color: var(--campus-muted); font-size: 11px; margin-top: 3px; }
.assistant-panel header button { background: transparent; color: var(--campus-muted); padding: 6px; min-height: 32px;border-radius:50%;margin:0;flex:none; }.assistant-panel header button:hover { background:#dfe8fc;color:var(--campus-blue); }
.assistant-timeline { flex: 1; overflow: auto;overscroll-behavior:contain;scrollbar-width:thin; min-height: 0; padding: 20px 18px; display: flex; flex-direction: column;align-items:stretch; gap: 12px;background:linear-gradient(#f8faff80,white 180px); }
.assistant-welcome { padding:4px 2px 12px; }.assistant-welcome-tag { display:inline-flex;gap:5px;align-items:center;font-size:11px;color:#805b12;background:var(--campus-yellow-soft);border:1px solid #f3dfa3;padding:3px 8px;border-radius:5px;transform:rotate(-3deg); }.assistant-welcome h2 { font-family:var(--campus-font-display);font-size:23px;line-height:1.55;margin:15px 0 8px;letter-spacing:-.025em; }.assistant-welcome p { font-size:13px;color:var(--campus-muted);line-height:1.8;margin:0 0 18px; }
.assistant-suggestions { display:grid;gap:8px; }.assistant-suggestions button { margin:0;padding:11px 12px;border:1px solid var(--campus-line);background:white;color:var(--campus-ink);font-size:13px;font-weight:500;text-align:left;justify-content:flex-start;border-radius:10px; }.assistant-suggestions button > .campus-icon:first-child { color:var(--campus-blue); }.assistant-suggestions button > :last-child { margin-left:auto;color:var(--campus-muted); }.assistant-suggestions button:hover:not(:disabled) { border-color:#b9cdf5;background:var(--campus-blue-soft);transform:translateX(2px); }.assistant-suggestions button:disabled { opacity:.65; }
.assistant-welcome-footnote { display:flex;align-items:center;gap:5px;color:var(--campus-muted);font-size:10px;margin-top:16px; }.assistant-reply-label { display:flex;align-items:center;gap:5px;color:var(--campus-muted);font-size:10px;margin:3px 0 -7px; }
.assistant-question,.assistant-answer { padding: 11px 13px; white-space: pre-wrap; overflow-wrap: anywhere; border-radius: 9px; font-size: 14px; line-height: 1.75; }
.assistant-question { background: var(--campus-blue);color:white; align-self: flex-end; max-width: 90%;border-radius:14px 14px 4px 14px; }
.assistant-answer { background: var(--campus-ground);border:1px solid #e9edf4; align-self: flex-start; max-width: 100%; white-space: normal; min-width: 0;border-radius:4px 14px 14px 14px; }
.assistant-live.is-streaming::after { content: ''; display: inline-block; height: 14px; width: 2px; background: var(--campus-blue); animation: assistant-breathe 1s ease-in-out infinite; }
.assistant-live small { display: block; color: var(--campus-muted); }
.assistant-sources { font-size: 12px; line-height: 1.65; overflow-wrap: anywhere; color: var(--campus-muted); }
.assistant-sources p { white-space: pre-wrap; }
.assistant-panel .assistant-form { padding: 14px; margin: 0; display: flex; flex-direction: column; gap: 10px; border-top: 1px solid var(--campus-line);background:white;flex-shrink:0; }
.assistant-compose { display: flex;gap:8px; align-items: flex-end; width: 100%;padding:8px;border:1.5px solid transparent;border-radius:14px;background:linear-gradient(var(--campus-ground),var(--campus-ground)) padding-box,conic-gradient(from var(--assistant-flow-angle),#4285f4,#6870e8,#a17bf2,#65b7ef,#4285f4) border-box;box-shadow:none; }.assistant-compose:focus-within { animation:assistant-border-flow 7s linear infinite; }
.assistant-panel .assistant-consent { display: flex; align-items: center; gap: 9px; font-size: 12px; color: var(--campus-ink);margin:0;cursor:pointer;font-weight:500; }.assistant-consent small { display:block;color:var(--campus-muted);font-size:10px;font-weight:400;margin-top:1px; }
.assistant-consent input { width: 16px; height: 16px; margin: 0;flex:none; }
.assistant-panel textarea { resize: none; min-height: 60px; margin: 0; flex: 1; min-width: 0; font-size: 13px;line-height:1.65;background:transparent;border:0;border-radius:6px;padding:5px;box-shadow:none; }.assistant-panel textarea:focus-visible { outline:none; }.assistant-panel textarea:disabled { background:transparent; }
.assistant-panel .assistant-send { padding:8px 10px;margin:0;font-size:12px;border-radius:9px;min-height:36px;flex:none; }.assistant-stop-icon { width:9px;height:9px;border-radius:2px;background:currentColor; }.assistant-compose-hint { display:flex;justify-content:space-between;gap:8px;font-size:10px;color:var(--campus-muted);padding:0 2px; }
.assistant-notice { padding: 9px 16px; font-size: 12px; margin: 0;border-top:1px solid #f1dfae;background:var(--campus-yellow-soft);flex-shrink:0; }.assistant-notice a { color:var(--campus-blue);text-decoration:underline; }
.assistant-thinking { font-size: 13px; color: var(--campus-muted); }
.assistant-thinking span { display: inline-block; animation: assistant-breathe 1.2s ease-in-out infinite; }
.assistant-panel-enter-active,.assistant-panel-leave-active { transition: opacity .2s var(--campus-ease), transform .2s var(--campus-ease); }
.assistant-panel-enter-from,.assistant-panel-leave-to { opacity: 0; transform: translateY(12px) scale(.98); transform-origin: bottom right; }
@keyframes assistant-breathe { 50% { opacity: .3; } }
@keyframes assistant-border-flow { to { --assistant-flow-angle:360deg; } }
.assistant-widget--left .assistant-panel-enter-from,.assistant-widget--left .assistant-panel-leave-to { transform-origin: bottom left; }
@media(max-width:520px) { .assistant-widget { right: 12px; bottom:max(12px,env(safe-area-inset-bottom));max-width:calc(100vw - 24px); }.assistant-widget--left { right: auto; left: 12px; }.assistant-launcher { padding: 12px; width: 46px; height: 46px; }.assistant-launcher > span { display: none; }.assistant-panel { width: calc(100vw - 24px); height: min(680px,calc(100dvh - 94px - env(safe-area-inset-bottom)));border-radius:18px; }.assistant-panel .assistant-header { padding:16px 14px;gap:9px; }.assistant-timeline { padding:16px 14px; }.assistant-heading strong { font-size:16px; }.assistant-emblem { width:38px;height:38px; }.assistant-panel .assistant-form { padding:12px; }.assistant-welcome h2 { font-size:21px; } }
@media(prefers-reduced-motion:reduce) { .assistant-widget * { transition:none!important;animation:none!important; }.assistant-panel-enter-active,.assistant-panel-leave-active { transition: none; } }
@media(forced-colors:active) { .assistant-compose { border:1px solid ButtonText;background:Canvas; }.assistant-compose:focus-within { outline:2px solid Highlight; } }
</style>
