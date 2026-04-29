package com.god2.TAssistant.core
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.widget.*

class OverlayManager(private val context: Context, private val onCancel: () -> Unit) {
    private var windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var tvTitle: TextView? = null
    private var tvContent: TextView? = null
    private var etCommand: EditText? = null
    private var btnConfirm: Button? = null
    private var tvCountdown: TextView? = null
    
    private val autoHandler = Handler(Looper.getMainLooper())

    private fun createOverlayIfNeeded() {
        if (overlayView != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
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
                setStroke(4, Color.parseColor("#3300E676"))
            }
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.setMargins(40, 0, 40, 100)
            layoutParams = lp
            setOnClickListener { } // Chặn click xuyên thủng
        }

        tvTitle = TextView(context).apply { setTextColor(Color.parseColor("#00E676")); textSize = 12f; setTypeface(null, Typeface.BOLD); setPadding(0, 0, 0, 16) }
        val scrollView = ScrollView(context).apply { layoutParams = LinearLayout.LayoutParams(-1, -2); isScrollbarFadingEnabled = false }
        tvContent = TextView(context).apply { setTextColor(Color.WHITE); textSize = 18f; setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL)) }
        scrollView.addView(tvContent)

        etCommand = EditText(context).apply {
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00E676"))
            textSize = 18f
            visibility = View.GONE
        }
        
        btnConfirm = Button(context).apply {
            text = "EXECUTE NOW"
            setBackgroundColor(Color.parseColor("#00E676"))
            setTextColor(Color.BLACK)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 20 }
        }
        
        tvCountdown = TextView(context).apply {
            setTextColor(Color.GRAY)
            textSize = 12f
            visibility = View.GONE
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }

        card.addView(tvTitle); card.addView(scrollView); card.addView(etCommand); card.addView(btnConfirm); card.addView(tvCountdown)
        root.addView(card)
        overlayView = root
        try { windowManager.addView(overlayView, params) } catch (e: Exception) {}
    }

    fun showEditableCommand(cmd: String, onExecute: (String) -> Unit) {
        createOverlayIfNeeded()
        autoHandler.removeCallbacksAndMessages(null)
        
        tvTitle?.text = "COMMAND DETECTED"
        tvContent?.visibility = View.GONE
        etCommand?.visibility = View.VISIBLE
        btnConfirm?.visibility = View.VISIBLE
        tvCountdown?.visibility = View.VISIBLE
        
        etCommand?.setText(cmd)
        
        var ticks = 5
        val executeFn = {
            autoHandler.removeCallbacksAndMessages(null)
            showOverlay("PROCESSING...", "Executing...", true)
            onExecute(etCommand?.text.toString())
        }

        btnConfirm?.setOnClickListener { executeFn() }
        etCommand?.setOnTouchListener { _, _ -> 
            autoHandler.removeCallbacksAndMessages(null) // Chạm vào là hủy đếm ngược
            tvCountdown?.text = "Auto-execute paused. Edit and press Execute."
            false 
        }

        val runnable = object : Runnable {
            override fun run() {
                if (ticks > 0) {
                    tvCountdown?.text = "Auto-executing in $ticks s..."
                    ticks--
                    autoHandler.postDelayed(this, 1000)
                } else {
                    executeFn()
                }
            }
        }
        autoHandler.post(runnable)
    }

    fun showOverlay(title: String, content: String, isProcessing: Boolean = false) {
        createOverlayIfNeeded()
        autoHandler.removeCallbacksAndMessages(null)
        
        etCommand?.visibility = View.GONE
        btnConfirm?.visibility = View.GONE
        tvCountdown?.visibility = View.GONE
        tvContent?.visibility = View.VISIBLE
        
        tvTitle?.text = title.uppercase()
        tvContent?.text = content
        
        if (isProcessing) {
            val anim = AlphaAnimation(0.5f, 1.0f).apply { duration = 500; repeatCount = -1; repeatMode = 2 }
            tvContent?.startAnimation(anim)
        } else { tvContent?.clearAnimation() }
    }

    fun updateContent(content: String) { tvContent?.text = content }

    fun hide() {
        autoHandler.removeCallbacksAndMessages(null)
        overlayView?.let { try { windowManager.removeView(it) } catch (e: Exception) {}; overlayView = null }
    }
}