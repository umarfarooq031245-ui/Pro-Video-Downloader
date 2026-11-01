package com.pro.videodownloader.player

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.TextView

object P5UiHelper {

    fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density + 0.5f).toInt()
    }

    fun createIcon(
        context: Context,
        drawableRes: Int,
        description: String,
        sizeDp: Int = 24,
        tintColor: Int = Color.WHITE
    ): ImageView {
        return ImageView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                dp(context, sizeDp),
                dp(context, sizeDp)
            )
            setImageDrawable(context.getDrawable(drawableRes))
            imageTintList = ColorStateList.valueOf(tintColor)
            contentDescription = description
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
    }

    fun styleCard(view: View) {
        view.setBackgroundResource(R.drawable.bg_p5_card)
        view.elevation = dp(view.context, 3).toFloat()
    }

    fun stylePrimaryButton(view: View) {
        view.setBackgroundResource(R.drawable.bg_p5_primary_button)
        view.minimumHeight = dp(view.context, 48)
        view.elevation = dp(view.context, 2).toFloat()
        view.isClickable = true
    }

    fun styleInput(view: View) {
        view.setBackgroundResource(R.drawable.bg_p5_input)
        view.minimumHeight = dp(view.context, 48)
    }

    fun styleTitle(textView: TextView) {
        textView.setTextColor(Color.WHITE)
        textView.textSize = 20f
        textView.typeface = Typeface.create(
            "sans-serif-medium",
            Typeface.NORMAL
        )
        textView.gravity = Gravity.CENTER_VERTICAL
    }

    fun styleSubtitle(textView: TextView) {
        textView.setTextColor(Color.LTGRAY)
        textView.textSize = 14f
        textView.gravity = Gravity.CENTER_VERTICAL
    }
}
