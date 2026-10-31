package io.legado.app.lib.theme

/**
 * 13-read-style#22 单一 ink 梯度表：底部弹层类界面以自适应 primaryText（ink）为基色的派生档位，
 * 替代散布各文件的裸 float 比率（调整任一档位只改此处）。
 *
 * 档位与 13-read-style 梯度注对应：分割线 12% / 容器 8% / 胶囊 10%（非文字用途），
 * 另含描边 25%（softStroke/未选色板）/ 30%（ghost 虚线）两档。
 * 文字弱化不在表内：次要字走 on_surface_variant 角色令牌（[Context.getOnSurfaceVariant]），
 * ink 8%/10% 叠层胶囊上的文字用 ink 本色（叠层上弱化字色 <4.5:1，见 13-read-style 梯度注）。
 */
object InkGradient {
    /** 分割线（ReadStyleDialog vwBgFg/vwBgFg1） */
    const val DIVIDER = 0.12f

    /** 容器/胶囊底（DetailSeekBar 行卡、ThemeRadioNoButton 未选底、色板 footer ghost 圆底） */
    const val CONTAINER = 0.08f

    /** 值胶囊底（DetailSeekBar tvSeekValue） */
    const val CHIP = 0.10f

    /** 静息/未选描边（StrokeTextView softStroke、色板未选描边） */
    const val STROKE_MUTED = 0.25f

    /** ghost 虚线描边（色板 footer 加号圆） */
    const val STROKE_GHOST = 0.30f
}
