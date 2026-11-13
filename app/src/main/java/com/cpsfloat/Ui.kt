package com.cpsfloat

import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*

/** Claude 桌面端深色主题配色 */
object Pal {
    const val BG = 0xFF262624.toInt()          // 页面背景
    const val CARD = 0xFF30302E.toInt()        // 卡片
    const val FIELD = 0xFF1F1E1D.toInt()       // 输入框/凹陷区域
    const val LINE = 0xFF3E3E38.toInt()        // 分割线/描边
    const val ACCENT = 0xFFD97757.toInt()      // Claude 陶土橙
    const val ACCENT_SOFT = 0xFF523E36.toInt() // 橙色淡底
    const val TEXT = 0xFFFAF9F5.toInt()        // 主文字（象牙白）
    const val SUB = 0xFFA3A198.toInt()         // 次级文字
    const val OFF = 0xFF55544F.toInt()         // 关闭状态的轨道
    const val OK = 0xFF7BC47F.toInt()
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun rounded(c: Context, color: Int, radiusDp: Int): GradientDrawable =
    GradientDrawable().apply { setColor(color); cornerRadius = c.dp(radiusDp).toFloat() }

fun lp(w: Int, h: Int, top: Int = 0, ctx: Context? = null): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(w, h).apply { if (ctx != null) topMargin = ctx.dp(top) }

fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

fun lerpColor(a: Int, b: Int, t: Float): Int = Color.argb(
    lerpF(Color.alpha(a).toFloat(), Color.alpha(b).toFloat(), t).toInt(),
    lerpF(Color.red(a).toFloat(), Color.red(b).toFloat(), t).toInt(),
    lerpF(Color.green(a).toFloat(), Color.green(b).toFloat(), t).toInt(),
    lerpF(Color.blue(a).toFloat(), Color.blue(b).toFloat(), t).toInt()
)

/**
 * 液态玻璃质感（近似实现）：通透的染色底 + 白色渐变 + 顶部镜面高光 + 底部反光 +
 * 边缘折射光（左上、右下较亮）+ 柔和投影；按下时更通透。
 */
object Glass {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rr = RectF()

    fun draw(
        canvas: Canvas, r: RectF, radius0: Float, tint: Int,
        baseAlpha: Int, pressed: Float, d: Float, shadow: Boolean
    ) {
        val press = pressed.coerceIn(0f, 1f)
        val radius = minOf(radius0, r.height() / 2f)
        p.style = Paint.Style.FILL
        p.shader = null

        if (shadow) {
            for (k in 3 downTo 1) {
                rr.set(r.left - k * d * 0.6f, r.top + k * d * 0.5f, r.right + k * d * 0.6f, r.bottom + k * d * 1.4f)
                p.color = Color.argb(16 - k * 3, 0, 0, 0)
                canvas.drawRoundRect(rr, radius + k * d * 0.6f, radius + k * d * 0.6f, p)
            }
        }

        val a = (baseAlpha * (1f - 0.62f * press)).toInt().coerceIn(0, 255)
        p.color = Color.argb(a, Color.red(tint), Color.green(tint), Color.blue(tint))
        canvas.drawRoundRect(r, radius, radius, p)

        p.shader = LinearGradient(
            0f, r.top, 0f, r.bottom,
            Color.argb((90 * (1f - 0.4f * press)).toInt(), 255, 255, 255), Color.argb(14, 255, 255, 255),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(r, radius, radius, p)

        rr.set(r.left + d * 1.5f, r.top + d * 1.2f, r.right - d * 1.5f, r.top + r.height() * 0.5f)
        if (rr.height() > 1f) {
            p.shader = LinearGradient(
                0f, rr.top, 0f, rr.bottom,
                Color.argb((120 * (1f - 0.5f * press)).toInt(), 255, 255, 255), Color.argb(0, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rr, radius * 0.9f, radius * 0.9f, p)
        }

        rr.set(r.left + d * 2f, r.top + r.height() * 0.62f, r.right - d * 2f, r.bottom - d)
        if (rr.height() > 1f) {
            p.shader = LinearGradient(
                0f, rr.top, 0f, rr.bottom,
                Color.argb(0, 255, 255, 255), Color.argb(55, 255, 255, 255), Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rr, radius * 0.8f, radius * 0.8f, p)
        }

        p.shader = LinearGradient(
            r.left, r.top, r.right, r.bottom,
            intArrayOf(
                Color.argb(230, 255, 255, 255), Color.argb(24, 255, 255, 255),
                Color.argb(24, 255, 255, 255), Color.argb(170, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.35f, 0.65f, 1f), Shader.TileMode.CLAMP
        )
        p.style = Paint.Style.STROKE
        p.strokeWidth = d * 1.2f
        rr.set(r.left + d * 0.6f, r.top + d * 0.6f, r.right - d * 0.6f, r.bottom - d * 0.6f)
        val rim = maxOf(radius - d * 0.6f, 0f)
        canvas.drawRoundRect(rr, rim, rim, p)

        p.shader = null
        p.style = Paint.Style.FILL
    }
}

/** iOS 原生样式开关：51×31 的胶囊轨道 + 白色圆形滑块（带轻微投影），按下时滑块略微拉长。 */
class IosSwitch(c: Context) : View(c) {
    var onChange: ((Boolean) -> Unit)? = null
    var isOn = false
        private set

    private val d = c.resources.displayMetrics.density
    private var pos = 0f
    private var press = 0f
    private var posAnim: ValueAnimator? = null
    private var pressAnim: ValueAnimator? = null
    private var startX = 0f
    private var moved = false
    private val slop = ViewConfiguration.get(c).scaledTouchSlop
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun setState(on: Boolean, animate: Boolean) {
        isOn = on
        posAnim?.cancel()
        val target = if (on) 1f else 0f
        if (!animate) {
            pos = target; invalidate(); return
        }
        posAnim = ValueAnimator.ofFloat(pos, target).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { pos = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    fun toggle() {
        val now = !isOn
        setState(now, true)
        onChange?.invoke(now)
    }

    private fun pressTo(v: Float) {
        pressAnim?.cancel()
        pressAnim = ValueAnimator.ofFloat(press, v).apply {
            duration = 150
            addUpdateListener { press = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((51f * d).toInt(), (31f * d).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val t = pos.coerceIn(0f, 1f)
        paint.style = Paint.Style.FILL
        paint.color = lerpColor(Pal.OFF, Pal.ACCENT, t)
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, paint)

        val pad = 2f * d
        val baseW = h - 2f * pad
        val thW = baseW + press * 4f * d
        val thH = baseW
        val left = pad
        val right = w - pad - thW
        val tl = lerpF(left, right, pos.coerceIn(0f, 1f))
        val tt = (h - thH) / 2f
        // 投影
        for (k in 3 downTo 1) {
            rect.set(tl - k * 0.4f * d, tt + k * 0.6f * d, tl + thW + k * 0.4f * d, tt + thH + k * 1.2f * d)
            paint.color = Color.argb(14, 0, 0, 0)
            canvas.drawRoundRect(rect, thH / 2f, thH / 2f, paint)
        }
        rect.set(tl, tt, tl + thW, tt + thH)
        paint.color = Color.WHITE
        canvas.drawRoundRect(rect, thH / 2f, thH / 2f, paint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                startX = e.x; moved = false
                posAnim?.cancel()
                pressTo(1f)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!moved && Math.abs(e.x - startX) > slop) moved = true
                if (moved) {
                    val travel = width - 4f * d - (height - 4f * d)
                    pos = ((e.x - 2f * d - (height - 4f * d) / 2f) / travel).coerceIn(0f, 1f)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                pressTo(0f)
                val old = isOn
                val now = if (moved) pos > 0.5f else !old
                setState(now, true)
                if (now != old) onChange?.invoke(now)
            }
            MotionEvent.ACTION_CANCEL -> {
                pressTo(0f)
                setState(isOn, true)
            }
        }
        return true
    }
}

/** 拖动条：滑块是液态玻璃质感的宽胶囊，按下时放大并更通透。 */
class GlassSeekBar(c: Context) : View(c) {
    var max = 100
    var progress = 0
        private set
    var onUser: ((Int) -> Unit)? = null

    private val d = c.resources.displayMetrics.density
    private var press = 0f
    private var pressAnim: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val thumb = RectF()
    private val padX = 24f * d

    fun setProgressValue(v: Int) {
        progress = v.coerceIn(0, max)
        invalidate()
    }

    private fun pressTo(v: Float) {
        pressAnim?.cancel()
        pressAnim = ValueAnimator.ofFloat(press, v).apply {
            duration = 160
            addUpdateListener { press = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (44f * d).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cy = h / 2f
        val frac = if (max > 0) progress.toFloat() / max else 0f
        val cx = padX + frac * (w - 2f * padX)
        val trackH = 6f * d
        paint.style = Paint.Style.FILL
        paint.shader = null
        rect.set(4f * d, cy - trackH / 2f, w - 4f * d, cy + trackH / 2f)
        paint.color = Pal.OFF
        canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, paint)
        rect.set(4f * d, cy - trackH / 2f, maxOf(cx, 4f * d + trackH), cy + trackH / 2f)
        paint.color = Pal.ACCENT
        canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, paint)

        val thW = lerpF(38f, 46f, press) * d
        val thH = lerpF(26f, 32f, press) * d
        thumb.set(cx - thW / 2f, cy - thH / 2f, cx + thW / 2f, cy + thH / 2f)
        Glass.draw(canvas, thumb, thH / 2f, Color.WHITE, 235, press, d, true)
    }

    private fun setFrom(x: Float) {
        if (max <= 0) return
        val frac = ((x - padX) / (width - 2f * padX)).coerceIn(0f, 1f)
        val v = Math.round(frac * max)
        if (v != progress) {
            progress = v
            invalidate()
            onUser?.invoke(v)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                pressTo(1f); setFrom(e.x)
            }
            MotionEvent.ACTION_MOVE -> setFrom(e.x)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> pressTo(0f)
        }
        return true
    }
}

object Ui {
    fun card(c: Context): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(Pal.CARD); cornerRadius = c.dp(20).toFloat(); setStroke(c.dp(1), Pal.LINE)
        }
        setPadding(c.dp(18), c.dp(16), c.dp(18), c.dp(16))
    }

    fun title(c: Context, t: String) = TextView(c).apply {
        text = t; setTextColor(Pal.TEXT); textSize = 16f; typeface = Typeface.DEFAULT_BOLD
    }

    fun sub(c: Context, t: String) = TextView(c).apply {
        text = t; setTextColor(Pal.SUB); textSize = 12.5f
    }

    fun label(c: Context, t: String) = TextView(c).apply {
        text = t; setTextColor(Pal.TEXT); textSize = 14.5f
    }

    fun divider(c: Context) = View(c).apply {
        setBackgroundColor(Pal.LINE)
        layoutParams = lp(ViewGroup.LayoutParams.MATCH_PARENT, 1, 12, c).apply { bottomMargin = c.dp(12) }
    }

    fun button(c: Context, t: String, filled: Boolean = true, onClick: () -> Unit) = TextView(c).apply {
        text = t; textSize = 14.5f; gravity = Gravity.CENTER
        setTextColor(if (filled) Color.WHITE else Pal.ACCENT)
        typeface = Typeface.DEFAULT_BOLD
        background = rounded(c, if (filled) Pal.ACCENT else Pal.ACCENT_SOFT, 14)
        setPadding(c.dp(16), c.dp(11), c.dp(16), c.dp(11))
        setOnClickListener { onClick() }
    }

    fun switchRow(c: Context, t: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        col.addView(label(c, t))
        if (desc != null) col.addView(sub(c, desc))
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = IosSwitch(c)
        sw.setState(checked, false)
        sw.onChange = onChange
        row.addView(sw, LinearLayout.LayoutParams(c.dp(51), c.dp(31)))
        row.setOnClickListener { sw.toggle() }
        return row
    }

    fun segmented(c: Context, labels: List<String>, sel: Int, onSel: (Int) -> Unit): LinearLayout {
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Pal.FIELD); cornerRadius = c.dp(14).toFloat(); setStroke(c.dp(1), Pal.LINE)
            }
            setPadding(c.dp(4), c.dp(4), c.dp(4), c.dp(4))
        }
        val items = labels.map { t ->
            TextView(c).apply {
                text = t; gravity = Gravity.CENTER; textSize = 13.5f
                setPadding(c.dp(6), c.dp(9), c.dp(6), c.dp(9))
            }
        }
        fun paint(s: Int) {
            items.forEachIndexed { i, tv ->
                if (i == s) {
                    tv.background = rounded(c, Pal.ACCENT, 11)
                    tv.setTextColor(Color.WHITE); tv.typeface = Typeface.DEFAULT_BOLD
                } else {
                    tv.background = null; tv.setTextColor(Pal.SUB); tv.typeface = Typeface.DEFAULT
                }
            }
        }
        items.forEachIndexed { i, tv ->
            tv.setOnClickListener { paint(i); onSel(i) }
            box.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        paint(sel)
        return box
    }

    /** 拖动条 + 旁边的数值（点击即可直接输入数字）。 */
    fun sliderRow(
        c: Context, name: String, unit: String,
        lo: Int, hi: Int, hard: Int, init: Int, onChange: (Int) -> Unit
    ): View {
        val root = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        root.addView(label(c, name))
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        var cur = init.coerceIn(lo, hard)
        var internal = false

        val bar = GlassSeekBar(c)
        bar.max = hi - lo
        bar.setProgressValue(cur.coerceIn(lo, hi) - lo)

        val et = EditText(c).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(cur.toString())
            setTextColor(Pal.TEXT); textSize = 15f; gravity = Gravity.CENTER
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
            background = rounded(c, Pal.FIELD, 10)
            setPadding(c.dp(6), c.dp(7), c.dp(6), c.dp(7))
        }
        val u = TextView(c).apply { text = unit; setTextColor(Pal.SUB); textSize = 13f }

        fun commit(v: Int) {
            if (v == cur) return
            cur = v
            onChange(v)
        }
        fun fixText() {
            val v = (et.text.toString().toIntOrNull() ?: cur).coerceIn(lo, hard)
            internal = true
            et.setText(v.toString())
            bar.setProgressValue(v.coerceIn(lo, hi) - lo)
            internal = false
            commit(v)
        }

        bar.onUser = { p ->
            val v = p + lo
            internal = true
            et.setText(v.toString())
            internal = false
            commit(v)
        }
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (internal) return
                val v = s.toString().toIntOrNull() ?: return
                if (v < lo || v > hard) return
                internal = true
                bar.setProgressValue(v.coerceIn(lo, hi) - lo)
                internal = false
                commit(v)
            }
        })
        et.setOnEditorActionListener { v, a, _ ->
            if (a == EditorInfo.IME_ACTION_DONE) {
                fixText(); v.clearFocus()
                (c.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(v.windowToken, 0)
                true
            } else false
        }
        et.setOnFocusChangeListener { _, f -> if (!f) fixText() }

        row.addView(bar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(et, LinearLayout.LayoutParams(c.dp(62), ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = c.dp(8) })
        row.addView(u, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = c.dp(4) })
        root.addView(row)
        return root
    }

    /** 颜色行：点击弹出调色板，确定后才应用。 */
    fun colorRow(c: Context, t: String, color: Int, onPick: (Int) -> Unit): View {
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, c.dp(4), 0, c.dp(4))
        }
        row.addView(label(c, t), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = SwatchView(c)
        sw.color = color
        row.addView(sw, LinearLayout.LayoutParams(c.dp(44), c.dp(30)))
        row.setOnClickListener {
            ColorDialog.show(c, t, sw.color) { picked -> sw.color = picked; onPick(picked) }
        }
        return row
    }

    fun navRow(c: Context, t: String, value: String, onValue: ((TextView) -> Unit)? = null, onClick: () -> Unit): View {
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, c.dp(8), 0, c.dp(8))
        }
        row.addView(label(c, t), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val v = TextView(c).apply { text = value; setTextColor(Pal.ACCENT); textSize = 14f }
        onValue?.invoke(v)
        row.addView(v)
        row.addView(TextView(c).apply { text = "  ›"; setTextColor(Pal.ACCENT); textSize = 14f })
        row.setOnClickListener { onClick() }
        return row
    }
}

fun drawChecker(canvas: Canvas, w: Float, h: Float, cell: Float) {
    val p = Paint()
    var y = 0f
    var r = 0
    while (y < h) {
        var x = 0f
        var cI = r
        while (x < w) {
            p.color = if (cI % 2 == 0) 0xFFFFFFFF.toInt() else 0xFFD9DCE8.toInt()
            canvas.drawRect(x, y, minOf(x + cell, w), minOf(y + cell, h), p)
            x += cell; cI++
        }
        y += cell; r++
    }
}

class SwatchView(c: Context) : View(c) {
    var color: Int = Color.WHITE
        set(v) { field = v; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = c.dp(1).toFloat(); this.color = 0x55FFFFFF
    }
    private val path = Path()
    override fun onDraw(canvas: Canvas) {
        val r = context.dp(8).toFloat()
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        path.reset(); path.addRoundRect(rect, r, r, Path.Direction.CW)
        canvas.save(); canvas.clipPath(path)
        drawChecker(canvas, width.toFloat(), height.toFloat(), context.dp(6).toFloat())
        paint.color = color
        canvas.drawRect(rect, paint)
        canvas.restore()
        rect.inset(0.5f, 0.5f)
        canvas.drawRoundRect(rect, r, r, border)
    }
}

/** 饱和度/明度方块 */
private class SvView(c: Context, val st: ColorState) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val rr = context.dp(12).toFloat()
        path.reset(); path.addRoundRect(RectF(0f, 0f, w, h), rr, rr, Path.Direction.CW)
        canvas.save(); canvas.clipPath(path)
        val pure = Color.HSVToColor(floatArrayOf(st.hsv[0], 1f, 1f))
        val sat = LinearGradient(0f, 0f, w, 0f, Color.WHITE, pure, Shader.TileMode.CLAMP)
        val value = LinearGradient(0f, 0f, 0f, h, Color.WHITE, Color.BLACK, Shader.TileMode.CLAMP)
        paint.shader = ComposeShader(sat, value, PorterDuff.Mode.MULTIPLY)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        canvas.restore()
        val cx = st.hsv[1] * w; val cy = (1f - st.hsv[2]) * h
        ring.strokeWidth = context.dp(3).toFloat(); ring.color = Color.WHITE
        canvas.drawCircle(cx, cy, context.dp(10).toFloat(), ring)
        ring.strokeWidth = context.dp(1).toFloat(); ring.color = 0x66000000
        canvas.drawCircle(cx, cy, context.dp(11).toFloat(), ring)
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        st.hsv[1] = (e.x / width).coerceIn(0f, 1f)
        st.hsv[2] = 1f - (e.y / height).coerceIn(0f, 1f)
        st.changed(this)
        return true
    }
}

/** kind 0 = 色相条，1 = 透明度条 */
private class BarView(c: Context, val st: ColorState, val kind: Int) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val rr = h / 2f
        path.reset(); path.addRoundRect(RectF(0f, 0f, w, h), rr, rr, Path.Direction.CW)
        canvas.save(); canvas.clipPath(path)
        if (kind == 0) {
            val cols = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)) }
            paint.shader = LinearGradient(0f, 0f, w, 0f, cols, null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
        } else {
            drawChecker(canvas, w, h, context.dp(6).toFloat())
            val rgb = Color.HSVToColor(st.hsv) and 0x00FFFFFF
            paint.shader = LinearGradient(0f, 0f, w, 0f, rgb, rgb or (0xFF shl 24), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
        }
        paint.shader = null
        canvas.restore()
        val frac = if (kind == 0) st.hsv[0] / 360f else st.alpha / 255f
        val cx = rr + frac * (w - 2 * rr)
        ring.strokeWidth = context.dp(3).toFloat(); ring.color = Color.WHITE
        canvas.drawCircle(cx, h / 2f, rr - context.dp(2), ring)
        ring.strokeWidth = context.dp(1).toFloat(); ring.color = 0x66000000
        canvas.drawCircle(cx, h / 2f, rr, ring)
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        val rr = height / 2f
        val f = ((e.x - rr) / (width - 2 * rr)).coerceIn(0f, 1f)
        if (kind == 0) st.hsv[0] = f * 360f else st.alpha = Math.round(f * 255f)
        st.changed(this)
        return true
    }
}

private class ColorState {
    val hsv = FloatArray(3)
    var alpha = 255
    var listener: ((View?) -> Unit)? = null
    fun set(color: Int) { Color.colorToHSV(color, hsv); alpha = Color.alpha(color) }
    fun color(): Int = Color.HSVToColor(alpha, hsv)
    fun changed(src: View?) { listener?.invoke(src) }
}

object ColorDialog {
    fun show(c: Context, title: String, initial: Int, onOk: (Int) -> Unit) {
        val st = ColorState().also { it.set(initial) }
        val dlg = Dialog(c)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(c.dp(20), c.dp(18), c.dp(20), c.dp(14))
        }
        root.addView(Ui.title(c, "调色板 · $title"))

        val top = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val preview = SwatchView(c)
        top.addView(preview, LinearLayout.LayoutParams(c.dp(56), c.dp(40)))
        val hex = EditText(c).apply {
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setTextColor(Pal.TEXT); textSize = 15f
            background = rounded(c, Pal.FIELD, 10)
            setPadding(c.dp(12), c.dp(9), c.dp(12), c.dp(9))
            typeface = Typeface.MONOSPACE
        }
        top.addView(hex, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = c.dp(12) })
        root.addView(top, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 14, c))

        val sv = SvView(c, st)
        val hue = BarView(c, st, 0)
        val alpha = BarView(c, st, 1)
        root.addView(sv, lp(ViewGroup.LayoutParams.MATCH_PARENT, c.dp(190), 14, c))
        root.addView(hue, lp(ViewGroup.LayoutParams.MATCH_PARENT, c.dp(26), 14, c))
        root.addView(alpha, lp(ViewGroup.LayoutParams.MATCH_PARENT, c.dp(26), 12, c))

        var internal = false
        fun hexOf(col: Int) = String.format("#%08X", col)
        fun refresh(src: View?) {
            preview.color = st.color()
            sv.invalidate(); hue.invalidate(); alpha.invalidate()
            if (src !== hex) {
                internal = true; hex.setText(hexOf(st.color())); internal = false
            }
        }
        st.listener = { refresh(it) }
        hex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (internal) return
                val t = s.toString().trim().removePrefix("#")
                if (t.length != 6 && t.length != 8) return
                try {
                    st.set(Color.parseColor("#$t")); refresh(hex)
                } catch (e: IllegalArgumentException) { }
            }
        })
        refresh(null)

        val btns = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        btns.addView(Ui.button(c, "取消", false) { dlg.dismiss() })
        btns.addView(Ui.button(c, "确定") { onOk(st.color()); dlg.dismiss() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = c.dp(10) })
        root.addView(btns, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 16, c))

        dlg.setContentView(root)
        dlg.window?.apply {
            setBackgroundDrawable(GradientDrawable().apply {
                setColor(Pal.CARD); cornerRadius = c.dp(24).toFloat(); setStroke(c.dp(1), Pal.LINE)
            })
            setLayout((c.resources.displayMetrics.widthPixels * 0.9f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        dlg.show()
    }
}
