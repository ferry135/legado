# Legado (classic) — Design System MASTER

> Source of truth for UI refactoring. Page-specific overrides live in `pages/<page>.md`
> and take precedence over this file.

## 1. Product & Direction

- **Product**: 电子书阅读器（book reader），内容优先、长时间阅读、工具型。
- **Visual direction**: Minimalism / content-first（查询自 ui-ux-pro-max：
  "mobile reading app bookshelf minimal clean" + "Book & Reading Tracker" palette）。
- **Key principles**:
  1. 暖纸色中性表面（warm paper surfaces），让封面图片成为唯一的强视觉焦点。
  2. 主色/强调色保持**运行时用户可换肤**（ThemeStore），设计令牌只固化中性表面色。
  3. Material 3 语义命名（surface / on_surface / outline ...），底层仍是 AppCompat View 栈。
  4. 无障碍：正文对比度 ≥ 4.5:1，触控目标 ≥ 48dp，相邻目标间距 ≥ 8dp。

## 2. Color Tokens

### Light
| Token | Value | 用途 |
|---|---|---|
| `surface` | `#FAF9F7` | 页面背景（暖纸白） |
| `surface_container` | `#FFFFFF` | 卡片/列表容器 |
| `surface_container_high` | `#EEECE9` | 菜单/弹层/底栏 |
| `on_surface` | `#20242B` | 主文字（暖墨色） |
| `on_surface_variant` | `#60656D` | 次要文字 |
| `outline` | `#8B8878` | 强分隔/图标描边 |
| `outline_variant` | `#E4E1D8` | 分割线/描边 |
| `primary_container` | `#C2E8FF` | 主色容器（默认主色的 M3 色调映射） |
| `on_primary_container` | `#004C64` | 主色容器上的文字 |
| `error` | `#BA1A1A` | 错误 |
| `success` | `#3B7A45` | 成功 |
| `warning` | `#C2551F` | 警示（换源「踩」iv_bad 激活态，明暗同值） |

### Dark (Night)
| Token | Value |
|---|---|
| `surface` | `#141312` |
| `surface_container` | `#1D1B19` |
| `surface_container_high` | `#242220` |
| `on_surface` | `#F3F0EB` |
| `on_surface_variant` | `#B4B0AA` |
| `outline` | `#8F8B7F` |
| `outline_variant` | `#36342E` |
| `primary_container` | `#004C64` |
| `on_primary_container` | `#C2E8FF` |
| `error` | `#FFB4AB` |
| `success` | `#7FD891` |
| `warning` | `#C2551F` |

### Legacy aliases（保持旧布局兼容，指向新令牌）
- `background` → `surface`
- `background_card` → `surface_container`
- `background_menu` → `surface_container_high`
- `primaryText` → `on_surface`
- `secondaryText` → `on_surface_variant`
- `tv_text_summary` → `on_surface_variant`
- `menu_color_default` → `on_surface`
- `bg_divider_line` → `outline_variant`
- `divider` 保留半透明灰 `#66666666`（阅读页代码用在任意自定义背景上，必须随背景自适应）

### 不动的
- `primary` / `primaryDark` / `accent`：默认值仅作为 ThemeStore 换肤的初始色，运行时可被用户覆盖。
- `colors_material_design.xml` 的 `md_*` 调色板。
- `btn_bg_press*`（TV 焦点态需要可见性）。

## 3. Shape / Spacing / Type

- 圆角：`corner_radius_sm=8dp`（小控件）、`corner_radius_md=12dp`（卡片/菜单）、
  `corner_radius_lg=16dp`（底部弹层）。
- 间距：`spacing_sm=8dp`、`spacing_md=12dp`、`spacing_lg=16dp`、`spacing_xl=24dp`。
- 图标：`action_icon_size=24dp`（阅读菜单/自动阅读/朗读弹层动作图标最大高度）。
- 字体：系统默认字体栈；type scale——Title 18sp/Medium、正文 14sp、辅助 12sp，
  主文字色 `on_surface`、辅助 `on_surface_variant`，禁用灰底灰字。

## 4. Anti-patterns（本项目重点规避）
- 纯黑/纯白大面积背景（改用暖纸/暖黑）。
- 冷灰蓝暗色（旧 grey_900/blue_grey）→ 暖黑层级化表面。
- 2dp 直角弹窗菜单（旧 bg_popup_menu）。
- 文字对比 < 4.5:1；触控目标 < 48dp。
- 在布局里硬编码 primary/accent（必须可换肤）。
