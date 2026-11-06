package expo.modules.applock

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import android.widget.FrameLayout

/** Opaque surface shared by the overlay and native authentication activity. */
object LockSurface {
  fun create(context: Context, pkg: String, message: String, unlock: () -> Unit, home: () -> Unit): View {
    val dp = context.resources.displayMetrics.density
    fun px(v: Int) = (v * dp).toInt()
    val layout = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      setPadding(px(32), px(48), px(32), px(48))
      setBackgroundColor(Color.rgb(20, 19, 24))
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    val pm = context.packageManager
    val label = try { pm.getApplicationLabel(PackageMetadata.applicationInfo(pm, pkg)).toString() } catch (_: Exception) { pkg }
    try {
      layout.addView(ImageView(context).apply { setImageDrawable(pm.getApplicationIcon(pkg)); contentDescription = null }, LinearLayout.LayoutParams(px(64), px(64)).apply { bottomMargin = px(24) })
    } catch (_: Exception) { /* Still show an opaque lock if the package disappeared. */ }
    fun text(value: String, size: Float, color: Int) = TextView(context).apply {
      text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER
      setPadding(0, px(8), 0, px(8))
    }
    layout.addView(text(label, 28f, Color.WHITE).apply { typeface = Typeface.create("sans-serif", Typeface.NORMAL) })
    layout.addView(text("App Lock", 16f, Color.rgb(208, 188, 255)))
    layout.addView(text(message, 16f, Color.LTGRAY))
    fun button(title: String, action: () -> Unit) = Button(context).apply {
      text = title; isAllCaps = false; setTextColor(Color.rgb(33, 0, 93))
      background = GradientDrawable().apply { setColor(Color.rgb(208, 188, 255)); cornerRadius = px(28).toFloat() }
      setPadding(px(24), px(12), px(24), px(12)); setOnClickListener { action() }
      layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(56)).apply { topMargin = px(20) }
    }
    layout.addView(button("Unlock with Android", unlock))
    layout.addView(button("Go to home screen", home))
    return ScrollView(context).apply {
      setBackgroundColor(Color.rgb(20, 19, 24))
      isFillViewport = true
      addView(layout, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
    }
  }
}
