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
- 现状：暖纸白 `#FAF9F6` / 暖黑 `#151412`（阅读类 App 主流）。
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
