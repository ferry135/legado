package io.legado.app.lib.prefs

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder
import io.legado.app.R

class PreferenceCategory(
    context: Context,
    attrs: AttributeSet,
) : PreferenceCategory(context, attrs) {
    init {
        isPersistent = true
        layoutResource = R.layout.view_preference_category
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val view = holder.findViewById(R.id.preference_title)
        if (view is TextView) { //  && !view.isInEditMode
            view.text = title
            if (view.isInEditMode) return
            // 09稿改动④：组题色回归 XML 的 on_surface_variant，不再用运行时 accentColor 覆盖
            // 09稿改动③：组间 24dp 纯留白，divider 不再按背景 shiftColor 涂色带
            view.isVisible = !title.isNullOrEmpty()

            val da = holder.findViewById(R.id.preference_divider_above)
            if (da is View) {
                da.isVisible = holder.isDividerAllowedAbove
            }
            val db = holder.findViewById(R.id.preference_divider_below)
            if (db is View) {
                db.isVisible = holder.isDividerAllowedBelow
            }
        }
    }
}
