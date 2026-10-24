<script setup lang="ts">
withDefaults(defineProps<{
  kind: 'pin' | 'clip' | 'spark' | 'arrow' | 'stamp' | 'tape'
}>(), { kind: 'spark' })
</script>

<template>
  <span class="campus-ornament" :class="'ornament-' + kind" aria-hidden="true">
    <svg v-if="kind === 'pin'" viewBox="0 0 28 32" fill="none">
      <path d="m14 17-2 13" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
      <circle cx="15" cy="11" r="8" fill="#ffdf7e" stroke="currentColor" stroke-width="1.6" />
      <path d="M11 8c1-2 3-3 5-2" stroke="white" stroke-width="2.5" stroke-linecap="round" />
    </svg>
    <svg v-else-if="kind === 'clip'" viewBox="0 0 28 44" fill="none">
      <path d="M9 31V12a6 6 0 0 1 12 0v21a9 9 0 0 1-18 0V11a4 4 0 0 1 8 0v20a2 2 0 0 0 4 0V14" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
    </svg>
    <svg v-else-if="kind === 'spark'" viewBox="0 0 58 48" fill="none">
      <path d="m25 5 4 13 12 5-12 4-5 14-4-13-13-5 13-4 5-14Z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" />
      <path d="m47 3 2 6 6 2-6 2-2 6-2-6-6-2 6-2 2-6Z" fill="#ffdf7e" stroke="currentColor" stroke-width="1.2" />
      <path d="m46 33 2 6m-5-2 7-2M5 6l3 4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
    </svg>
    <svg v-else-if="kind === 'arrow'" viewBox="0 0 90 44" fill="none">
      <path d="M4 11c12 21 39 26 63 10 6-4 11-9 15-14m-2 19 3-20-19 3" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
      <path d="M6 20c12 15 28 19 42 14" stroke="currentColor" stroke-width="1.1" stroke-linecap="round" />
    </svg>
    <span v-else-if="kind === 'stamp'" class="ornament-stamp-ink">校园<br />日常</span>
  </span>
</template>

<style scoped>
.campus-ornament { display: inline-block; flex: none; color: var(--campus-blue-strong); pointer-events: none; user-select: none; }
.campus-ornament svg { display: block; width: 100%; height: 100%; }
.ornament-pin { width: 24px; height: 29px; transform: rotate(12deg); }
.ornament-clip { width: 22px; height: 37px; transform: rotate(-11deg); }
.ornament-spark { width: 48px; height: 40px; color: var(--campus-orange); }
.ornament-arrow { width: 76px; height: 38px; }
.ornament-tape { width: 40px; height: 13px; background: #ffdf7ec9; transform: rotate(-7deg); }
.ornament-stamp { transform: rotate(11deg); }
.ornament-stamp-ink { display: grid; place-items: center; width: 47px; height: 47px; border: 3px double currentColor; border-radius: 50%; font-size: 11px; line-height: 1.3; letter-spacing: .08em; text-align: center; }
</style>
