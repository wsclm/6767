package com.cpsfloat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Clawd 像素螃蟹（Claude Code 吉祥物）桌宠，复刻 Clawd on Desk 里手机上能用的部分：
 *
 *  - 12 种动画状态：待机（眼球跟随触摸）、思考、打字、建造、耳机律动、杂耍、错误、开心、通知、
 *    扫地、搬运、睡觉（打哈欠 → 打盹 → 趴下 → 熟睡，有操作时醒来）；
 *  - 原作里由 AI 编程助手驱动的状态，这里改为由“点击速度(CPS)”驱动：
 *    点得越快 打字 → 建造 → 戴耳机 → 杂耍，停手后开心，刷新纪录弹出感叹号；
 *  - 互动彩蛋：点一下害羞、连点两下戳一戳、三下爱心、四下挣扎，可随时拖动，长按弹出菜单（相当于右键菜单）；
 *  - 迷你模式：拖到屏幕左右边缘后缩到边缘、点一下探头，进出时有抛物线跳跃；位置与迷你状态都会记住；
 *  - 勿扰模式：一直睡觉、不响应点击统计。
 *
 *  不适用于手机的部分（AI 助手钩子、权限气泡、会话面板、局域网伴侣 App、SSH、主题导入、全局快捷键、
 *  多显示器、托盘菜单）已去掉。
 */
object PetSt {
    const val NONE = -1
    const val IDLE = 0
    const val TYPE = 1
    const val BUILD = 2
    const val GROOVE = 3
    const val JUGGLE = 4
    const val SLEEP = 5
    const val THINK = 6
    const val ERROR = 7
    const val HAPPY = 8
    const val NOTIFY = 9
    const val SWEEP = 10
    const val CARRY = 11
    const val SHY = 12
    const val LOVE = 13
    const val POKE = 14
    const val STRUGGLE = 15
    const val WAKE = 16
    const val DRAG = 17

    /** 软件里“动画预览”按钮 */
    val DEMOS: List<Pair<String, Int>> = listOf(
        "待机" to IDLE, "思考" to THINK, "打字" to TYPE, "建造" to BUILD,
        "耳机律动" to GROOVE, "杂耍" to JUGGLE, "错误" to ERROR, "开心" to HAPPY,
        "通知" to NOTIFY, "扫地" to SWEEP, "搬运" to CARRY, "睡觉" to SLEEP,
        "害羞" to SHY, "爱心" to LOVE, "戳一戳" to POKE, "挣扎" to STRUGGLE
    )

    /** 一次性动作的持续时间（毫秒），0 表示不是一次性动作 */
    fun duration(s: Int): Long = when (s) {
        THINK -> 2600L
        ERROR -> 2400L
        HAPPY -> 2400L
        NOTIFY -> 2200L
        SWEEP -> 3600L
        SHY -> 1800L
        LOVE -> 2600L
        POKE -> 700L
        STRUGGLE -> 1900L
        WAKE -> 900L
        else -> 0L
    }
}

private const val E_NORMAL = 0
private const val E_BLINK = 1
private const val E_WIDE = 2
private const val E_HAPPY = 3
private const val E_X = 4
private const val E_HEART = 5
private const val E_CLOSED = 6
private const val E_HALF = 7
private const val E_SQUINT = 8
private const val E_SHY = 9

private const val PIF = 3.14159265f

/** 像素小图案（每个字符一个像素，'.' 为透明）。 */
private object PetArt {
    val HEART7 = arrayOf(".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X...")
    val HEART5 = arrayOf(".p.p.", "ppppp", "ppppp", ".ppp.", "..p..")
    val HARDHAT = arrayOf("....yyyyy....", "..yyyyyyyyy..", ".yyyyyyyyyyy.", "YYYYYYYYYYYYY")
    val HAM_UP = arrayOf(".gggg", ".gGGg", "..n..", "..n..", "..n..", "..n..", "..n..")
    val HAM_DOWN = arrayOf("..n..", "..n..", "..n..", "..n..", ".gggg", ".gGGg")
    val KEYBOARD = arrayOf("gggggggggggggggggg", "GGGGGGGGGGGGGGGGGG")
    val BROOM = arrayOf("..n...", "..n...", "..n...", "..n...", "..n...", "..n...", ".yyyy.", "yYyYyY", "yYyYyY")
    val BOX = arrayOf("nnnnnnnnnn", "NNNNNNNNNN", "nnnnYYnnnn", "nnnnYYnnnn", "nnnnYYnnnn", "NNNNNNNNNN")
    val NOTE = arrayOf("..zz", "..z.", "..z.", "zz..")
    val ZZ = arrayOf("zzzzz", "...z.", "..z..", ".z...", "zzzzz")
    val STAR = arrayOf("..y..", "..y..", "yyyyy", "..y..", "..y..")
    val BANG = arrayOf(
        ".wwwwwww.", "wwwwrwwww", "wwwwrwwww", "wwwwrwwww", "wwwwwwwww",
        "wwwwrwwww", ".wwwwwww.", "..wwww...", "...ww...."
    )
    val THINKB = arrayOf(
        "..wwwwwww..", ".wwwwwwwww.", "wwwwwwwwwww", "wwwwwwwwwww",
        ".wwwwwwwww.", "..wwwwwww..", ".ww........", "w.........."
    )
    val SWEAT = arrayOf(".b", "bb", "bb")
    val PLANK = arrayOf("NNNN", "nnnn")
    val MARK = arrayOf("r", "r", "r", ".", "r")
}

class PetView(c: Context) : View(c) {

    companion object {
        const val GW = 24f
        const val GH = 20f
        private const val OX = 3f
        private const val OY = 6f
        private const val BODY = 0xFFD97757.toInt()
        private const val BODY_DARK = 0xFFB85F40.toInt()
        private const val EYE = 0xFF2B2018.toInt()
        private const val PINK = 0xFFFF6B8B.toInt()
        private const val BLUSH = 0x99FF8FA3.toInt()
        private val LEG_X = floatArrayOf(3f, 6f, 10f, 13f)
    }

    // ---- 控制器 / 外部设置 ----
    var walking = false
    var draggable = false
    var airborne = false
    var lookX = 0f
    var lookY = 0f
    var onDrag: ((Float, Float) -> Unit)? = null
    var onDragState: ((Boolean) -> Unit)? = null
    var onReact: ((Int, Long) -> Unit)? = null
    var onInteract: (() -> Unit)? = null
    var onWake: (() -> Unit)? = null
    var onMenu: (() -> Unit)? = null

    // ---- 状态 ----
    var base = PetSt.IDLE
        private set
    private var baseStart = SystemClock.uptimeMillis()
    private var shot = PetSt.NONE
    private var shotStart = 0L
    private var shotEnd = 0L
    private var forced = PetSt.NONE
    private var forcedStart = 0L
    private var forcedUntil = 0L

    private var isDragging = false
    private var dragStart = 0L
    private var curLX = 0f
    private var curLY = 0f
    private var nextBlink = SystemClock.uptimeMillis() + 2000L
    private var blinkUntil = 0L
    private val rnd = Random()

    // ---- 绘制用 ----
    private val paint = Paint()
    private val path = Path()
    private var cv: Canvas? = null
    private var u = 1f
    private var px0 = 0f
    private var py0 = 0f

    // ---- 触摸 ----
    private val slop = ViewConfiguration.get(c).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var longFired = false
    private var cancelled = false
    private var tapCount = 0
    private var lastTapAt = 0L

    private val frame = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, 33)
        }
    }
    private val longRun = Runnable {
        if (!isDragging && !cancelled) {
            longFired = true
            val m = onMenu
            if (m != null) m() else play(PetSt.LOVE)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(frame)
        post(frame)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        removeCallbacks(longRun)
        super.onDetachedFromWindow()
    }

    // ---------------- 对外接口 ----------------

    /** 设置持续状态（待机/打字/建造/耳机/杂耍/睡觉/搬运）。从睡觉醒来时自动播放“醒来”动作。 */
    fun switchBase(s: Int) {
        if (s == base) return
        val now = SystemClock.uptimeMillis()
        val prev = base
        base = s
        baseStart = now
        if (prev == PetSt.SLEEP) {
            shot = PetSt.WAKE
            shotStart = now
            shotEnd = now + PetSt.duration(PetSt.WAKE)
        }
    }

    /** 播放一次性动作（思考、开心、害羞……），结束后回到持续状态。 */
    fun play(kind: Int, durMs: Long = PetSt.duration(kind)) {
        val now = SystemClock.uptimeMillis()
        shot = kind
        shotStart = now
        shotEnd = now + durMs
        onReact?.invoke(kind, durMs)
        invalidate()
    }

    /** 软件内预览：强制显示某个状态几秒钟。 */
    fun demo(kind: Int) {
        val now = SystemClock.uptimeMillis()
        forced = kind
        forcedStart = now
        val dur = if (kind == PetSt.SLEEP) 8000L else max(PetSt.duration(kind), 4200L)
        forcedUntil = now + dur
        invalidate()
    }

    // ---------------- 绘制工具 ----------------

    private fun fill(color: Int, a: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        if (a < 255) paint.alpha = (a.coerceIn(0, 255) * (color ushr 24) / 255)
    }

    /** 画布绝对坐标（单位：格子） */
    private fun ar(color: Int, l: Float, t: Float, r: Float, b: Float, a: Int = 255) {
        fill(color, a)
        cv?.drawRect(l * u, t * u, r * u, b * u, paint)
    }

    /** 相对身体坐标（会跟着身体的位移一起动） */
    private fun rr(color: Int, l: Float, t: Float, r: Float, b: Float, a: Int = 255) {
        ar(color, px0 + l, py0 + t, px0 + r, py0 + b, a)
    }

    private fun palette(ch: Char): Int = when (ch) {
        'o' -> BODY
        'd' -> BODY_DARK
        'k' -> EYE
        'w' -> 0xFFFFFFFF.toInt()
        'g' -> 0xFF9A9A94.toInt()
        'G' -> 0xFF4A4A46.toInt()
        'y' -> 0xFFFFC83D.toInt()
        'Y' -> 0xFFE0A100.toInt()
        'r' -> 0xFFE5484D.toInt()
        'p' -> PINK
        'X' -> PINK
        'b' -> 0xFF5B9BD5.toInt()
        'n' -> 0xFFB9824A.toInt()
        'N' -> 0xFF7A4F26.toInt()
        'l' -> 0xFF7BC47F.toInt()
        'z' -> 0xFF9CC4FF.toInt()
        else -> 0
    }

    /** 画像素图案：x、y 为画布绝对坐标（格子），cell 为每个像素占多少格。 */
    private fun spr(rows: Array<String>, x: Float, y: Float, cell: Float = 1f, tint: Int = 0, a: Int = 255) {
        val canvas = cv ?: return
        for (ri in rows.indices) {
            val row = rows[ri]
            for (ci in row.indices) {
                val ch = row[ci]
                if (ch == '.' || ch == ' ') continue
                val col = if (tint != 0) tint else palette(ch)
                if (col == 0) continue
                fill(col, a)
                canvas.drawRect(
                    (x + ci * cell) * u, (y + ri * cell) * u,
                    (x + (ci + 1) * cell) * u, (y + (ri + 1) * cell) * u, paint
                )
            }
        }
    }

    /** 折线（用于 ^、X、> < 形状的眼睛），坐标相对身体。 */
    private fun poly(color: Int, w: Float, vararg p: Float) {
        val canvas = cv ?: return
        if (p.size < 4) return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = w * u
        paint.strokeCap = Paint.Cap.SQUARE
        paint.strokeJoin = Paint.Join.MITER
        paint.color = color
        path.reset()
        path.moveTo((px0 + p[0]) * u, (py0 + p[1]) * u)
        var i = 2
        while (i + 1 < p.size) {
            path.lineTo((px0 + p[i]) * u, (py0 + p[i + 1]) * u)
            i += 2
        }
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun sn(x: Double): Float = sin(x).toFloat()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        cv = canvas
        val now = SystemClock.uptimeMillis()
        if (now >= nextBlink) {
            blinkUntil = now + 140L
            nextBlink = now + 2200L + rnd.nextInt(3500)
        }
        curLX += (lookX - curLX) * 0.22f
        curLY += (lookY - curLY) * 0.22f
        u = width / GW
        if (u <= 0f) return

        // 当前要显示的状态：持续状态 < 一次性动作 < 预览 < 拖动
        var st = base
        var t = now - baseStart
        if (shot != PetSt.NONE) {
            if (now >= shotEnd) {
                shot = PetSt.NONE
            } else {
                st = shot
                t = now - shotStart
            }
        }
        if (forced != PetSt.NONE) {
            if (now >= forcedUntil) {
                forced = PetSt.NONE
            } else {
                st = forced
                t = now - forcedStart
            }
        }
        if (isDragging) {
            st = PetSt.DRAG
            t = now - dragStart
        }

        var bx = 0f
        var by = 0f
        var flat = 0f
        var leg = 0
        var lcy = 0f
        var rcy = 0f
        var lcx = 0f
        var rcx = 0f
        var eyes = E_NORMAL
        var lx = 0f
        var ly = 0f
        var mouth = 0f
        var phase = 0
        val blink = now < blinkUntil
        val tf = t.toFloat()

        if (st == PetSt.SLEEP) {
            phase = if (t < 1500L) 0 else if (t < 3700L) 1 else if (t < 4400L) 2 else 3
        }

        when (st) {
            PetSt.IDLE -> {
                by = sn(now / 520.0) * 0.12f
                if (walking) {
                    by += sn(now / 90.0) * 0.2f
                    leg = ((now / 140L) % 2L).toInt() + 1
                }
                lx = curLX
                ly = curLY
                if (blink) eyes = E_BLINK
            }
            PetSt.CARRY -> {
                if (walking) {
                    by = sn(now / 90.0) * 0.2f
                    leg = ((now / 140L) % 2L).toInt() + 1
                }
                lcy = -5f
                rcy = -5f
                lcx = 2f
                rcx = -2f
                if (blink) eyes = E_BLINK
            }
            PetSt.TYPE -> {
                val k = ((now / 110L) % 2L) == 0L
                lcy = if (k) 8.2f else 7.0f      // 打字时爪子位置是绝对值
                rcy = if (k) 7.0f else 8.2f
                ly = 0.6f
                if (blink) eyes = E_BLINK
            }
            PetSt.BUILD -> {
                by = -abs(sn(now / 160.0)) * 0.3f
                ly = 0.2f
            }
            PetSt.GROOVE -> {
                bx = sn(now / 200.0) * 0.5f
                by = -abs(sn(now / 200.0)) * 0.5f
                leg = ((now / 200L) % 2L).toInt() + 1
                if (((now / 600L) % 4L) == 3L) eyes = E_CLOSED
            }
            PetSt.JUGGLE -> {
                val k = ((now / 300L) % 2L) == 0L
                lcy = if (k) -4f else -2.5f
                rcy = if (k) -2.5f else -4f
                lx = sn(now / 300.0) * 0.8f
                ly = -0.4f
            }
            PetSt.THINK -> {
                ly = -0.6f
                lx = 0.7f
                rcy = -2.5f
                rcx = -1f
                by = sn(now / 600.0) * 0.1f
            }
            PetSt.ERROR -> {
                bx = sn(now / 35.0) * 0.25f
                eyes = E_X
                lcy = -1f
                rcy = -1f
            }
            PetSt.HAPPY -> {
                by = -abs(sn(now / 150.0)) * 1.8f
                eyes = E_HAPPY
                lcy = -3f
                rcy = -3f
                leg = ((now / 120L) % 2L).toInt() + 1
            }
            PetSt.NOTIFY -> {
                by = -abs(sn(now / 130.0)) * 0.8f
                eyes = E_WIDE
                rcy = if (((now / 180L) % 2L) == 0L) -3f else -4f
            }
            PetSt.SWEEP -> {
                ly = 0.7f
                leg = ((now / 220L) % 2L).toInt() + 1
            }
            PetSt.SHY -> {
                bx = sn(now / 45.0) * 0.18f
                eyes = E_SHY
            }
            PetSt.LOVE -> {
                by = -abs(sn(t / 170.0)) * 1.6f
                eyes = E_HEART
            }
            PetSt.POKE -> {
                val k = (tf / 700f).coerceIn(0f, 1f)
                by = -sin(k * PIF) * 2.4f
                eyes = E_WIDE
                lcy = -2f
                rcy = -2f
                lcx = -0.8f
                rcx = 0.8f
                leg = 1
            }
            PetSt.STRUGGLE -> {
                bx = sn(now / 28.0) * 0.6f
                by = sn(now / 41.0) * 0.4f
                eyes = E_SQUINT
                val k = ((now / 70L) % 2L) == 0L
                lcy = if (k) -2.5f else 1f
                rcy = if (k) 1f else -2.5f
                leg = ((now / 60L) % 2L).toInt() + 1
            }
            PetSt.WAKE -> {
                val k = (tf / 900f).coerceIn(0f, 1f)
                by = -sin(k * PIF) * 1.5f
                eyes = if (k < 0.5f) E_BLINK else E_NORMAL
                lcy = -3f * (1f - k)
                rcy = -3f * (1f - k)
            }
            PetSt.DRAG -> {
                by = -0.5f
                eyes = E_WIDE
                leg = ((now / 90L) % 2L).toInt() + 1
                lcy = -1.5f
                rcy = -1.5f
            }
            PetSt.SLEEP -> {
                when (phase) {
                    0 -> {   // 打哈欠
                        val k = (tf / 1500f).coerceIn(0f, 1f)
                        val s = sin(k * PIF)
                        lcy = -3f * s
                        rcy = -3f * s
                        mouth = s
                        eyes = if (k > 0.3f) E_CLOSED else E_HALF
                        by = -0.3f * s
                    }
                    1 -> {   // 打盹
                        val k = (tf - 1500f) / 2200f
                        by = 0.3f * abs(sin(k * 6f))
                        eyes = if ((k * 4f).toInt() % 2 == 0) E_HALF else E_CLOSED
                    }
                    2 -> {   // 趴下
                        flat = ((tf - 3700f) / 700f).coerceIn(0f, 1f)
                        eyes = E_CLOSED
                    }
                    else -> {   // 熟睡
                        flat = 1f
                        by = sn(now / 700.0) * 0.12f
                        eyes = E_CLOSED
                    }
                }
            }
        }

        // 空中（迷你模式进出的跳跃）
        if (airborne && st != PetSt.DRAG) {
            leg = 1
            lcy = -3f
            rcy = -3f
        }

        px0 = OX + bx
        py0 = OY + by

        // 地面阴影（身体越高越小）
        val shw = 12f * max(0.35f, 1f + min(by, 0f) * 0.12f)
        ar(0x33000000, 12f - shw / 2f, 16.8f, 12f + shw / 2f, 18.0f)

        // 打字用的键盘放在身体后面
        if (st == PetSt.TYPE) spr(PetArt.KEYBOARD, 3f, 16.2f)

        // 四条腿
        val legH = 3f * (1f - flat)
        for (i in LEG_X.indices) {
            var bottom = 11f
            if (leg == 1 && i % 2 == 0) bottom = 9.6f
            if (leg == 2 && i % 2 == 1) bottom = 9.6f
            bottom = 8f + (bottom - 8f) * (1f - flat)
            if (legH > 0.01f && bottom > 8f) rr(BODY, LEG_X[i], 8f, LEG_X[i] + 2f, bottom)
        }

        // 身体
        val ftop = 1f + 3.6f * flat
        rr(BODY, 2f, ftop, 16f, 8f)

        // 双钳
        val cy0 = 4f + 3f * flat
        val lcTop = if (st == PetSt.TYPE) lcy else cy0 + lcy
        val rcTop = if (st == PetSt.TYPE) rcy else cy0 + rcy
        rr(BODY, 0f + lcx, lcTop, 2f + lcx, lcTop + 2f)
        rr(BODY, 16f + rcx, rcTop, 18f + rcx, rcTop + 2f)
        rr(BODY_DARK, 2f, 7f, 16f, 8f)

        // 眼睛
        val ey = 3f + 3f * flat
        when (eyes) {
            E_NORMAL -> eyeRects(0f, ey, 1.4f, ey + 2.2f, lx, ly)
            E_BLINK -> eyeRects(0f, ey + 1.2f, 1.4f, ey + 1.8f, lx, ly)
            E_WIDE -> eyeRects(-0.1f, ey - 0.4f, 1.5f, ey + 2.6f, lx, ly)
            E_HALF -> eyeRects(0f, ey + 0.9f, 1.4f, ey + 2.1f, lx, ly)
            E_CLOSED -> eyeRects(-0.2f, ey + 1.7f, 1.6f, ey + 2.2f, lx, ly)
            E_HEART -> {
                spr(PetArt.HEART7, px0 + 6f - 1.05f, py0 + 4.1f - 0.9f, 0.3f, PINK)
                spr(PetArt.HEART7, px0 + 12f - 1.05f, py0 + 4.1f - 0.9f, 0.3f, PINK)
            }
            E_HAPPY, E_SHY -> {
                poly(EYE, 0.7f, 5.0f, 4.9f, 6.0f, 3.7f, 7.0f, 4.9f)
                poly(EYE, 0.7f, 11.0f, 4.9f, 12.0f, 3.7f, 13.0f, 4.9f)
                if (eyes == E_SHY) {
                    rr(BLUSH, 3.2f, 5.4f, 5.2f, 6.4f)
                    rr(BLUSH, 12.8f, 5.4f, 14.8f, 6.4f)
                }
            }
            E_X -> {
                poly(EYE, 0.5f, 5.1f, ey + 0.2f, 6.9f, ey + 2.0f)
                poly(EYE, 0.5f, 6.9f, ey + 0.2f, 5.1f, ey + 2.0f)
                poly(EYE, 0.5f, 11.1f, ey + 0.2f, 12.9f, ey + 2.0f)
                poly(EYE, 0.5f, 12.9f, ey + 0.2f, 11.1f, ey + 2.0f)
            }
            E_SQUINT -> {
                poly(EYE, 0.55f, 5.2f, ey, 6.8f, ey + 1.1f, 5.2f, ey + 2.2f)
                poly(EYE, 0.55f, 12.8f, ey, 11.2f, ey + 1.1f, 12.8f, ey + 2.2f)
            }
        }
        if (mouth > 0.05f) rr(EYE, 7.8f, 5.3f, 10.2f, 5.3f + 0.6f + 1.6f * mouth)

        // 各状态的道具与特效
        accessories(st, now, phase)
    }

    private fun eyeRects(l: Float, t: Float, r: Float, b: Float, lx: Float, ly: Float) {
        val dx = lx * 0.9f
        val dy = ly * 0.6f
        rr(EYE, 5.3f + l + dx, t + dy, 5.3f + r + dx, b + dy)
        rr(EYE, 11.3f + l + dx, t + dy, 11.3f + r + dx, b + dy)
    }

    private fun accessories(st: Int, now: Long, phase: Int) {
        when (st) {
            PetSt.BUILD -> {
                spr(PetArt.HARDHAT, px0 + 2.5f, py0 - 2.6f)
                spr(PetArt.PLANK, 19.5f, 16.4f)
                if (((now / 220L) % 2L) == 0L) {
                    spr(PetArt.HAM_UP, px0 + 17.5f, py0 + 4.2f - 7f + 1.2f)
                } else {
                    spr(PetArt.HAM_DOWN, px0 + 17.5f, py0 + 4.9f)
                    for (i in 0 until 3) {
                        ar(0xFFFFC83D.toInt(), 20.5f + i * 0.7f, 15.2f - i * 0.9f, 21.3f + i * 0.7f, 15.9f - i * 0.9f)
                    }
                }
            }
            PetSt.GROOVE -> {
                val dark = 0xFF4A4A46.toInt()
                val red = 0xFFE5484D.toInt()
                rr(dark, 3f, 0.1f, 15f, 1.1f)
                rr(dark, 2f, 0.6f, 3f, 4.2f)
                rr(dark, 15f, 0.6f, 16f, 4.2f)
                rr(dark, 0.8f, 3f, 2.4f, 6.4f)
                rr(red, 1.1f, 3.4f, 2f, 6f)
                rr(dark, 15.6f, 3f, 17.2f, 6.4f)
                rr(red, 16f, 3.4f, 16.9f, 6f)
                for (k in 0 until 3) {
                    val p = ((now / 1400f) + k / 3f) % 1f
                    val nx = 18.5f + sin(p * 6.28f + k) * 0.8f
                    val ny = 8f - p * 7f
                    spr(PetArt.NOTE, nx, ny, 0.5f, 0, ((1f - p) * 255f).toInt())
                }
            }
            PetSt.JUGGLE -> {
                val cols = intArrayOf(0xFFE5484D.toInt(), 0xFFFFC83D.toInt(), 0xFF5B9BD5.toInt())
                for (k in 0 until 3) {
                    val p = ((now / 1300f) + k / 3f) % 1f
                    val x: Float
                    val q: Float
                    if (p < 0.5f) {
                        q = p * 2f
                        x = 1f + 16f * q
                    } else {
                        q = (p - 0.5f) * 2f
                        x = 17f - 16f * q
                    }
                    val y = 0.2f - 4.6f * sin(PIF * q)
                    ar(cols[k], OX + x, OY + y, OX + x + 1.8f, OY + y + 1.8f)
                }
            }
            PetSt.THINK -> {
                spr(PetArt.THINKB, OX + 11.5f, OY - 6.5f)
                val on = ((now / 380L) % 4L).toInt()
                for (k in 0 until 3) {
                    val col = if (k < on) 0xFF4A4A46.toInt() else 0xFFBBBBB6.toInt()
                    ar(col, OX + 13.8f + k * 2.3f, OY - 4.3f, OX + 14.8f + k * 2.3f, OY - 3.3f)
                }
            }
            PetSt.ERROR -> {
                for (k in 0 until 3) {
                    val p = ((now / 1000f) + k / 3f) % 1f
                    val a = ((1f - p) * 200f).toInt()
                    val sz = 1f + p * 1.6f
                    val cx = 5f + k * 3.5f + sin(p * 5f + k) * 0.8f
                    ar(0xFF909090.toInt(), OX + cx, OY - 0.5f - p * 4f, OX + cx + sz, OY - 0.5f - p * 4f + sz, a)
                }
                spr(PetArt.SWEAT, px0 + 15.6f, py0 + 0.8f)
            }
            PetSt.HAPPY -> {
                val cols = intArrayOf(0xFFFFC83D.toInt(), 0xFFFF6B8B.toInt(), 0xFF5B9BD5.toInt(), 0xFF7BC47F.toInt(), 0xFFE5484D.toInt())
                for (k in 0 until 9) {
                    val p = ((now / 1100f) + k / 9f) % 1f
                    val ang = k * 2.4f
                    val dist = 3f + p * 7f
                    val x = 12f + cos(ang) * dist
                    val y = 9f + sin(ang) * dist * 0.8f - 2f
                    ar(cols[k % 5], x, y, x + 0.8f, y + 0.8f, ((1f - p) * 255f).toInt())
                }
                spr(PetArt.STAR, OX + 18.2f, OY - 3f + sn(now / 200.0) * 0.4f, 0.5f)
                spr(PetArt.STAR, OX - 2.2f, OY - 2f + cos(now / 200.0).toFloat() * 0.4f, 0.5f)
            }
            PetSt.NOTIFY -> {
                spr(PetArt.BANG, OX + 12f, OY - 8.5f + sn(now / 130.0) * 0.4f)
            }
            PetSt.SWEEP -> {
                val sx = sn(now / 260.0) * 1.8f
                spr(PetArt.BROOM, OX + 13.5f + sx, OY + 3.2f)
                for (k in 0 until 3) {
                    val p = ((now / 700f) + k / 3f) % 1f
                    val dx = OX + 15f + sx - p * 4.5f
                    val dy = OY + 10.2f - p * 1.2f - k * 0.3f
                    ar(0xFFCCCCC6.toInt(), dx, dy, dx + 0.8f, dy + 0.8f, ((1f - p) * 255f).toInt())
                }
            }
            PetSt.CARRY -> {
                spr(PetArt.BOX, px0 + 4f, py0 - 5.4f)
            }
            PetSt.LOVE -> {
                for (k in 0 until 3) {
                    val p = ((now / 1400f) + k / 3f) % 1f
                    val hx = OX + 5f + k * 4f + sin(p * 6.28f + k) * 0.5f
                    val hy = OY + 2.6f - p * 5.2f
                    spr(PetArt.HEART5, hx, hy, 0.4f, 0, ((1f - p) * 255f).toInt())
                }
            }
            PetSt.SLEEP -> {
                if (phase >= 1) {
                    for (k in 0 until 3) {
                        val p = ((now / 2200f) + k / 3f) % 1f
                        val zx = OX + 14f + k * 1.8f + p * 2f
                        val zy = OY + 2f - p * 6f
                        spr(PetArt.ZZ, zx, zy, 0.28f + 0.08f * k, 0, ((1f - p) * 255f).toInt())
                    }
                }
            }
            PetSt.STRUGGLE -> {
                for (k in 0 until 2) {
                    val p = ((now / 500f) + k / 2f) % 1f
                    spr(PetArt.SWEAT, px0 + (if (k == 0) 0.4f else 15.8f), py0 + 0.5f + p * 3f, 0.8f, 0, ((1f - p) * 255f).toInt())
                }
            }
            PetSt.POKE -> {
                spr(PetArt.MARK, OX + 8.6f, OY - 4.5f, 0.8f)
            }
        }
    }

    // ---------------- 触摸 ----------------

    private fun handleTap() {
        val now = SystemClock.uptimeMillis()
        // 睡着时点一下只负责叫醒
        if (forced == PetSt.SLEEP) {
            forced = PetSt.NONE
            play(PetSt.WAKE)
            return
        }
        if (base == PetSt.SLEEP && forced == PetSt.NONE) {
            onWake?.invoke()
            return
        }
        tapCount = if (now - lastTapAt < 420L) tapCount + 1 else 1
        lastTapAt = now
        onInteract?.invoke()
        when {
            tapCount >= 4 -> {
                play(PetSt.STRUGGLE)
                tapCount = 0
            }
            tapCount == 3 -> play(PetSt.LOVE)
            tapCount == 2 -> play(PetSt.POKE)
            else -> play(PetSt.SHY)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = e.rawX
                downY = e.rawY
                lastX = e.rawX
                lastY = e.rawY
                isDragging = false
                longFired = false
                cancelled = false
                removeCallbacks(longRun)
                postDelayed(longRun, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                val far = abs(e.rawX - downX) > slop || abs(e.rawY - downY) > slop
                if (!isDragging && !longFired && !cancelled && far) {
                    removeCallbacks(longRun)
                    if (draggable) {
                        isDragging = true
                        dragStart = SystemClock.uptimeMillis()
                        shot = PetSt.NONE
                        onDragState?.invoke(true)
                    } else {
                        cancelled = true
                    }
                }
                if (isDragging) onDrag?.invoke(e.rawX - lastX, e.rawY - lastY)
                lastX = e.rawX
                lastY = e.rawY
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longRun)
                if (isDragging) {
                    isDragging = false
                    onDragState?.invoke(false)
                } else if (!longFired && !cancelled) {
                    handleTap()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longRun)
                if (isDragging) {
                    isDragging = false
                    onDragState?.invoke(false)
                }
                cancelled = true
            }
        }
        return true
    }
}

/** 长按桌宠弹出的小菜单（相当于原作的右键菜单）。 */
private class MenuBox(c: Context, val onOutside: () -> Unit) : LinearLayout(c) {
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onOutside()
            return true
        }
        return super.onTouchEvent(e)
    }
}

private class PetMenu(
    private val ctx: Context,
    private val wm: WindowManager,
    private val items: List<Pair<String, () -> Unit>>,
    private val onClosed: () -> Unit
) {
    private var root: LinearLayout? = null
    private val h = Handler(Looper.getMainLooper())
    private val autoClose = Runnable { dismiss() }

    fun show(ax: Int, ay: Int, aw: Int, ah: Int, sw: Int, sh: Int) {
        val box = MenuBox(ctx) { dismiss() }
        box.orientation = LinearLayout.VERTICAL
        box.background = GradientDrawable().apply {
            setColor(Pal.CARD)
            cornerRadius = ctx.dp(14).toFloat()
            setStroke(ctx.dp(1), Pal.LINE)
        }
        box.setPadding(ctx.dp(6), ctx.dp(6), ctx.dp(6), ctx.dp(6))
        for (item in items) {
            val tv = TextView(ctx)
            tv.text = item.first
            tv.textSize = 14.5f
            tv.setTextColor(Pal.TEXT)
            tv.setPadding(ctx.dp(16), ctx.dp(11), ctx.dp(16), ctx.dp(11))
            tv.setOnClickListener {
                dismiss()
                item.second()
            }
            box.addView(tv)
        }
        box.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val mw = box.measuredWidth
        val mh = box.measuredHeight
        var x = ax + aw / 2 - mw / 2
        var y = ay + ah + ctx.dp(6)
        if (y + mh > sh) y = ay - mh - ctx.dp(6)
        x = x.coerceIn(0, (sw - mw).coerceAtLeast(0))
        y = y.coerceIn(0, (sh - mh).coerceAtLeast(0))
        val l = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        l.gravity = Gravity.TOP or Gravity.START
        l.x = x
        l.y = y
        try {
            wm.addView(box, l)
            root = box
            h.postDelayed(autoClose, 6000L)
        } catch (_: Throwable) {
            onClosed()
        }
    }

    fun dismiss() {
        h.removeCallbacks(autoClose)
        val r = root
        root = null
        if (r != null) {
            try { wm.removeView(r) } catch (_: Throwable) { }
            onClosed()
        }
    }
}

/** 桌宠悬浮窗控制器：随机走动、睡觉、迷你模式、对点击速度做出反应、被拖动。 */
class PetController(private val ctx: Context, private val wm: WindowManager) {

    private class Jump(
        val sx: Float, val sy: Float, val tx: Float, val ty: Float,
        val t0: Long, val dur: Long, val arc: Float
    )

    private val h = Handler(Looper.getMainLooper())
    private val rnd = Random()
    private val d = ctx.resources.displayMetrics.density

    private var cfg = PetCfg()
    private var view: PetView? = null
    private var lp: WindowManager.LayoutParams? = null

    private var x = 0f
    private var y = 0f
    private var vx = 0f
    private var vy = 0f
    private var moving = false
    private var carrying = false
    private var phaseEnd = 0L
    private var dragging = false
    private var lastTick = 0L
    private var maxX = 0f
    private var maxY = 0f
    private var scrW = 0
    private var scrH = 0

    private var lastActivity = 0L
    private var forceSleep = false
    private var cpsNow = 0
    private var level = 0
    private var levelHold = 0L
    private var peak = 0
    private var best = 0
    private var lastNotify = 0L
    private var lastError = 0L
    private var mini = 0
    private var peekUntil = 0L
    private var lookUntil = 0L
    private var jump: Jump? = null
    private var menu: PetMenu? = null

    private val tick = object : Runnable {
        override fun run() {
            if (view == null) return
            step()
            h.postDelayed(this, 33)
        }
    }

    private fun viewW(): Int = ctx.dp(cfg.sizeDp)
    private fun viewH(): Int = (viewW() * PetView.GH / PetView.GW).toInt()

    @Suppress("DEPRECATION")
    private fun updateBounds() {
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        scrW = dm.widthPixels
        scrH = dm.heightPixels
        maxX = (scrW - viewW()).coerceAtLeast(0).toFloat()
        maxY = (scrH - viewH()).coerceAtLeast(0).toFloat()
    }

    /** 迷你模式下桌宠窗口的 x：只露出一部分身体（探头时露出更多）。 */
    private fun miniTargetX(): Float {
        val w = viewW().toFloat()
        val f = if (SystemClock.uptimeMillis() < peekUntil) 0.85f else 0.42f
        return if (mini == 1) -(1f - f) * w else scrW - f * w
    }

    fun apply(c: PetCfg) {
        cfg = c
        if (!c.enabled) {
            remove()
            return
        }
        if (view == null) {
            create()
        } else {
            resize()
            if (c.miniSide != mini && jump == null && !dragging) {
                if (c.miniSide != 0) enterMini(c.miniSide) else exitMini()
            }
        }
    }

    private fun create() {
        val v = PetView(ctx)
        v.draggable = true
        val l = WindowManager.LayoutParams(
            viewW(), viewH(),
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
        updateBounds()
        mini = cfg.miniSide
        val saved = Cfg.petPos(ctx)
        if (saved != null) {
            x = saved[0] / 1000f * maxX
            y = saved[1] / 1000f * maxY
        } else {
            x = maxX * 0.8f
            y = maxY * 0.6f
        }
        y = y.coerceIn(0f, maxY)
        x = if (mini != 0) miniTargetX() else x.coerceIn(0f, maxX)
        l.x = x.toInt()
        l.y = y.toInt()

        v.onDrag = { dx, dy ->
            val w = viewW().toFloat()
            x = (x + dx).coerceIn(-w * 0.5f, scrW - w * 0.5f)
            y = (y + dy).coerceIn(0f, maxY)
            pushPos()
        }
        v.onDragState = { on ->
            lastActivity = SystemClock.uptimeMillis()
            if (on) {
                dragging = true
                moving = false
                carrying = false
                jump = null
                v.airborne = false
                v.walking = false
                menu?.dismiss()
            } else {
                dragging = false
                onDropped()
            }
        }
        v.onReact = { kind, ms -> onReaction(kind, ms) }
        v.onInteract = { onInteract() }
        v.onWake = { wake() }
        v.onMenu = { showMenu() }

        try {
            wm.addView(v, l)
        } catch (t: Throwable) {
            return
        }
        view = v
        lp = l
        lastTick = SystemClock.uptimeMillis()
        lastActivity = lastTick
        forceSleep = false
        level = 0
        peak = 0
        beginPause()
        h.removeCallbacks(tick)
        h.post(tick)
    }

    private fun resize() {
        val l = lp ?: return
        val v = view ?: return
        if (l.width == viewW()) return
        val fx = if (maxX > 0f) x / maxX else 0f
        val fy = if (maxY > 0f) y / maxY else 0f
        l.width = viewW()
        l.height = viewH()
        updateBounds()
        x = if (mini != 0) miniTargetX() else (fx * maxX).coerceIn(0f, maxX)
        y = (fy * maxY).coerceIn(0f, maxY)
        l.x = x.toInt()
        l.y = y.toInt()
        try { wm.updateViewLayout(v, l) } catch (_: Throwable) { }
    }

    /** 屏幕方向变化：按比例换算位置，确保桌宠仍在屏幕内。 */
    fun onScreenChanged() {
        if (view == null) return
        val fx = if (maxX > 0f) x / maxX else 0f
        val fy = if (maxY > 0f) y / maxY else 0f
        updateBounds()
        jump = null
        view?.airborne = false
        x = if (mini != 0) miniTargetX() else (fx * maxX).coerceIn(0f, maxX)
        y = (fy * maxY).coerceIn(0f, maxY)
        pushPos()
    }

    fun contains(px: Int, py: Int): Boolean {
        val l = lp ?: return false
        if (view == null) return false
        return px >= l.x && px <= l.x + l.width && py >= l.y && py <= l.y + l.height
    }

    fun remove() {
        menu?.dismiss()
        val v = view
        if (v != null) {
            savePos()
            try { wm.removeView(v) } catch (_: Throwable) { }
        }
        h.removeCallbacks(tick)
        view = null
        lp = null
        dragging = false
        moving = false
        carrying = false
        jump = null
    }

    private fun savePos() {
        val fx = if (maxX > 0f) (x / maxX * 1000f).toInt() else 0
        val fy = if (maxY > 0f) (y / maxY * 1000f).toInt() else 0
        Cfg.putPetPos(ctx, fx.coerceIn(0, 1000), fy.coerceIn(0, 1000))
    }

    private fun pushPos() {
        val l = lp ?: return
        val v = view ?: return
        l.x = x.toInt()
        l.y = y.toInt()
        try { wm.updateViewLayout(v, l) } catch (_: Throwable) { }
    }

    // ---------------- 外部输入 ----------------

    /** 每 100ms 由服务传入当前最高的 CPS，据此切换打字/建造/耳机/杂耍。 */
    fun onCps(c: Int) {
        if (view == null) return
        val now = SystemClock.uptimeMillis()
        cpsNow = if (cfg.reactCps && !cfg.dnd) c else 0
        val raw = when {
            cpsNow >= 12 -> 4
            cpsNow >= 8 -> 3
            cpsNow >= 4 -> 2
            cpsNow >= 1 -> 1
            else -> 0
        }
        if (cpsNow > 0) lastActivity = now
        val prev = level
        if (raw > level) {
            level = raw
            levelHold = now
        } else if (raw < level && now - levelHold > 700L) {
            level = raw
            levelHold = now
        }
        if (prev == 0 && level > 0) peak = cpsNow
        if (cpsNow > peak) peak = cpsNow
        // 刷新纪录：弹出感叹号
        if (cpsNow >= 8 && cpsNow > best) {
            best = cpsNow
            if (now - lastNotify > 4000L) {
                lastNotify = now
                view?.play(PetSt.NOTIFY)
            }
        }
        // 一阵连点结束：点得够快就庆祝一下
        if (prev > 0 && level == 0) {
            if (peak >= 8) view?.play(PetSt.HAPPY)
            peak = 0
        }
    }

    /** 屏幕上任意位置有触摸：眼睛看过去，同时保持清醒。 */
    fun onTouchPoint(px: Int, py: Int) {
        val v = view ?: return
        if (cfg.dnd) return
        val now = SystemClock.uptimeMillis()
        lastActivity = now
        if (!cfg.eyeTrack) return
        val l = lp ?: return
        val cx = l.x + l.width / 2f
        val cy = l.y + l.height / 2f
        val dx = px - cx
        val dy = py - cy
        val dist = hypot(dx, dy)
        if (dist < 1f) {
            v.lookX = 0f
            v.lookY = 0f
        } else {
            val k = dist / (dist + 140f * d)
            v.lookX = dx / dist * k
            v.lookY = dy / dist * k
        }
        lookUntil = now + 5000L
    }

    /** Shizuku 没就绪等错误：螃蟹露出错误表情。 */
    fun flashError() {
        val now = SystemClock.uptimeMillis()
        if (now - lastError < 8000L) return
        lastError = now
        view?.play(PetSt.ERROR)
    }

    // ---------------- 交互 ----------------

    private fun onReaction(kind: Int, ms: Long) {
        val now = SystemClock.uptimeMillis()
        pauseFor(ms)
        if (mini != 0 && (kind == PetSt.HAPPY || kind == PetSt.NOTIFY || kind == PetSt.ERROR)) {
            peekUntil = now + ms
        }
    }

    private fun onInteract() {
        val now = SystemClock.uptimeMillis()
        lastActivity = now
        if (mini != 0) peekUntil = if (now < peekUntil) 0L else now + 3500L
    }

    private fun wake() {
        lastActivity = SystemClock.uptimeMillis()
        forceSleep = false
        view?.switchBase(PetSt.IDLE)
    }

    private fun showMenu() {
        val v = view ?: return
        val l = lp ?: return
        menu?.dismiss()
        lastActivity = SystemClock.uptimeMillis()
        val items = ArrayList<Pair<String, () -> Unit>>()
        fun add(label: String, act: () -> Unit) {
            items.add(Pair(label, act))
        }
        val isMini = mini != 0
        add(if (isMini) "退出迷你模式" else "迷你模式") {
            val side = if (x + viewW() / 2f < scrW / 2f) 1 else 2
            Cfg.putPetInt(ctx, "mini", if (isMini) 0 else side)
        }
        val dndOn = cfg.dnd
        add(if (dndOn) "关闭勿扰模式" else "勿扰模式（睡觉）") {
            Cfg.putPetBool(ctx, "dnd", !dndOn)
        }
        val asleep = v.base == PetSt.SLEEP
        add(if (asleep) "叫醒" else "睡觉") {
            if (asleep) {
                wake()
            } else {
                forceSleep = true
            }
        }
        add("关闭桌宠") {
            Cfg.putPetBool(ctx, "enabled", false)
        }
        val m = PetMenu(ctx, wm, items) { menu = null }
        menu = m
        m.show(l.x, l.y, l.width, l.height, scrW, scrH)
    }

    // ---------------- 迷你模式 / 跳跃 ----------------

    private fun onDropped() {
        val w = viewW().toFloat()
        val thr = w * 0.3f
        val distL = x
        val distR = scrW - (x + w)
        val near = min(distL, distR) < thr
        if ((cfg.miniEdge || mini != 0) && near) {
            enterMini(if (distL <= distR) 1 else 2)
        } else {
            if (mini != 0) {
                mini = 0
                Cfg.putPetInt(ctx, "mini", 0)
            }
            x = x.coerceIn(0f, maxX)
            y = y.coerceIn(0f, maxY)
            pushPos()
            savePos()
            beginPause()
        }
    }

    private fun enterMini(side: Int) {
        mini = side
        peekUntil = 0L
        Cfg.putPetInt(ctx, "mini", side)
        startJump(miniTargetX(), y.coerceIn(0f, maxY))
        savePos()
    }

    private fun exitMini() {
        val w = viewW().toFloat()
        val side = mini
        mini = 0
        peekUntil = 0L
        Cfg.putPetInt(ctx, "mini", 0)
        val tx = if (side == 1) w * 0.35f else maxX - w * 0.35f
        startJump(tx.coerceIn(0f, maxX), y.coerceIn(0f, maxY))
        savePos()
    }

    private fun startJump(tx: Float, ty: Float) {
        val dist = hypot(tx - x, ty - y)
        jump = Jump(x, y, tx, ty, SystemClock.uptimeMillis(), 520L, max(ctx.dp(30).toFloat(), dist * 0.2f))
        moving = false
        carrying = false
        view?.walking = false
        view?.airborne = true
    }

    // ---------------- 行为 ----------------

    private fun randMs(a: Int, b: Int): Long {
        val lo = min(a, b).coerceAtLeast(1)
        val hi = max(a, b).coerceAtLeast(lo)
        return ((lo + rnd.nextFloat() * (hi - lo)) * 1000f).toLong()
    }

    private fun desiredBase(now: Long): Int {
        if (forceSleep) return PetSt.SLEEP
        val idleMs = now - lastActivity
        if (cfg.dnd) return if (idleMs > 8000L) PetSt.SLEEP else PetSt.IDLE
        if (level == 0 && idleMs > cfg.sleepAfter * 1000L) return PetSt.SLEEP
        return when {
            level >= 4 -> PetSt.JUGGLE
            level == 3 -> PetSt.GROOVE
            level == 2 -> PetSt.BUILD
            level == 1 -> PetSt.TYPE
            carrying && moving -> PetSt.CARRY
            else -> PetSt.IDLE
        }
    }

    private fun beginPause() {
        moving = false
        carrying = false
        vx = 0f
        vy = 0f
        val now = SystemClock.uptimeMillis()
        phaseEnd = now + randMs(cfg.pauseMin, cfg.pauseMax)
        view?.walking = false
        // 偶尔自己玩：思考 / 扫地
        if (view != null && desiredBase(now) == PetSt.IDLE) {
            val r = rnd.nextInt(100)
            if (r < 18) view?.play(PetSt.THINK) else if (r < 36) view?.play(PetSt.SWEEP)
        }
    }

    private fun beginMove() {
        moving = true
        carrying = rnd.nextInt(100) < 25
        val ang = rnd.nextFloat() * 2f * Math.PI.toFloat()
        val sp = cfg.speedDp * d * (0.7f + 0.6f * rnd.nextFloat())
        vx = cos(ang) * sp
        vy = sin(ang) * sp
        phaseEnd = SystemClock.uptimeMillis() + randMs(cfg.moveMin, cfg.moveMax)
    }

    private fun pauseFor(ms: Long) {
        moving = false
        carrying = false
        vx = 0f
        vy = 0f
        view?.walking = false
        phaseEnd = max(phaseEnd, SystemClock.uptimeMillis() + ms)
    }

    private fun step() {
        val now = SystemClock.uptimeMillis()
        val dt = ((now - lastTick) / 1000f).coerceIn(0f, 0.1f)
        lastTick = now
        val v = view ?: return

        // 跳跃（进出迷你模式）：抛物线
        val j = jump
        if (j != null) {
            val k = ((now - j.t0).toFloat() / j.dur).coerceIn(0f, 1f)
            x = j.sx + (j.tx - j.sx) * k
            y = j.sy + (j.ty - j.sy) * k - 4f * j.arc * k * (1f - k)
            pushPos()
            if (k >= 1f) {
                jump = null
                v.airborne = false
                x = j.tx
                y = j.ty
                pushPos()
                beginPause()
            }
            return
        }
        if (dragging) return

        // 眼睛：触摸 5 秒后慢慢看回正前方
        if (now > lookUntil) {
            v.lookX = 0f
            v.lookY = 0f
        }

        val want = desiredBase(now)
        v.switchBase(want)

        if (mini != 0) {
            x += (miniTargetX() - x) * 0.25f
            y = y.coerceIn(0f, maxY)
            pushPos()
            v.walking = false
            return
        }

        val mobile = want == PetSt.IDLE || want == PetSt.CARRY
        if (!mobile || !cfg.wander) {
            if (moving) beginPause()
            v.walking = false
            return
        }

        if (now >= phaseEnd) {
            if (moving) beginPause() else beginMove()
        }
        if (moving) {
            x += vx * dt
            y += vy * dt
            if (x < 0f) {
                x = 0f
                vx = abs(vx)
            } else if (x > maxX) {
                x = maxX
                vx = -abs(vx)
            }
            if (y < 0f) {
                y = 0f
                vy = abs(vy)
            } else if (y > maxY) {
                y = maxY
                vy = -abs(vy)
            }
            pushPos()
        }
        v.walking = moving
    }
}
