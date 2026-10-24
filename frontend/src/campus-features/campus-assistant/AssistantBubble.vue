<script setup lang="ts">
import CampusIcon from '../../campus-shared/CampusIcon.vue'
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useSessionStore } from '../../campus-stores/session'
import { request, ApiError } from '../../campus-shared/http'
import { transport } from '../../campus-shared/transport'
interface Capabilities { enabled: boolean; accountType: string | null; maxMessageLength: number; timeoutSeconds: number; tools: string[] }
interface Message { id: string; conversationId: string; status: string; question: string; reply: string | null; tools: string[]; sources: { type: string; id: string; title: string; excerpt: string }[]; errorCode: string | null }
const session = useSessionStore(), open = ref(false), busy = ref(false), message = ref(''), input = ref(''), conversation = ref('')
const history = ref<Message[]>([]), capabilities = ref<Capabilities | null>(null), editor = ref<HTMLTextAreaElement | null>(null), timeline = ref<HTMLElement | null>(null)
const identity = computed(() => session.profile ? `${session.profile.id}:${session.profile.accountType}` : '')
const label = computed(() => ({ USER: '校园生活助手', MERCHANT: '门店经营助手', ADMIN: '平台管理助手' })[session.profile?.accountType ?? 'USER'])
let epoch = 0, controller: AbortController | null = null, pending: { text: string; key: string } | null = null
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
  if (previous) sessionStorage.removeItem(storageKey(previous))
  conversation.value = id ? sessionStorage.getItem(storageKey(id)) ?? '' : ''; void load()
}, { immediate: true })
watch(open, async value => { if (value) { await load(); await nextTick(); editor.value?.focus(); await scroll() } })
async function send() {
  const text = input.value.trim(), cap = capabilities.value
  if (busy.value || !text || !session.authenticated || !cap?.enabled) return
  const generation = epoch, id = identity.value; busy.value = true; message.value = ''; controller = new AbortController()
  if (!pending || pending.text !== text) pending = { text, key: crypto.randomUUID() }
  const current = pending, signal = AbortSignal.any([controller.signal, AbortSignal.timeout(Math.max(cap.timeoutSeconds, 10) * 1000)])
  try {
    if (!conversation.value) { const c = await request<{ id: string }>('/assistant/conversations', { method: 'POST', body: '{}', signal }); if (generation !== epoch) return; conversation.value = c.id; sessionStorage.setItem(storageKey(id), c.id) }
    let row = await request<Message>(`/assistant/conversations/${conversation.value}/messages`, { method: 'POST', headers: { 'Idempotency-Key': current.key }, body: JSON.stringify({ message: text }), signal })
    while (['PENDING', 'RUNNING'].includes(row.status)) {
      if (signal.aborted || generation !== epoch) return
      await new Promise(resolve => setTimeout(resolve, 1000))
      row = await request<Message>(`/assistant/conversations/${conversation.value}/messages/${row.id}`, { signal })
    }
    if (generation !== epoch) return
    if (row.status !== 'COMPLETED') throw new Error('本次回答未完成，可重试同一问题。')
    const index = history.value.findIndex(m => m.id === row.id); if (index < 0) history.value.push(row); else history.value[index] = row
    input.value = ''; pending = null; await scroll()
  } catch (e) { if (generation === epoch) message.value = e instanceof DOMException && e.name === 'TimeoutError' ? '等待较久，可重试同一问题以恢复处理结果。' : e instanceof Error ? e.message : '提问失败，请重试' }
  finally { if (generation === epoch) { busy.value = false; controller = null } }
}
function closeOnEscape(event: KeyboardEvent) { if (event.key === 'Escape') open.value = false }
window.addEventListener('keydown', closeOnEscape)
onBeforeUnmount(() => { controller?.abort(); window.removeEventListener('keydown', closeOnEscape) })
</script>
<template>
  <div class="assistant-widget">
    <Transition name="assistant-panel"><section v-if="open" id="campus-assistant-panel" class="assistant-panel" role="dialog" :aria-label="label" aria-modal="false">
      <header><div><strong>{{ label }}</strong><small>仅处理当前账号有权访问的信息</small></div><button type="button" aria-label="收起助手" @click="open = false"><CampusIcon name="close" :size="19" /></button></header>
      <div ref="timeline" class="assistant-timeline" aria-live="polite">
        <p v-if="!history.length" class="muted">你好，可以向我询问优惠信息或当前账号的相关业务。</p>
        <template v-for="row in history" :key="row.id"><div class="assistant-question">{{ row.question }}</div><div class="assistant-answer">{{ row.reply }}</div><div v-if="row.sources.length" class="assistant-sources"><details v-for="source in row.sources" :key="source.type + source.id"><summary>参考：{{ source.title }}</summary><p>{{ source.excerpt }}</p></details></div></template>
        <p v-if="busy" class="assistant-thinking"><span aria-hidden="true">●</span> 正在核对信息并组织回答…</p>
      </div>
      <p v-if="message" class="assistant-notice" role="status">{{ message }}</p>
      <p v-if="!session.authenticated" class="assistant-notice">登录后即可使用助手。<RouterLink to="/login">前往登录</RouterLink></p>
      <p v-else-if="!capabilities?.enabled" class="assistant-notice">助手暂不可用，请稍后重试。</p>
      <form v-else @submit.prevent="send"><label class="sr-only" for="assistant-input">向助手提问</label><textarea id="assistant-input" ref="editor" v-model="input" rows="2" :maxlength="capabilities.maxMessageLength" :disabled="busy" placeholder="输入问题，Enter发送，Shift+Enter换行" @keydown.enter.exact.prevent="send" /><button :disabled="busy || !input.trim()">{{ busy ? '回答中' : '发送' }}</button></form>
    </section></Transition>
    <button class="assistant-launcher" type="button" :aria-expanded="open" aria-controls="campus-assistant-panel" :aria-label="open ? '收起智能助手' : '打开智能助手'" @click="open = !open"><CampusIcon name="community" :size="20" /><span>{{ open ? '收起助手' : '智能助手' }}</span></button>
  </div>
</template>
<style scoped>
.assistant-widget { position: fixed; right: 20px; bottom: 20px; z-index: 1000; display: flex; align-items: flex-end; flex-direction: column; gap: 12px; max-width: calc(100vw - 32px); }
.assistant-launcher { border-radius: 30px; padding: 11px 16px; display: flex; gap: 8px; align-items: center; min-height: 44px; }
.assistant-launcher:hover:not(:disabled) { transform: translateY(-2px); }
.assistant-panel { width: 390px; max-width: 100%; height: min(600px,75vh); display: flex; flex-direction: column; background: white; border-radius: 12px; box-shadow: 0 12px 42px #23365328; overflow: hidden; }
.assistant-panel header { display: flex; justify-content: space-between; align-items: center; background: var(--campus-blue-soft); padding: 15px 18px; }
.assistant-panel header small { display: block; color: var(--campus-muted); font-size: 12px; margin-top: 4px; }
.assistant-panel header button { background: transparent; color: var(--campus-ink); padding: 6px; min-height: 32px; }
.assistant-timeline { flex: 1; overflow: auto; min-height: 0; padding: 16px; display: flex; flex-direction: column; gap: 12px; }
.assistant-question,.assistant-answer { padding: 11px 13px; white-space: pre-wrap; overflow-wrap: anywhere; border-radius: 9px; font-size: 14px; line-height: 1.75; }
.assistant-question { background: var(--campus-blue-soft); align-self: flex-end; max-width: 90%; }
.assistant-answer { background: var(--campus-ground); align-self: flex-start; max-width: 100%; }
.assistant-sources { font-size: 12px; line-height: 1.65; overflow-wrap: anywhere; color: var(--campus-muted); }
.assistant-sources p { white-space: pre-wrap; }
.assistant-panel form { padding: 12px; margin: 0; display: flex; gap: 8px; align-items: flex-end; border-top: 1px solid var(--campus-line); }
.assistant-panel textarea { resize: none; min-height: 65px; margin: 0; flex: 1; min-width: 0; font-size: 14px; }
.assistant-panel form button { padding: 10px 13px; }
.assistant-notice { padding: 8px 16px; font-size: 13px; margin: 0; }
.assistant-thinking { font-size: 13px; color: var(--campus-muted); }
.assistant-thinking span { display: inline-block; animation: assistant-breathe 1.2s ease-in-out infinite; }
.assistant-panel-enter-active,.assistant-panel-leave-active { transition: opacity .2s var(--campus-ease), transform .2s var(--campus-ease); }
.assistant-panel-enter-from,.assistant-panel-leave-to { opacity: 0; transform: translateY(12px) scale(.98); transform-origin: bottom right; }
@keyframes assistant-breathe { 50% { opacity: .3; } }
@media(max-width:520px) { .assistant-widget { right: 14px; bottom: 14px; }.assistant-launcher { padding: 12px; width: 46px; height: 46px; }.assistant-launcher > span { display: none; }.assistant-panel { width: calc(100vw - 28px); height: min(620px,74vh); } }
@media(prefers-reduced-motion:reduce) { .assistant-launcher,.assistant-panel-enter-active,.assistant-panel-leave-active { transition: none; }.assistant-thinking span { animation: none; } }
</style>
