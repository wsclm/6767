package com.cpsfloat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * CPS 显示控件（悬浮窗和软件内预览共用）。
 *
 * 绘制方式：背景圆角矩形 [boxW × boxH] 和文字都以控件中心为基点居中绘制，文字永远画在背景上方，
 * 不会被任何背景边缘遮住。控件本身的大小 = max(背景, 文字)，所以：
 *  - 调背景宽度 / 高度：只会围绕中心向两侧对称缩放；
 *  - 背景比文字小时，文字会完整露在背景外面，不会被截断；
 *  - 整体缩放（scalePct）：背景、文字、圆角、内边距一起按比例缩放，同样以中心为基点。
 */
class CpsView(c: Context) : View(c) {

    var text: String = ""
        set(v) {
            field = v
            invalidate()
        }

    /** 额外的统一缩小系数，只用于软件内预览（让大尺寸也能完整显示在预览框里）。 */
    var fit = 1f

    /** 背景最大宽/高（像素，一般为屏幕宽/高），悬浮窗用来防止背景超出屏幕。 */
    var limitW = Int.MAX_VALUE
    var limitH = Int.MAX_VALUE

    private var sizeSp = 22
    private var textColor = 0xFFFFFFFF.toInt()
    private var bgColor = 0xAA000000.toInt()
    private var radiusDp = 12
    private var bgW = 0
    private var bgH = 0
    private var scalePct = 100
    private var fontKey = "sans-serif"
    private var bold = true
    private var appliedFont: String? = null
    private var appliedBold = false

    private val dens = c.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var boxW = 1f
    private var boxH = 1f
    private var contentW = 1
    private var contentH = 1

    fun configure(x: SlotCfg) {
        sizeSp = x.sizeSp
        textColor = x.textColor
        bgColor = x.bgColor
        radiusDp = x.radiusDp
        bgW = x.bgW
        bgH = x.bgH
        scalePct = x.scalePct
        fontKey = x.font
        bold = x.bold
        if (appliedFont != fontKey || appliedBold != bold) {
            paint.typeface = Fonts.typeface(fontKey, bold)
            appliedFont = fontKey
            appliedBold = bold
        }
        invalidate()
    }

    private fun scale(): Float = (max(scalePct, 10) / 100f) * fit

    /** 按当前配置与文字重新计算背景和控件大小。 */
    private fun compute() {
        val sc = scale()
        paint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, sizeSp.toFloat(), resources.displayMetrics
        ) * sc
        val fm = paint.fontMetrics
        val textW = if (text.isEmpty()) 0f else paint.measureText(text)
        val textH = fm.descent - fm.ascent
        val padX = 12f * dens * sc
        val padY = 6f * dens * sc
        val autoW = max(textW + 2f * padX, 24f * dens * sc)
        val autoH = textH + 2f * padY
        boxW = if (bgW > 0) bgW * dens * sc else autoW
        boxH = if (bgH > 0) bgH * dens * sc else autoH
        boxW = min(boxW, limitW.toFloat()).coerceAtLeast(1f)
        boxH = min(boxH, limitH.toFloat()).coerceAtLeast(1f)
        contentW = ceil(max(boxW, textW)).toInt().coerceAtLeast(1)
        contentH = ceil(max(boxH, textH)).toInt().coerceAtLeast(1)
    }

    /** 控件需要的大小 [宽, 高]（像素）。悬浮窗据此设置窗口大小。 */
    fun measureContent(): IntArray {
        compute()
        return intArrayOf(contentW, contentH)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        compute()
        setMeasuredDimension(
            resolveSize(contentW, widthMeasureSpec),
            resolveSize(contentH, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        compute()
        val sc = scale()
        val cx = width / 2f
        val cy = height / 2f
        // 背景：以中心为基点
        rect.set(cx - boxW / 2f, cy - boxH / 2f, cx + boxW / 2f, cy + boxH / 2f)
        val rad = min(radiusDp * dens * sc, min(boxW, boxH) / 2f)
        bgPaint.color = bgColor
        canvas.drawRoundRect(rect, rad, rad, bgPaint)
        // 文字：画在背景之上，同样居中
        if (text.isNotEmpty()) {
            paint.color = textColor
            val fm = paint.fontMetrics
            val baseline = cy - (fm.ascent + fm.descent) / 2f
            canvas.drawText(text, cx, baseline, paint)
        }
    }
}
