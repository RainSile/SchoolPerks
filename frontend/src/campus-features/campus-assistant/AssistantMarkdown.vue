<script setup lang="ts">
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
const props = defineProps<{ text: string }>()
const markdown = new MarkdownIt({ html: false, breaks: true, linkify: true }).disable('image')
markdown.renderer.rules.link_open = (tokens, index, options, _environment, renderer) => {
  tokens[index]!.attrSet('target', '_blank')
  tokens[index]!.attrSet('rel', 'noopener noreferrer nofollow')
  return renderer.renderToken(tokens, index, options)
}
const html = computed(() => DOMPurify.sanitize(markdown.render(props.text), {
  ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 's', 'blockquote', 'ul', 'ol', 'li', 'pre', 'code', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'hr', 'table', 'thead', 'tbody', 'tr', 'th', 'td', 'a'],
  ALLOWED_ATTR: ['href', 'title', 'target', 'rel', 'start'],
  ALLOW_DATA_ATTR: false,
}))
</script>
<template><div class="assistant-markdown" v-html="html" /></template>
<style scoped>
.assistant-markdown { overflow-wrap: anywhere; min-width: 0; line-height: 1.75; }
.assistant-markdown :deep(> :first-child) { margin-top: 0; }
.assistant-markdown :deep(> :last-child) { margin-bottom: 0; }
.assistant-markdown :deep(p) { margin: .55em 0; }
.assistant-markdown :deep(h1),.assistant-markdown :deep(h2),.assistant-markdown :deep(h3) { font-size: 1.08em; margin: .8em 0 .4em; line-height: 1.5; }
.assistant-markdown :deep(ul),.assistant-markdown :deep(ol) { padding-left: 1.6em; margin: .6em 0; }
.assistant-markdown :deep(pre) { overflow: auto; max-width: 100%; padding: 10px; background: #16243a; color: #eef4ff; border-radius: 6px; white-space: pre; font-size: 12px; }
.assistant-markdown :deep(code) { font-family: ui-monospace, Consolas, monospace; font-size: .9em; }
.assistant-markdown :deep(:not(pre) > code) { background: #2345770c; padding: 1px 4px; border-radius: 3px; }
.assistant-markdown :deep(table) { display: block; overflow: auto; max-width: 100%; border-collapse: collapse; font-size: 12px; }
.assistant-markdown :deep(th),.assistant-markdown :deep(td) { border: 1px solid var(--campus-line); padding: 5px 8px; min-width: 50px; }
.assistant-markdown :deep(th) { background: var(--campus-blue-soft); }
.assistant-markdown :deep(blockquote) { margin: .6em 0; border-left: 3px solid var(--campus-blue); padding-left: 10px; color: var(--campus-muted); }
.assistant-markdown :deep(a) { color: var(--campus-blue); text-decoration: underline; }
</style>
