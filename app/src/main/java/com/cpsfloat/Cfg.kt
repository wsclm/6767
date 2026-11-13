package com.cpsfloat

import android.content.Context
import android.content.SharedPreferences
import android.util.DisplayMetrics
import android.view.WindowManager

const val SLOTS = 2

object Modes {
    const val FULL = 0    // 全屏点击统计（Shizuku）
    const val REGION = 1  // 指定区域统计（Shizuku）
    const val COVER = 2   // 区域覆盖统计（无需 Shizuku，会拦截该区域点击）
}

data class SlotCfg(
    val enabled: Boolean,
    val mode: Int,
    val rx: Int, val ry: Int, val rw: Int, val rh: Int,   // 点击区域（屏幕像素）
    val x: Int, val y: Int,                                // CPS 显示中心位置（屏幕像素）
    val sizeSp: Int,
    val textColor: Int,
    val bgColor: Int,
    val radiusDp: Int,
    val bgW: Int,                                          // 背景宽度 dp，0 = 自适应
    val bgH: Int,                                          // 背景高度 dp，0 = 自适应
    val scalePct: Int,                                     // 整体显示缩放 %（背景 + 文字一起缩放，以中心为基点）
    val template: String,
    val font: String,
    val bold: Boolean,
    val hideText: Boolean,
    val idleBgOnly: Boolean,
    val locked: Boolean
) {
    val hasRegion get() = rw > 0 && rh > 0
}

data class PetCfg(
    val enabled: Boolean = false,
    val sizeDp: Int = 96,
    val speedDp: Int = 60,                 // 移动速度 dp/秒
    val moveMin: Int = 1, val moveMax: Int = 4,     // 每次移动的时长范围（秒）
    val pauseMin: Int = 2, val pauseMax: Int = 8,   // 每次停止的时长范围（秒）
    val wander: Boolean = true,            // 随机走动
    val eyeTrack: Boolean = true,          // 眼睛跟随最近一次触摸
    val reactCps: Boolean = true,          // 随点击速度切换动作
    val sleepAfter: Int = 60,              // 无操作多少秒后睡觉
    val miniEdge: Boolean = true,          // 拖到屏幕边缘自动进入迷你模式
    val miniSide: Int = 0,                 // 迷你模式：0 = 关，1 = 左边缘，2 = 右边缘
    val dnd: Boolean = false               // 勿扰模式：一直睡觉，不响应点击统计
)

/** 所有设置改动即时写入 SharedPreferences，服务监听变化后自动同步，无需“保存并应用”。 */
object Cfg {
    fun prefs(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("cps_cfg", Context.MODE_PRIVATE)

    private fun k(i: Int, n: String) = "s${i}_$n"

    // ---- 横/竖屏分别保存位置与区域；某个方向没保存过时，按比例从另一个方向换算 ----
    @Suppress("DEPRECATION")
    private fun real(c: Context): IntArray {
        val wm = c.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        return intArrayOf(dm.widthPixels, dm.heightPixels)
    }

    fun isLandscape(c: Context): Boolean { val r = real(c); return r[0] > r[1] }

    private fun sfx(land: Boolean) = if (land) "_l" else ""

    /** 从“另一个方向”换算到“当前方向”的 x、y 缩放系数 */
    private fun scale(c: Context, land: Boolean): FloatArray {
        val r = real(c)
        val a = minOf(r[0], r[1]).toFloat()
        val b = maxOf(r[0], r[1]).toFloat()
        return if (land) floatArrayOf(b / a, a / b) else floatArrayOf(a / b, b / a)
    }

    /** CPS 显示的默认中心位置（屏幕像素） */
    fun defPos(i: Int): IntArray = intArrayOf(300, 320 + i * 200)

    private fun pos(p: SharedPreferences, c: Context, i: Int, dx: Int, dy: Int, land: Boolean): IntArray {
        val s = sfx(land); val o = sfx(!land)
        if (p.contains(k(i, "cx$s"))) return intArrayOf(p.getInt(k(i, "cx$s"), dx), p.getInt(k(i, "cy$s"), dy))
        if (p.contains(k(i, "cx$o"))) {
            val f = scale(c, land)
            return intArrayOf((p.getInt(k(i, "cx$o"), dx) * f[0]).toInt(), (p.getInt(k(i, "cy$o"), dy) * f[1]).toInt())
        }
        return intArrayOf(dx, dy)
    }

    private fun region(p: SharedPreferences, c: Context, i: Int, land: Boolean): IntArray {
        val s = sfx(land); val o = sfx(!land)
        if (p.contains(k(i, "rw$s"))) return intArrayOf(
            p.getInt(k(i, "rx$s"), 0), p.getInt(k(i, "ry$s"), 0),
            p.getInt(k(i, "rw$s"), 0), p.getInt(k(i, "rh$s"), 0))
        if (p.contains(k(i, "rw$o"))) {
            val f = scale(c, land)
            return intArrayOf(
                (p.getInt(k(i, "rx$o"), 0) * f[0]).toInt(), (p.getInt(k(i, "ry$o"), 0) * f[1]).toInt(),
                (p.getInt(k(i, "rw$o"), 0) * f[0]).toInt(), (p.getInt(k(i, "rh$o"), 0) * f[1]).toInt())
        }
        return intArrayOf(0, 0, 0, 0)
    }

    fun load(c: Context, i: Int): SlotCfg {
        val p = prefs(c)
        fun gi(n: String, d: Int) = p.getInt(k(i, n), d)
        fun gb(n: String, d: Boolean) = p.getBoolean(k(i, n), d)
        val land = isLandscape(c)
        val dp0 = defPos(i)
        val ps = pos(p, c, i, dp0[0], dp0[1], land)
        val rg = region(p, c, i, land)
        return SlotCfg(
            enabled = gb("enabled", false),
            mode = gi("mode", Modes.FULL),
            rx = rg[0], ry = rg[1], rw = rg[2], rh = rg[3],
            x = ps[0], y = ps[1],
            sizeSp = gi("size", 22),
            textColor = gi("textColor", 0xFFFFFFFF.toInt()),
            bgColor = gi("bgColor", 0xAA000000.toInt()),
            radiusDp = gi("radius", 12),
            bgW = gi("bgW", 0),
            bgH = gi("bgH", 0),
            scalePct = gi("scale", 100),
            template = p.getString(k(i, "template"), "{cps} CPS") ?: "{cps} CPS",
            font = p.getString(k(i, "font"), "sans-serif") ?: "sans-serif",
            bold = gb("bold", true),
            hideText = gb("hideText", false),
            idleBgOnly = gb("idleBgOnly", false),
            locked = gb("locked", false)
        )
    }

    fun putInt(c: Context, i: Int, n: String, v: Int) = prefs(c).edit().putInt(k(i, n), v).apply()
    fun putBool(c: Context, i: Int, n: String, v: Boolean) = prefs(c).edit().putBoolean(k(i, n), v).apply()
    fun putStr(c: Context, i: Int, n: String, v: String) = prefs(c).edit().putString(k(i, n), v).apply()

    /** 保存到当前屏幕方向（横/竖屏各存一份） */
    fun putRegion(c: Context, i: Int, x: Int, y: Int, w: Int, h: Int) {
        val s = sfx(isLandscape(c))
        prefs(c).edit()
            .putInt(k(i, "rx$s"), x).putInt(k(i, "ry$s"), y)
            .putInt(k(i, "rw$s"), w).putInt(k(i, "rh$s"), h).apply()
    }

    fun putPos(c: Context, i: Int, x: Int, y: Int) {
        val s = sfx(isLandscape(c))
        prefs(c).edit().putInt(k(i, "cx$s"), x).putInt(k(i, "cy$s"), y).apply()
    }

    fun anyEnabled(c: Context) = (0 until SLOTS).any { load(c, it).enabled }

    // ---- 桌宠 ----
    fun loadPet(c: Context): PetCfg {
        val p = prefs(c)
        val d = PetCfg()
        return PetCfg(
            enabled = p.getBoolean("pet_enabled", d.enabled),
            sizeDp = p.getInt("pet_size", d.sizeDp),
            speedDp = p.getInt("pet_speed", d.speedDp),
            moveMin = p.getInt("pet_mmin", d.moveMin), moveMax = p.getInt("pet_mmax", d.moveMax),
            pauseMin = p.getInt("pet_pmin", d.pauseMin), pauseMax = p.getInt("pet_pmax", d.pauseMax),
            wander = p.getBoolean("pet_wander", d.wander),
            eyeTrack = p.getBoolean("pet_eye", d.eyeTrack),
            reactCps = p.getBoolean("pet_react", d.reactCps),
            sleepAfter = p.getInt("pet_sleep", d.sleepAfter),
            miniEdge = p.getBoolean("pet_miniedge", d.miniEdge),
            miniSide = p.getInt("pet_mini", d.miniSide).coerceIn(0, 2),
            dnd = p.getBoolean("pet_dnd", d.dnd)
        )
    }

    fun putPetInt(c: Context, n: String, v: Int) = prefs(c).edit().putInt("pet_$n", v).apply()
    fun putPetBool(c: Context, n: String, v: Boolean) = prefs(c).edit().putBoolean("pet_$n", v).apply()

    /** 桌宠位置：占屏幕可移动范围的千分比（横竖屏通用） */
    fun petPos(c: Context): IntArray? {
        val p = prefs(c)
        if (!p.contains("pet_fx")) return null
        return intArrayOf(p.getInt("pet_fx", 0), p.getInt("pet_fy", 0))
    }

    fun putPetPos(c: Context, fx: Int, fy: Int) =
        prefs(c).edit().putInt("pet_fx", fx).putInt("pet_fy", fy).apply()

    fun anyActive(c: Context) = anyEnabled(c) || loadPet(c).enabled
}
