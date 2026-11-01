package com.pro.videodownloader.player

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView

/**
 * Phase 6: Premium Purple / Violet / Magenta design helpers.
 * Visual-only styling — no business logic lives here.
 */
object P6UiHelper {

    fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density + 0.5f).toInt()
    }

    /** Gradient tile (56dp) with a centered white glyph — for shortcut tiles. */
    fun createIconTile(context: Context, glyphRes: Int, description: String, tileDp: Int = 56): ImageView {
        return ImageView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(dp(context, tileDp), dp(context, tileDp))
            setBackgroundResource(R.drawable.bg_p6_icon_tile)
            setImageResource(glyphRes)
            setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14))
            contentDescription = description
            scaleType = ImageView.ScaleType.FIT_CENTER
            elevation = dp(context, 2).toFloat()
        }
    }

    fun styleCard(view: View) {
        view.setBackgroundResource(R.drawable.bg_p6_card)
        view.elevation = dp(view.context, 3).toFloat()
    }

    fun stylePrimaryButton(view: View) {
        view.setBackgroundResource(R.drawable.bg_p6_primary_button)
        view.minimumHeight = dp(view.context, 48)
        view.elevation = dp(view.context, 2).toFloat()
        view.isClickable = true
    }

    fun styleInput(view: View) {
        view.setBackgroundResource(R.drawable.bg_p6_input)
        view.minimumHeight = dp(view.context, 48)
    }

    fun styleTitle(textView: TextView) {
        textView.setTextColor(Color.parseColor("#F5F3FF"))
        textView.textSize = 20f
        textView.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textView.gravity = Gravity.CENTER_VERTICAL
    }

    fun styleSubtitle(textView: TextView) {
        textView.setTextColor(Color.parseColor("#B3AED6"))
        textView.textSize = 13f
        textView.gravity = Gravity.CENTER_VERTICAL
    }

    fun styleSectionLabel(textView: TextView) {
        textView.setTextColor(Color.parseColor("#B3AED6"))
        textView.textSize = 13f
        textView.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    /** Bottom-nav active tab: gradient pill. Inactive tabs: subtle surface. */
    fun setNavActive(btn: Button) {
        btn.setBackgroundResource(R.drawable.bg_p6_primary_button)
        btn.setTextColor(Color.WHITE)
    }

    fun setNavInactive(btn: Button) {
        btn.setBackgroundResource(R.drawable.bg_p6_nav_inactive)
        btn.setTextColor(Color.parseColor("#B3AED6"))
    }
}
