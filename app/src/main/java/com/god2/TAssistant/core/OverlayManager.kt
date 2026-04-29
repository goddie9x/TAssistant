package com.god2.TAssistant.core
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class OverlayManager(private val context: Context, private val onCancel: () -> Unit) {
    private var windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var tvTitle: TextView? = null
    private var tvContent: TextView? = null

    fun showOverlay(title: String, content: String, isProcessing: Boolean = false) {
        if (overlayView == null) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.BOTTOM }

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM
                setBackgroundColor(Color.TRANSPARENT)
                setOnTouchListener { _, _ -> onCancel(); true }
            }

            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(60, 40, 60, 60)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#F2121212"))
                    cornerRadius = 80f
                    setStroke(4, Color.parseColor("#33FFFFFF"))
                }
                val lp = LinearLayout.LayoutParams(-1, -2)
                lp.setMargins(40, 0, 40, 100)
                layoutParams = lp
                setOnClickListener { } 
            }

            tvTitle = TextView(context).apply {
                setTextColor(Color.parseColor("#00E676")); textSize = 12f; setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 16)
            }

            val scrollView = ScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(-1, -2)
                isScrollbarFadingEnabled = false
            }

            tvContent = TextView(context).apply {
                setTextColor(Color.WHITE); textSize = 18f; setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL))
            }

            scrollView.addView(tvContent)
            card.addView(tvTitle); card.addView(scrollView); root.addView(card)
            overlayView = root
            try { windowManager.addView(overlayView, params) } catch (e: Exception) {}
        }
        tvTitle?.text = title.uppercase()
        tvContent?.text = content
        if (isProcessing) {
            val anim = AlphaAnimation(0.5f, 1.0f).apply { duration = 500; repeatCount = -1; repeatMode = 2 }
            tvContent?.startAnimation(anim)
        } else { tvContent?.clearAnimation() }
    }

    fun updateContent(content: String) {
        tvContent?.text = content
    }

    fun hide() {
        overlayView?.let { try { windowManager.removeView(it) } catch (e: Exception) {}; overlayView = null }
    }
}