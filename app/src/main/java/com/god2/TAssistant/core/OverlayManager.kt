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
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )

            val rootLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener { onCancel() }
            }

            val cardLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(60, 40, 60, 60)
                val shape = GradientDrawable().apply {
                    setColor(Color.parseColor("#F2121212"))
                    cornerRadius = 80f
                    setStroke(4, Color.parseColor("#33FFFFFF"))
                }
                background = shape
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(40, 0, 40, 100) }
                setOnClickListener { } 
            }

            tvTitle = TextView(context).apply {
                setTextColor(Color.parseColor("#00E676"))
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 8)
            }

            tvContent = TextView(context).apply {
                setTextColor(Color.WHITE)
                textSize = 22f
                setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL))
            }

            cardLayout.addView(tvTitle)
            cardLayout.addView(tvContent)
            rootLayout.addView(cardLayout)
            overlayView = rootLayout
            windowManager.addView(overlayView, params)
        }

        tvTitle?.text = title.uppercase()
        tvContent?.text = content
        
        if (isProcessing) {
            tvContent?.setTextColor(Color.parseColor("#BBBBBB"))
            val anim = AlphaAnimation(0.5f, 1.0f).apply {
                duration = 500
                repeatCount = AlphaAnimation.INFINITE
                repeatMode = AlphaAnimation.REVERSE
            }
            tvContent?.startAnimation(anim)
        } else {
            tvContent?.setTextColor(Color.WHITE)
            tvContent?.clearAnimation()
        }
    }

    fun updateContent(content: String) {
        tvContent?.text = content
    }

    fun hide() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }
}