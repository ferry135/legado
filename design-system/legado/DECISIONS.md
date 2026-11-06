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
- 豁免 1：`item_chapter_list.xml:84` iv_checked `app:tint="?attr/colorPrimary"` ——
  02 稿 :461 注记明确许可「改 ?attr/colorPrimary 即与本稿一致（纯 XML 一行）」，按稿保留。
- 豁免 2：`activity_manga.xml:57` 加载指示器 `app:indicatorColor="@color/primary"` ——
  Material3 CircularProgressIndicator，稿面无单独换肤注记，按静态兜底色落地。
- 如需严格可换肤：前者在 ChapterListAdapter 绑定处以 `context.primaryColor` 着色（约 3 行），
  后者在加载视图初始化处 `setIndicatorColor(context.primaryColor)`（约 1 行）；
  默认色兜底已是暖橙体系（见 §1），未换肤用户观感不受影响。

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

