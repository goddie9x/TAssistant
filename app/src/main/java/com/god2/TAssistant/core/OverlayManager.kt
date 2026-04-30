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
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.inputmethod.InputMethodManager
import android.widget.*

class OverlayManager(private val context: Context, private val onCancel: () -> Unit) {
    private var windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var tvTitle: TextView? = null
    private var tvContent: TextView? = null
    private var etCommand: EditText? = null
    private var btnConfirm: Button? = null
    
    private val autoHandler = Handler(Looper.getMainLooper())

    private fun createOverlayIfNeeded() {
        if (overlayView != null) return
        
        // NÂNG CẤP: MATCH_PARENT toàn màn hình để bắt mọi cú chạm ra ngoài
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setBackgroundColor(Color.TRANSPARENT)
            // Chạm vào phần màn hình trống (ngoài Card) là lập tức hủy lệnh
            setOnClickListener { onCancel() }
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
            isClickable = true // Chặn không cho click lọt xuống nền Root
        }

        tvTitle = TextView(context).apply { setTextColor(Color.parseColor("#00E676")); textSize = 11f; setTypeface(null, Typeface.BOLD); setPadding(0, 0, 0, 8) }
        tvContent = TextView(context).apply { setTextColor(Color.WHITE); textSize = 20f; setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)) }

        etCommand = EditText(context).apply {
            setTextColor(Color.WHITE); textSize = 20f
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00E676"))
            visibility = View.GONE
        }
        
        btnConfirm = Button(context).apply {
            text = "RUN NOW"; setBackgroundColor(Color.parseColor("#00E676")); setTextColor(Color.BLACK)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 20 }
        }

        card.addView(tvTitle); card.addView(tvContent); card.addView(etCommand); card.addView(btnConfirm)
        root.addView(card)
        overlayView = root
        try { windowManager.addView(overlayView, params) } catch (e: Exception) {}
    }

    fun showFlashCommand(cmd: String, onExecute: (String) -> Unit) {
        createOverlayIfNeeded()
        autoHandler.removeCallbacksAndMessages(null)
        
        tvTitle?.text = "QUICK COMMAND"
        tvContent?.text = cmd
        tvContent?.visibility = View.VISIBLE
        etCommand?.visibility = View.GONE
        btnConfirm?.visibility = View.GONE

        val card = (overlayView as LinearLayout).getChildAt(0) as LinearLayout
        
        val executeNow = {
            autoHandler.removeCallbacksAndMessages(null)
            showOverlay("WORKING", "Processing...", true)
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(etCommand?.windowToken, 0)
            onExecute(etCommand?.text.toString().ifEmpty { cmd })
        }

        // NÂNG CẤP: Bắt ACTION_DOWN (chạm tay xuống là kích hoạt ngay lập tức)
        card.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                autoHandler.removeCallbacksAndMessages(null) // Phanh gấp bộ đếm
                card.setOnTouchListener(null) // Gỡ listener để gõ phím không bị loạn
                
                tvTitle?.text = "EDIT COMMAND"
                tvContent?.visibility = View.GONE
                etCommand?.apply { 
                    visibility = View.VISIBLE
                    setText(cmd)
                    setSelection(cmd.length)
                    requestFocus()
                    // Delay nhẹ 100ms để layout kịp vẽ rồi mới đẩy bàn phím ảo lên
                    postDelayed({
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                    }, 100)
                }
                btnConfirm?.apply { visibility = View.VISIBLE; setOnClickListener { executeNow() } }
            }
            true // Báo hiệu đã nuốt trọn sự kiện chạm này
        }

        // Đếm ngược nửa giây
        autoHandler.postDelayed({ executeNow() }, 550)
    }

    fun showOverlay(title: String, content: String, isProcessing: Boolean = false) {
        createOverlayIfNeeded()
        autoHandler.removeCallbacksAndMessages(null)
        
        etCommand?.visibility = View.GONE
        btnConfirm?.visibility = View.GONE
        tvContent?.visibility = View.VISIBLE
        
        tvTitle?.text = title.uppercase()
        tvContent?.text = content
        
        val card = (overlayView as LinearLayout).getChildAt(0) as LinearLayout
        card.setOnTouchListener(null) // Trạng thái này không cần bắt edit nữa

        if (isProcessing) {
            val anim = AlphaAnimation(0.6f, 1.0f).apply { duration = 400; repeatCount = -1; repeatMode = 2 }
            tvContent?.startAnimation(anim)
        } else { tvContent?.clearAnimation() }
    }

    fun updateContent(content: String) { tvContent?.text = content }

    fun hide() {
        autoHandler.removeCallbacksAndMessages(null)
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        etCommand?.windowToken?.let { imm.hideSoftInputFromWindow(it, 0) }
        overlayView?.let { try { windowManager.removeView(it) } catch (e: Exception) {}; overlayView = null }
    }
}