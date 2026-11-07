# 需要用户决策的事项

以下改动因涉及主观偏好或架构取舍，本次重构**未擅自执行**。每项都标注了影响范围和改回/继续的成本。

## 1. 默认主题主色（已决策：暖橙 #FF6B2C）
- 用户已确认主题色换橙色：运行时主题（ThemeStore 偏好，默认 design_orange）与
  `values/colors.xml` 的 `primary`/`primaryDark`/`accent` 资源兜底色均已对齐暖橙体系。
- 注意：#FF6B2C 上白字对比约 2.8:1，低于 4.5:1 标准；若在意可换 ui-ux-pro-max
  推荐的琥珀 `#D97706`（对比约 3.9:1，仍不达 AA，深色底更佳），属用户偏好。
- 影响范围：换肤机制未动，用户仍可在设置里改任意颜色。

## 2. 底部导航是否显示文字标签
- 现状：已改为 labeled（图标+文字，M3 推荐，无障碍更好）。
- 如需极简 unlabeled：改 `styles.xml` 中 `Widget.Legado.BottomNavigationView` 的 `labelVisibilityMode` 为 `unlabeled`。

## 3. 中性表面色温
- 现状：暖纸白 `#FAF9F7` / 暖黑 `#141312`（阅读类 App 主流，与 MASTER.md 及 colors.xml 实装值一致）。
- 备选：纯白 `#FFFFFF` / 纯黑 `#000000`（AMOLED 省电）或原来的冷灰。
- 改动点：`colors.xml`（含 night）中 `surface` / `surface_container` 系列即可全局生效。

## 4. 对话框圆角 16dp（代码改动）
- 改了 `BaseDialogFragment`：容器背景从直角纯色改为运行时颜色 + 16dp 圆角。
- 如不喜欢：还原 `BaseDialogFragment.kt` 中 `view.background = GradientDrawable...` 一处即可。

## 5. 阅读器（正文阅读界面）未动
- 原因：阅读页背景/字号/行距全部是用户自定义的，且墨水屏模式有特殊分支；设计系统强行介入会与用户偏好冲突。
- 如需统一升级阅读菜单/底部操作栏，需要单独一轮设计。

## 6. 大方向：是否长期迁移 Material You / Compose
- 本次策略是"AppCompat 栈上的 M3 令牌化"，成本最低且不破坏换肤。
- 完整 M3（Theme.Material3 + dynamic color）或 Compose 重写属于架构级重构，需要专门排期。

## 7. 未逐页打磨的次要页面
- RSS 订阅源列表、漫画页、词典/字体管理等低频页面只受益于全局令牌，未做页面级布局调整。

## 8. 布局内 primary/accent 引用的换肤豁免清单
- 背景：MASTER.md §4 要求「布局不得固化 primary/accent（必须可换肤）」；经逐处核查，
  本应用无 ThemeStore→主题属性覆盖机制（`applyStyle` 全库零命中），`?attr/colorPrimary`
  实际解析 `styles.xml:28` 的静态映射 `@color/primary`，用户换肤后不跟随——机制性既有限制。
- 已达标（无豁免）：`dialog_read_aloud` 主播放钮已升级为运行时着色
  （`ReadAloudDialog.upPlayState` 以 `ThemeStore.accentColor` 设 backgroundTintList 并按明暗取图标黑白），
  XML 侧仅余 `tools:backgroundTint` 预览属性（03 稿注记许可的纯 XML 方案之上的加强）。
- 已达标（无豁免，二轮补）：`item_font.xml` 选中对勾与 `bg_click_action_panel_mid.xml` 中格虚线描边
  曾为布局内静态 `@color/primary`，已按上述自述疗法改为运行时着色——`FontAdapter.convert`
  以 `context.primaryColor` setColorFilter（XML 侧 tint 已删）、`ClickActionConfigDialog.onFragmentCreated`
  以 `context.primaryColor` mutate+setStroke（drawable 内 primary 仅余 ThemeStore 默认兜底，同豁免 2 兜底逻辑）。
- 豁免 1（2026-11-07 收编）：`item_chapter_list.xml` iv_checked 原 `?attr/colorPrimary` →
  ChapterListAdapter.upHasCache 以 `context.primaryColor` setColorFilter；
  ChangeBookSourceAdapter/ImportBookAdapter 的勾选/书籍图标同批运行时化（+约 3 行/处）。
- 豁免 2（2026-11-07 收编）：`activity_manga.xml` 加载指示器改
  `CircularProgressIndicator`，ReadMangaActivity.onActivityCreated
  `setIndicatorColor(primaryColor)`（+1 行）。
- 听书封面边框（`AudioPlayActivity` ivCover.borderColor，原 civ_border_color=@color/accent）
  同批改为运行时 accentColor。
- **更正（2026-11-07 二次核实，用户手机截图证伪）**：本节及 §11 原认定的
  「底导航走静态通道」为**误诊**——底导航是上游自染色控件
  `ThemeBottomNavigationVIew`（activity_main.xml），`upTheme()` 一直以运行时
  **accentColor** 染选中态，从未静态。此前"绿测只改主色调、导航不动"的实验
  单变量不完整，导向错误结论。真实静态点仅为当日收编的小部件（勾选图标、
  漫画指示器、导入徽标、听书封面边）。当日改动调整为：
  `ThemeBottomNavigationVIew.upTheme()` 选中色 accent→**primaryColor**（M3 语义，
  墨水屏维持强调色），指示条 12% primary；深浅切换经 onConfigurationChanged
  重着色单点生效（MainActivity 不再重复染色）。
  `styles.xml` 的 `?attr/colorPrimary|colorAccent → @color/primary|accent` 映射
  保留作首帧兜底（默认色已是暖橙体系，见 §1）。模拟器验证：改主色调→底导航
  即时跟随且胶囊（强调色）不变；应用「默认」预设→全 UI 回橙。

## 9. 书架设置弹窗容器降级为现状 AlertDialog（NEED-DECISION 路线③）
- 背景：bookshelf-config-dialog.html 的 [NEED-DECISION]「容器落地路线」给过三案：
  ①styles 新增全局 `alertDialogTheme`（圆角背景 + windowTitleStyle）；②本弹窗迁 `BaseDialogFragment`；
  ③降级现状容器、仅表单内令牌化。落地按 **③** 执行
  （`BaseBookshelfFragment.configBookshelf` / `dialog_bookshelf_config.xml`）：保留 androidx AlertDialog
  框架标题与正/负按钮面板，稿面 16dp 圆角卡/描边/18sp 标题/底栏分隔为设计目标形态，未落地。
- 约束来源：页面级落地任务规则禁止编辑 `values/*.xml`、`styles.xml`、themes 及既有 drawable
  （本条为落地轮约束，非本文件原有记录），路线①依赖新增全局资源被阻断；路线②需把
  alert + okButton/cancelButton 改写为 DialogFragment 自绘底栏，超出「行为不变」最小改动口径。
- 若后续放开 values 限制：按①新增全局 `alertDialogTheme` 一处生效全库 AlertDialog 即可升级，
  稿面 03 映射表「表单区各行」不受影响。
- **更正（2026-11-07 核实轮）**：alert 系容器 16dp 圆角实际已随
  `alertCardBackground`（MaterialValueHelper.kt，corner_radius_lg + 运行时 backgroundColor）
  经 `AlertDialog.applyTint()` 全局生效，模拟器实拍确认；本弹窗容器圆角不再是缺口，
  剩余未落地项收窄为「卡面描边 + 18sp 标题」两项纸面细节。

## 10. 清单外弹窗「圆角被遮蔽」清理（2026-11-07，用户反馈弹窗直角）
- 背景：用户核实设计稿落地情况，反馈「设计稿弹窗全圆角，App 弹窗基本直角」。逐项核实结论：
  `BaseDialogFragment`（62 类）、`alert{}`/`selector{}`（经 applyTint→alertCardBackground，约 151 调用点）、
  NumberPickerDialog 均已是 16dp 圆角运行时卡面；观感上的「直角」来自**旧式弹窗自带的灰底
  Toolbar/底栏把圆角卡面盖住**（多为两批 43 稿清单外的页面化弹窗）。
- 处理（对齐已落地 dialog_webdav_server 的 blend 标题栏语言，全部为去遮蔽最小改动）：
  - dialog_book_change_source / dialog_chapter_change_source：Toolbar 去 background_menu+5dp
    elevation，底栏去 @color/surface 底（分隔线保留）；ChangeBookSourceDialog 另修两处旧主色
    时代残留——showTitle() 的 elevation 回填删除，返回箭头明暗改按运行时卡面
    backgroundColor 判断（原按 primaryColor 判断在 blend 卡面上给出白箭头）；
  - dialog_search_scope：Toolbar 与底栏去底色；
  - dialog_book_group_edit：Toolbar 与表单区去 background_menu；
  - dialog_dict_rule_edit / dialog_login / dialog_toc_regex_edit：bg_card 卡内 Toolbar 去灰底
    （原「.dlg-bar 运行时染 primaryColor」注释为陈旧未实现项，随之移除）；
  - dialog_open_url_confirm：根卡 shape_card_view（3dp）→ bg_dialog_card（16dp+描边）；
  - PermissionActivity 理由弹窗（裸 AlertDialog.Builder，唯一未入 applyTint 的常驻弹窗）→
    show().applyTint()。
- 未动：阅读菜单（view_read_menu 等按 DECISIONS §5 维持现状）。

## 11. 主题色诊断与「默认」预设修正（2026-11-07，用户报手机/模拟器主题色不一致）
- 现象：模拟器改「主色调」后界面无变化（用户感知"调整无效"）；手机（3.26.110600debug）
  改主题设置却生效、整体呈另一颜色（蓝）。两台设备同为带橙色兜底资源的 M3 构建。
- **最终根因（二次核实修正）**：
  1. 底导航从上游起就是自染色控件 `ThemeBottomNavigationVIew`，选中态一直跟运行时
     **强调色**——并非静态通道（初诊有误，见 §8 更正）。
  2. "改主色调无效"的真因：M3 重构删除了上游最后的主色大消费者（TitleBar 染色），
     主色调在重构后的可见消费者≈0，改了无处显示；且 `applyTheme` 白天分支排除
     md_red_600（取色器默认选中色），直接确认还会被静默换回兜底橙。
  3. 手机/模拟器差异 = **设备存储差异**（`app_themes.xml` 跨覆盖安装保留）：模拟器存储
     恰为橙方案（强调色 #FF6B2C 与静态兜底同色，形成"整个 App 被静态锁橙"的伪装），
     手机存储为历史操作写入的蓝方案。无首启代码写 ThemeStore（App/WelcomeActivity
     零命中），全新安装才会落到资源兜底。
- 处置：主色调重新接入显眼消费（底导航选中色 accent→primary，见 §8 更正）；
  当日收编的静态小部件（勾选图标/漫画指示器/导入徽标/听书封面边）改为
  运行时 primaryColor/accentColor 染色。
- 预设修正：`defaultData/themeConfig.json` 的「默认」预设原为棕 #795548/红 #E53935
  ——而 `ThemeConfig.applyTheme` 白天分支明确排除 md_brown_500 与 md_red_600
  （防旧版死灰/大红回潮），红/棕主色会被静默换成 design_orange 兜底，
  强调色选红也被排除 → 该预设应用后"看起来没反应"。已将其更新为当前方案
  `#FF6B2C / #FF6B2C / #FAF9F7 / #FFFEFC`，与未换肤用户的实际观感一致。
- **陷阱备忘**：取色器里默认选中的红色（md_red_600）若直接确认，会被排除逻辑
  静默忽略（主/强调均如此），用户会以为"调了没生效"；后续若放开限制需先移除该排除对。

