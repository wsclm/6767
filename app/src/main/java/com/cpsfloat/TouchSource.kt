package com.cpsfloat

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * 通过 Shizuku 执行 `getevent -l` 读取触摸事件，只关心“按下”，
 * 并把原始触摸坐标换算成当前屏幕方向下的像素坐标。
 */
class TouchSource(ctx: Context, private val onDown: (Int, Int) -> Unit) {
    private val wm = ctx.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    @Volatile private var running = false
    private var proc: Process? = null

    private class Dev(val maxX: Int, val maxY: Int) {
        var cur = 0
        val xs = IntArray(16)
        val ys = IntArray(16)
        val pend = BooleanArray(16)
    }

    fun start() {
        if (running) return
        running = true
        Thread { loop() }.apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        try { proc?.destroy() } catch (_: Throwable) { }
        proc = null
    }

    private fun readRanges(): HashMap<String, Dev> {
        val devs = HashMap<String, Dev>()
        val text = Sh.run("getevent -lp")
        var path: String? = null
        var mx = 0
        var my = 0
        fun flush() {
            val p = path
            if (p != null && mx > 0 && my > 0) devs[p] = Dev(mx, my)
        }
        val addRe = Regex("add device \\d+: (\\S+)")
        val xRe = Regex("ABS_MT_POSITION_X\\s*:.*max (\\d+)")
        val yRe = Regex("ABS_MT_POSITION_Y\\s*:.*max (\\d+)")
        for (line in text.lineSequence()) {
            val a = addRe.find(line)
            if (a != null) { flush(); path = a.groupValues[1]; mx = 0; my = 0; continue }
            xRe.find(line)?.let { mx = it.groupValues[1].toInt() }
            yRe.find(line)?.let { my = it.groupValues[1].toInt() }
        }
        flush()
        return devs
    }

    @Suppress("DEPRECATION")
    private fun map(dev: Dev, x: Int, y: Int): IntArray {
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        val pw = minOf(dm.widthPixels, dm.heightPixels).toFloat()
        val ph = maxOf(dm.widthPixels, dm.heightPixels).toFloat()
        val px = x.toFloat() / dev.maxX * pw
        val py = y.toFloat() / dev.maxY * ph
        return when (wm.defaultDisplay.rotation) {
            1 -> intArrayOf(py.toInt(), (pw - px).toInt())
            2 -> intArrayOf((pw - px).toInt(), (ph - py).toInt())
            3 -> intArrayOf((ph - py).toInt(), px.toInt())
            else -> intArrayOf(px.toInt(), py.toInt())
        }
    }

    private fun loop() {
        try {
            val devs = readRanges()
            if (devs.isEmpty()) return
            val p = Sh.newProcess("exec getevent -l")
            proc = p
            val reader = p.inputStream.bufferedReader()
            val ws = Regex("\\s+")
            while (running) {
                val line = reader.readLine() ?: break
                val idx = line.indexOf(':')
                if (idx <= 0) continue
                val dev = devs[line.substring(0, idx)] ?: continue
                val t = line.substring(idx + 1).trim().split(ws)
                if (t.size < 3) continue
                val code = t[1]
                val value = t[2]
                when (code) {
                    "ABS_MT_SLOT" -> dev.cur = (value.toIntOrNull(16) ?: 0).coerceIn(0, 15)
                    "ABS_MT_TRACKING_ID" -> {
                        if (value.lowercase() != "ffffffff") dev.pend[dev.cur] = true
                    }
                    "ABS_MT_POSITION_X" -> dev.xs[dev.cur] = value.toLongOrNull(16)?.toInt() ?: 0
                    "ABS_MT_POSITION_Y" -> dev.ys[dev.cur] = value.toLongOrNull(16)?.toInt() ?: 0
                    "SYN_REPORT" -> {
                        for (i in 0 until 16) {
                            if (dev.pend[i]) {
                                dev.pend[i] = false
                                val m = map(dev, dev.xs[i], dev.ys[i])
                                onDown(m[0], m[1])
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        } finally {
            running = false
        }
    }
}
