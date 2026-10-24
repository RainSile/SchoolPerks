# Campus Notice Round

学生端标题字体，源自 [寒蝉全圆体 ChillRoundF](https://github.com/Warren2060/ChillRound)。官方源文件 `ChillRound/ChillRoundF.ttf`，固定版本提交 `46dda1602729b58de96e3fdee9f918d7aaa64727`。版权 © 2023 ChillType。

采用 SIL Open Font License 1.1，随附完整 [许可证](ChillRound-LICENSE.txt)。本项目按现有 Vue/TS 源码中的界面字符制作733字符、888字形的静态400字重子集，压缩为WOFF（247512字节）。为遵守源字体的保留字体名称约束，派生字体内部family／full name／PostScript名称已改为Campus Notice Round／CampusNoticeRound-Regular，保留版权和许可字段。

只用于学生端静态标题、品牌和便签短标题，`font-display: swap`；正文、金额、规则、动态商户标题和表单沿用系统无衬线字体。新增界面字符时重新制作子集；没有包含的字形由明确的系统后备字体显示。字体随项目自托管，无需用户访问Google Fonts或外部CDN。

制作使用现有FontTools的pyftsubset和TTFont压缩／元数据处理工具。它们不是项目运行时依赖；完整源字体与制作中间文件仅在忽略目录`.local/font-sources`中。
