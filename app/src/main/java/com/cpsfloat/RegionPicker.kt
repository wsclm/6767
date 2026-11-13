package com.cpsfloat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 点击区域选择：由两个悬浮窗组成。
 *  1. 区域框：窗口本身的位置/大小就是点击区域，拖动移动，拖左上/右下角缩放；
 *  2. 控制条：显示尺寸并带“取消/完成”按钮，始终完整显示在屏幕内（框太小也不会被挡住）。
 * 点“完成”后保存到该窗口当前屏幕方向的区域设置，并同时关闭两个悬浮窗。
 */
class RegionPicker(
    private val ctx: Context,
    private val slot: Int,
    private val onFinish: () -> Unit
) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val minSize = ctx.dp(40)
    private var screenW = 0
    private var screenH = 0
    private var lastHs = -1

    private val boxLp: WindowManager.LayoutParams
    private val box: FrameLayout
    private val label: TextView
    private val tl: Handle
    private val br: Handle

    private val barLp: WindowManager.LayoutParams
    private val bar: LinearLayout
    private val info: TextView

    private class Handle(c: Context) : View(c) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val r = width / 2f
            p.color = Color.WHITE
            canvas.drawCircle(r, r, r, p)
            p.color = Pal.ACCENT
            canvas.drawCircle(r, r, maxOf(r - context.dp(3), 1f), p)
        }
    }

    @Suppress("DEPRECATION")
    private fun realSize(): IntArray {
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        return intArrayOf(dm.widthPixels, dm.heightPixels)
    }

    private fun overlayLp(w: Int, h: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SPLIT_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

    init {
        val sz = realSize()
        screenW = sz[0]
        screenH = sz[1]

        val cfg = Cfg.load(ctx, slot)
        val w0 = (if (cfg.hasRegion) cfg.rw else (screenW * 0.5f).toInt()).coerceIn(minSize, screenW)
        val h0 = (if (cfg.hasRegion) cfg.rh else (screenH * 0.3f).toInt()).coerceIn(minSize, screenH)
        val x0 = (if (cfg.hasRegion) cfg.rx else (screenW - w0) / 2).coerceIn(0, screenW - w0)
        val y0 = (if (cfg.hasRegion) cfg.ry else (screenH - h0) / 2).coerceIn(0, screenH - h0)

        // ---- 区域框 ----
        boxLp = overlayLp(w0, h0).apply { x = x0; y = y0 }
        box = FrameLayout(ctx).apply {
            background = GradientDrawable().apply {
                setColor(0x44D97757)
                setStroke(ctx.dp(3), Pal.ACCENT)
                cornerRadius = ctx.dp(10).toFloat()
            }
        }
        label = TextView(ctx).apply {
            text = "窗口 ${slot + 1} · 点击区域"
            setTextColor(Color.WHITE); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
            gravity = Gravity.CENTER
        }
        box.addView(label, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        tl = Handle(ctx)
        br = Handle(ctx)
        box.addView(br, FrameLayout.LayoutParams(ctx.dp(28), ctx.dp(28), Gravity.BOTTOM or Gravity.END))
        box.addView(tl, FrameLayout.LayoutParams(ctx.dp(28), ctx.dp(28), Gravity.TOP or Gravity.START))

        attachMove(box)
        attachResize(br, topLeft = false)
        attachResize(tl, topLeft = true)

        // ---- 控制条 ----
        info = TextView(ctx).apply {
            setTextColor(Pal.TEXT); textSize = 13f; gravity = Gravity.CENTER
            typeface = Typeface.MONOSPACE
        }
        val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(Ui.button(ctx, "取消", false) { cancel() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        btnRow.addView(
            Ui.button(ctx, "完成") { finish() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = ctx.dp(10) }
        )
        bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Pal.CARD); cornerRadius = ctx.dp(18).toFloat(); setStroke(ctx.dp(1), Pal.ACCENT)
            }
            setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(14), ctx.dp(12))
            minimumWidth = ctx.dp(230)
        }
        bar.addView(info, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        bar.addView(btnRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(10) })
        barLp = overlayLp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        refreshBox()
    }

    /** 区域框改变后：夹在屏幕内、调整角点大小、更新文字并重新摆放控制条 */
    private fun refreshBox() {
        boxLp.width = boxLp.width.coerceIn(minSize, screenW)
        boxLp.height = boxLp.height.coerceIn(minSize, screenH)
        boxLp.x = boxLp.x.coerceIn(0, screenW - boxLp.width)
        boxLp.y = boxLp.y.coerceIn(0, screenH - boxLp.height)

        val hs = (minOf(boxLp.width, boxLp.height) / 3).coerceIn(ctx.dp(16), ctx.dp(28))
        if (hs != lastHs) {
            lastHs = hs
            tl.layoutParams = FrameLayout.LayoutParams(hs, hs, Gravity.TOP or Gravity.START)
            br.layoutParams = FrameLayout.LayoutParams(hs, hs, Gravity.BOTTOM or Gravity.END)
        }
        label.visibility = if (boxLp.width > ctx.dp(130) && boxLp.height > ctx.dp(60)) View.VISIBLE else View.GONE
        info.text = "${boxLp.width} × ${boxLp.height}  @ (${boxLp.x}, ${boxLp.y})"
        try { wm.updateViewLayout(box, boxLp) } catch (_: Throwable) { }
        placeBar()
    }

    /** 控制条优先放在框下方，放不下放上方，再不行放在屏幕底部——始终完整可见。 */
    private fun placeBar() {
        bar.measure(
            View.MeasureSpec.makeMeasureSpec(screenW - ctx.dp(16), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST)
        )
        val bw = bar.measuredWidth
        val bh = bar.measuredHeight
        val gap = ctx.dp(10)
        var by = boxLp.y + boxLp.height + gap
        if (by + bh > screenH) by = boxLp.y - bh - gap
        if (by < 0) by = screenH - bh - ctx.dp(24)
        val bx = (boxLp.x + boxLp.width / 2 - bw / 2).coerceIn(0, (screenW - bw).coerceAtLeast(0))
        barLp.x = bx
        barLp.y = by.coerceIn(0, (screenH - bh).coerceAtLeast(0))
        if (bar.parent != null) {
            try { wm.updateViewLayout(bar, barLp) } catch (_: Throwable) { }
        }
    }

    private fun attachMove(v: View) {
        var dx = 0f
        var dy = 0f
        var sx = 0
        var sy = 0
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dx = e.rawX; dy = e.rawY; sx = boxLp.x; sy = boxLp.y
                }
                MotionEvent.ACTION_MOVE -> {
                    boxLp.x = sx + (e.rawX - dx).toInt()
                    boxLp.y = sy + (e.rawY - dy).toInt()
                    refreshBox()
                }
            }
            true
        }
    }

    private fun attachResize(v: View, topLeft: Boolean) {
        var dx = 0f
        var dy = 0f
        var sx = 0
        var sy = 0
        var sw = 0
        var sh = 0
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dx = e.rawX; dy = e.rawY
                    sx = boxLp.x; sy = boxLp.y; sw = boxLp.width; sh = boxLp.height
                }
                MotionEvent.ACTION_MOVE -> {
                    val mx = (e.rawX - dx).toInt()
                    val my = (e.rawY - dy).toInt()
                    if (topLeft) {
                        val nw = (sw - mx).coerceIn(minSize, sx + sw)
                        val nh = (sh - my).coerceIn(minSize, sy + sh)
                        boxLp.x = sx + sw - nw
                        boxLp.y = sy + sh - nh
                        boxLp.width = nw
                        boxLp.height = nh
                    } else {
                        boxLp.width = (sw + mx).coerceIn(minSize, screenW - sx)
                        boxLp.height = (sh + my).coerceIn(minSize, screenH - sy)
                    }
                    refreshBox()
                }
            }
            true
        }
    }

    fun show() {
        try {
            wm.addView(box, boxLp)
            wm.addView(bar, barLp)
            placeBar()
        } catch (_: Throwable) {
            dismiss()
            onFinish()
        }
    }

    fun dismiss() {
        try { wm.removeView(bar) } catch (_: Throwable) { }
        try { wm.removeView(box) } catch (_: Throwable) { }
    }

    private fun cancel() {
        dismiss()
        onFinish()
    }

    private fun finish() {
        Cfg.putRegion(ctx, slot, boxLp.x, boxLp.y, boxLp.width, boxLp.height)
        dismiss()
        onFinish()
    }
}
