# 实验性前端视觉素材

分支：`campus-frontend-exp`；基于 `campus-frontend` 的 `3563b36cbb8e1e7eea812e749d5da8db38f9c6e2`。仅调整首页视觉，保留业务请求、领券逻辑和已定稿页脚。

使用用户确认的内置 image_gen 工具生成，工具未公开具体模型型号，不宣称使用 image2.6。主题为校园社团布告栏：浅白、校园蓝、少量橙黄，纸张、回形针、图钉、星芒与票券元素。不在图片中绘制金额、库存、商家照片或活动承诺。

| 素材 | 用途 |
| --- | --- |
| [纸张背景](frontend/src/assets/campus-exp/board-paper.webp) | 首页引导区浅色底纹 |
| [咖啡票券](frontend/src/assets/campus-exp/coffee-ticket.webp) | 咖啡主题入口局部装饰 |
| [校园票夹](frontend/src/assets/campus-exp/ticket-wallet.webp) | 布告栏券包提示装饰 |
| [折纸地图](frontend/src/assets/campus-exp/map-discovery.webp) | 附近商家入口 |
| [探店手记](frontend/src/assets/campus-exp/community-notes.webp) | 社区分享入口 |

以 WebP 导出压缩，两张独立装饰保留透明通道。装饰不参与辅助阅读与点击命中；正文使用实时 HTML。适配窄屏，尊重减少动态效果设置。

## 生成提示词

### board-paper

```text
Use case: stylized-concept. Asset type: wide decorative paper background for a Chinese university neighborhood discounts website, not a finished website screenshot. Create a sophisticated tactile campus society noticeboard collage, landscape ratio 3:2. Predominantly very pale off-white and pale campus blue, cobalt blue details with tiny warm orange/yellow accents. Fine real paper grain, faint graph-paper fragments, elegant torn paper edges, a few silver paperclips, one small blue pushpin, tasteful handdrawn starbursts and a curved doodle arrow along the far margins. Layered stationery and small blank ticket stubs mainly at the lower and upper far corners. The entire central 75 percent must remain extremely calm pale blue-white with subtle material texture only, suitable as an accessible backdrop beneath dense live HTML text. Youthful, polished editorial scrapbooking, not childish clipart, not generic SaaS, no oversized cards. No tape strips. No people, bicycles, plants, storefront photo or big scene illustration. Absolutely no text, letters, numbers, logos, watermarks, prices or pretend promotions. Flat rectangular artwork, no outer mockup margin. This is a decorative raster material, not a replacement for functional UI.
```

### coffee-ticket

```text
Use case: stylized-concept. Asset type: compact decorative raster cutout for a campus discounts website. Subject: one delightful small paper-craft still life of a cobalt blue takeaway coffee cup with a cream lid, one tiny warm yellow blank perforated coupon ticket leaning in front of it, a silver paperclip, and two little handdrawn blue and golden starbursts. Transparent background, isolated silhouette with generous transparent margin, subtle soft grounded contact shadow only. Youthful tactile editorial stationery collage, visibly layered textured cut paper, polished not childish, compatible with a pale white/campus blue and small orange-yellow website palette. Compact horizontal composition 3:2; objects form one coherent grouped small sticker not a big scene, not a sticker sheet. No human characters, plants, bicycles, stores or other props. No tape. No lettering, no numbers, no currency, no logos, no watermarks, no fake offers. Real alpha transparency outside objects, not a drawn checkerboard. Every edge crisp and clean at small display sizes.
```

### ticket-wallet

```text
Use case: stylized-concept. Asset type: compact transparent-background paper-craft decoration for the 'my coupon wallet' section of a Chinese university neighborhood perks website. A small cobalt-blue open paper ticket wallet holding two blank pale cream and warm golden-yellow perforated coupon stubs, one small silver paperclip and a tiny blue handdrawn sparkle. Same premium tactile cut-paper editorial stationery art direction as a campus society noticeboard, precise layered paper edges and fine natural paper grain, polished youthful college atmosphere. Compact horizontal 3:2 composition, entire object visible with generous transparent surrounding margins, restrained contact shadow, simple and immediately readable when displayed at 140 pixels wide. Colors: campus cobalt blue, pale white, small orange/gold accent. No text, letters, numbers, currency, brand names, logos or watermark. No tape strips, no characters, no large scenes, no plants or bicycles. This is decorative, all real offer information will be live HTML. Genuine alpha transparency, no painted background or checkerboard.
```


## 入口组一致性修正

咖啡、附近商家、探店分享三个入口全部使用同一咖啡素材作为风格参照的纸艺图，不再混用线性主图。后续按用户反馈将三张容器整体改为同款邮票，移除入口上的图钉、回形针和彩色便签底。

### map-discovery

素材：frontend/src/assets/campus-exp/map-discovery.webp

```text
Generate a NEW isolated decorative artwork. The supplied coffee-and-ticket image is STYLE REFERENCE ONLY, not an edit target: match its tactile layered cut-paper material, clean silhouette, premium campus stationery appearance, camera angle and cobalt-blue/cream/golden-yellow palette precisely. Asset type: tiny website noticeboard entry artwork, a folded pale cream paper street map with simple cobalt-blue street folds and ONE small golden yellow location pin. No coffee cup or coupon ticket needed in this new subject. Compact grouped object, 3:2 canvas, similar object occupancy and visual weight to the reference coffee cup. Entire object visible. Fine paper grain, restrained soft contact shadow, crisp small-size readability, no background scene. Genuine transparent alpha outside the artwork. No letters, names, numbers, logos, watermark, tape, bicycles, characters or plants. Must feel part of exactly the same coherent asset family as the supplied reference.
```

### community-notes

素材：frontend/src/assets/campus-exp/community-notes.webp

```text
Generate a NEW isolated decorative artwork. Supplied coffee-and-ticket image is STYLE REFERENCE ONLY: match its precise tactile cut-paper material, fine natural grain, premium campus stationery style, camera angle and cobalt-blue/cream/golden-yellow palette. Asset type: tiny decorative website entry for classmates sharing local-shop discoveries. Subject: a compact small cobalt-blue pocket notebook with two pale cream pages peeking out, a short golden-yellow pencil resting diagonally across it and one tiny blue paper speech bubble. One coherent small group, similar object occupancy and optical weight to reference coffee cup. 3:2 canvas, entire object visible, soft minimal contact shadow, clean readable silhouette at small size. Genuine transparent alpha background, not a checkerboard. No readable text, scribbles resembling writing, letters, numbers, photos, logos, watermarks, tape, people, bicycles, plants or storefronts. No additional ticket or coffee cup in this new image. Visually part of exactly the same paper-craft family as the reference.
```

## 验证与预览

类型检查与生产构建通过。1440、390、320px 页面检查无横向溢出；四个装饰前景图片均解码成功、无障碍隐藏且不拦截点击；咖啡关键词交互与减少动态效果检查通过。截图使用私有视觉夹具中的明确示例数据，不代表真实优惠或后端业务验收；没有业务写请求，也没有发送短信。五张生成素材合计以实际文件大小为准，保留 WebP 透明通道。

- [桌面效果](visual-preview/desktop.png)
- [手机效果](visual-preview/mobile.png)
- [统一入口素材](visual-preview/entry-family.png)
- [验证记录](visual-preview/checks.json)
- [纯票券 Logo 历史候选](visual-preview/logo-options.png)：用户已明确选择 C「双票叠放」。A、B 仅作为候选记录保留。

## 已确认的 Logo 与标签页图标

用户要求仅票券图形、站名独立显示，并从三款实际预览中选择 C。Logo 采用矢量绘制，源文件为 [campus-logo.svg](frontend/src/assets/campus-exp/campus-logo.svg)，统一由 CampusMark 组件用于学生端、商家端、管理端及页脚。HTML 关联同一 SVG，并提供 [16px](frontend/public/favicon-16.png)、[32px](frontend/public/favicon-32.png) PNG 后备图标。浏览器验证确认构建后品牌图与 SVG favicon 的路径几何、变换和配色一致，两个 PNG 图标正常加载。

本次提交仅在实验分支，未部署或合并到前端主分支。视觉预览使用示例数据，不发送短信，不执行领券、支付、审核等业务写操作。

## 邮票入口更新

三枚同款浅米白纸纹邮票，四边为真正透明的 CSS 齿孔，内框统一校园蓝。纸纹复用既有生成素材，齿孔及文字由原生界面绘制，保留实际按钮和链接。入口改为「课间咖啡」「周边小店」「探店分享」，对应原关键词查询和路由；不仿造邮资、折扣或商家活动信息。窄屏适配到 320px；键盘焦点轮廓位于齿孔内侧，减少动态效果设置下停用抬升和倾斜。已确认的 C 款 Logo 与 favicon 保留。
