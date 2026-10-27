<script setup lang="ts">
import CampusMark from './CampusMark.vue'
import campusLandscape from '../assets/campus-footer-landscape.webp'
import campusPeople from '../assets/campus-footer-people.webp'
</script>

<template>
  <footer class="campus-footer">
    <div class="campus-footer-scene" aria-hidden="true">
      <div class="campus-footer-landscape">
        <img class="campus-footer-panorama" :src="campusLandscape" alt="" width="2160" height="720" decoding="async" />
        <svg class="campus-footer-pavement" viewBox="0 640 2160 80" preserveAspectRatio="xMidYMax slice" focusable="false">
          <image :href="campusLandscape" width="2160" height="720" />
        </svg>
      </div>
      <img
        class="campus-footer-art"
        :src="campusPeople"
        alt=""
        width="1200"
        height="661"
        loading="lazy"
        decoding="async"
      />
    </div>
    <div class="site-footer">
      <div class="footer-brand">
        <CampusMark />
        <div><strong>校园周边优惠</strong><p>把校园周边的小确幸，装进你的日常。</p></div>
      </div>
      <div class="footer-links">
        <RouterLink to="/account/campus-verification">校园认证</RouterLink>
        <RouterLink to="/account/merchant-application">商家入驻</RouterLink>
        <RouterLink to="/nearby">逛逛周边</RouterLink>
      </div>
      <p>到店前查看使用规则与有效期，优惠以当前活动信息为准。</p>
    </div>
  </footer>
</template>

<style scoped>
.campus-footer {
  position: relative;
  isolation: isolate;
  overflow: hidden;
  background: var(--campus-blue-soft);
}

.campus-footer-scene {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

/* Blend the landscape into the main ground without fading the people layer. */
.campus-footer-scene::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
  background: linear-gradient(to bottom, var(--campus-ground), transparent 82px);
}

/* Keep the panorama on mobile; the desktop viewport shows only foreground pavement. */
.campus-footer-landscape {
  position: absolute;
  inset: 0;
  z-index: 0;
  width: 100%;
  height: 100%;
  opacity: .13;
}

.campus-footer-panorama,
.campus-footer-pavement {
  display: block;
  width: 100%;
  height: 100%;
}

.campus-footer-panorama {
  object-fit: cover;
  object-position: center bottom;
}

.campus-footer-pavement {
  display: none;
}

@media (min-width: 901px) {
  .campus-footer-panorama { display: none; }
  .campus-footer-pavement { display: block; }
}

.campus-footer-art {
  position: absolute;
  z-index: 1;
  bottom: 6px;
  left: 50%;
  transform: translateX(-50%);
  display: block;
  width: auto;
  height: min(180px, calc(100% - 12px), calc((100vw - 36px) * 661 / 1200));
  opacity: .33;
  object-fit: contain;
  object-position: center bottom;
}

.site-footer {
  position: relative;
  z-index: 1;
  border-top: 0;
}

.footer-brand p,
.site-footer > p {
  color: var(--campus-ink);
}
</style>
