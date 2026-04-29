package com.god2.TAssistant.core
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.god2.TAssistant.R

class OverlayManager(private val context: Context) {
    private var windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var textView: TextView? = null

    fun showText(text: String) {
        if (overlayView == null) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM
                y = 100
            }
            
            // Tạo một TextView đơn giản để hiển thị
            textView = TextView(context).apply {
                setBackgroundColor(0xAA000000.toInt()) // Màu đen mờ
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 18f
                setPadding(40, 20, 40, 20)
                textAlignment = View.TEXT_ALIGNMENT_CENTER
            }
            overlayView = textView
            windowManager.addView(overlayView, params)
        }
        textView?.text = text
    }

    fun hide() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }
}
