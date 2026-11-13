package com.cpsfloat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import rikka.shizuku.Shizuku

class FloatService : Service() {

    companion object {
        const val ACTION_SYNC = "com.cpsfloat.SYNC"
        const val ACTION_PICK = "com.cpsfloat.PICK"
        const val ACTION_STOP = "com.cpsfloat.STOP"
        const val EXTRA_SLOT = "slot"
        private const val CHANNEL = "cps_overlay"

        fun sync(c: Context) {
            val i = Intent(c, FloatService::class.java).setAction(ACTION_SYNC)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        }

        fun pick(c: Context, slot: Int) {
            val i = Intent(c, FloatService::class.java).setAction(ACTION_PICK).putExtra(EXTRA_SLOT, slot)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        }
    }

    private lateinit var wm: WindowManager
    private val h = Handler(Looper.getMainLooper())
    private val views = arrayOfNulls<SlotView>(SLOTS)
    private var pet: PetController? = null
    private var picker: RegionPicker? = null
    private var pickerSlot = 0
    private var touch: TouchSource? = null
    private var warnedShizuku = false
    private var lastRot = -1

    private val refreshRun = Runnable { refresh() }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        h.removeCallbacks(refreshRun)
        h.postDelayed(refreshRun, 30)
    }
    private val binderListener = Shizuku.OnBinderReceivedListener {
        h.post {
            warnedShizuku = false
            refresh()
        }
    }
    private val ticker = object : Runnable {
        override fun run() {
            checkRotation()
            var top = 0
            for (v in views) {
                v?.tick()
                val c = v?.cps() ?: 0
                if (c > top) top = c
            }
            pet?.onCps(top)
            h.postDelayed(this, 100)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        pet = PetController(this, wm)
        startFg()
        lastRot = rotation()
        Cfg.prefs(this).registerOnSharedPreferenceChangeListener(prefListener)
        try { Shizuku.addBinderReceivedListenerSticky(binderListener) } catch (_: Throwable) { }
        h.post(ticker)
    }

    @Suppress("DEPRECATION")
    private fun rotation(): Int = wm.defaultDisplay.rotation

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        checkRotation()
    }

    /** 屏幕方向变化：按新方向重新读取位置/区域并重新布局；若区域选择窗口开着则按新屏幕重开 */
    private fun checkRotation() {
        val r = rotation()
        if (r == lastRot) return
        lastRot = r
        pet?.onScreenChanged()
        refresh()
        if (picker != null) {
            val s = pickerSlot
            closePicker()
            openPicker(s)
        }
    }

    private fun openPicker(slot: Int) {
        pickerSlot = slot
        val p = RegionPicker(this, slot) { picker = null; refresh() }
        picker = p
        p.show()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                for (i in 0 until SLOTS) Cfg.putBool(this, i, "enabled", false)
                Cfg.putPetBool(this, "enabled", false)
                closePicker()
                refresh()
            }
            ACTION_PICK -> {
                closePicker()
                val slot = intent.getIntExtra(EXTRA_SLOT, 0).coerceIn(0, SLOTS - 1)
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                } else {
                    openPicker(slot)
                }
                refresh()
            }
            else -> refresh()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        Cfg.prefs(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        try { Shizuku.removeBinderReceivedListener(binderListener) } catch (_: Throwable) { }
        touch?.stop()
        closePicker()
        pet?.remove()
        for (v in views) v?.remove()
        super.onDestroy()
    }

    private fun closePicker() {
        picker?.dismiss()
        picker = null
    }

    private fun refresh() {
        val cfgs = (0 until SLOTS).map { Cfg.load(this, it) }
        val petCfg = Cfg.loadPet(this)
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }
        for (i in 0 until SLOTS) {
            if (cfgs[i].enabled) {
                val v = views[i] ?: SlotView(this, wm, i).also {
                    it.onTouch = { tx, ty -> pet?.onTouchPoint(tx, ty) }
                    views[i] = it
                }
                v.apply(cfgs[i])
            } else {
                views[i]?.remove()
                views[i] = null
            }
        }
        pet?.apply(petCfg)

        val needShizuku = cfgs.any { it.enabled && it.mode != Modes.COVER }
        if (needShizuku) {
            if (Sh.ready()) {
                if (touch == null) touch = TouchSource(this) { x, y -> h.post { onRawDown(x, y) } }
                touch?.start()
            } else {
                touch?.stop()
                touch = null
                if (!warnedShizuku) {
                    warnedShizuku = true
                    Toast.makeText(this, "全屏/区域统计需要先启动并授权 Shizuku", Toast.LENGTH_LONG).show()
                    pet?.flashError()
                }
            }
        } else {
            touch?.stop()
            touch = null
        }
        if (cfgs.none { it.enabled } && !petCfg.enabled && picker == null) stopSelf()
    }

    private fun onRawDown(x: Int, y: Int) {
        // 桌宠：眼睛跟随触摸、有操作时保持清醒
        pet?.onTouchPoint(x, y)
        // 点在 CPS 显示窗口或桌宠上（拖动它们）不计入点击
        if (views.any { it?.inDisplay(x, y) == true }) return
        if (pet?.contains(x, y) == true) return
        for (v in views) v?.onRawDown(x, y)
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "CPS 悬浮窗", NotificationManager.IMPORTANCE_LOW))
        }
        val stop = PendingIntent.getService(
            this, 0, Intent(this, FloatService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("CPS 悬浮窗运行中")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "停止", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }
}

/** 一个 CPS 显示窗口（可拖动）+ 可选的区域覆盖层。 */
class SlotView(private val ctx: Context, private val wm: WindowManager, private val idx: Int) {
    private var cfg: SlotCfg = Cfg.load(ctx, idx)
    private val times = ArrayList<Long>()

    /** 区域覆盖模式下收到点击时通知（屏幕坐标），桌宠用来让眼睛跟随。 */
    var onTouch: ((Int, Int) -> Unit)? = null

    private var tv: CpsView? = null
    private var tvLp: WindowManager.LayoutParams? = null
    private var cover: View? = null
    private var coverLp: WindowManager.LayoutParams? = null
    private var lastText: String? = null

    // CPS 显示的中心（屏幕像素）。窗口大小变化（文字变长、调背景、缩放）时始终围绕这个中心展开。
    private var cx = 0
    private var cy = 0

    @Suppress("DEPRECATION")
    private fun screen(): IntArray {
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        return intArrayOf(dm.widthPixels, dm.heightPixels)
    }

    private fun baseFlags(touchable: Boolean): Int {
        // FLAG_SPLIT_TOUCH：允许一根手指按在别的窗口上时，这个窗口仍能接收新的手指按下
        var f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_SPLIT_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!touchable) f = f or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return f
    }

    private fun newLp(w: Int, h: Int, touchable: Boolean) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        baseFlags(touchable), PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        else if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    fun apply(c: SlotCfg) {
        cfg = c
        applyCover()
        applyDisplay()
    }

    // ---------- 区域覆盖层（模式 2） ----------
    private fun applyCover() {
        val want = cfg.mode == Modes.COVER && cfg.hasRegion
        if (!want) {
            cover?.let { try { wm.removeView(it) } catch (_: Throwable) { } }
            cover = null
            coverLp = null
            return
        }
        val sc = screen()
        val cw = cfg.rw.coerceIn(1, sc[0])
        val ch = cfg.rh.coerceIn(1, sc[1])
        val rx = cfg.rx.coerceIn(0, sc[0] - cw)
        val ry = cfg.ry.coerceIn(0, sc[1] - ch)
        var created = false
        if (cover == null) {
            val v = View(ctx)
            v.setBackgroundColor(0x14000000)
            v.setOnTouchListener { _, e ->
                if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                    click()
                    val i = e.actionIndex
                    // 触摸点：窗口内坐标 + 窗口位置
                    val l0 = coverLp
                    onTouch?.invoke((e.getX(i) + (l0?.x ?: 0)).toInt(), (e.getY(i) + (l0?.y ?: 0)).toInt())
                }
                true
            }
            val l = newLp(cw, ch, true)
            l.x = rx
            l.y = ry
            try {
                wm.addView(v, l)
                cover = v
                coverLp = l
                created = true
            } catch (_: Throwable) { }
        } else {
            val l = coverLp
            if (l != null) {
                l.x = rx
                l.y = ry
                l.width = cw
                l.height = ch
                try { wm.updateViewLayout(cover, l) } catch (_: Throwable) { }
            }
        }
        // 覆盖层新建后会盖在显示窗口上面，重新添加显示窗口让它回到最上层
        if (created) {
            tv?.let { try { wm.removeView(it) } catch (_: Throwable) { } }
            tv = null
            tvLp = null
        }
    }

    // ---------- CPS 显示窗口 ----------
    private fun applyDisplay() {
        if (tv == null) {
            val t = CpsView(ctx)
            val l = newLp(1, 1, !cfg.locked)
            attachDrag(t, l)
            try {
                wm.addView(t, l)
                tv = t
                tvLp = l
            } catch (_: Throwable) {
                return
            }
            lastText = null
        }
        val t = tv ?: return
        val l = tvLp ?: return
        val sc = screen()

        t.limitW = sc[0]
        t.limitH = sc[1]
        t.configure(cfg)
        val s = currentText()
        lastText = s
        t.text = s

        val touchable = !cfg.locked
        val has = (l.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) == 0
        if (touchable != has) {
            l.flags = if (touchable) l.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            else l.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        cx = cfg.x
        cy = cfg.y
        place(t, l, sc)
        try { wm.updateViewLayout(t, l) } catch (_: Throwable) { }
    }

    /** 按“中心 + 控件大小”算出窗口位置，并限制在屏幕内（旋转后也不会跑出屏幕）。 */
    private fun place(t: CpsView, l: WindowManager.LayoutParams, sc: IntArray) {
        val cs = t.measureContent()
        l.width = cs[0]
        l.height = cs[1]
        l.x = (cx - cs[0] / 2f).toInt().coerceIn(0, (sc[0] - cs[0]).coerceAtLeast(0))
        l.y = (cy - cs[1] / 2f).toInt().coerceIn(0, (sc[1] - cs[1]).coerceAtLeast(0))
    }

    private fun attachDrag(t: CpsView, l: WindowManager.LayoutParams) {
        var dx = 0f
        var dy = 0f
        var sx = 0
        var sy = 0
        t.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dx = e.rawX
                    dy = e.rawY
                    sx = l.x
                    sy = l.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val s = screen()
                    l.x = (sx + (e.rawX - dx).toInt()).coerceIn(0, (s[0] - l.width).coerceAtLeast(0))
                    l.y = (sy + (e.rawY - dy).toInt()).coerceIn(0, (s[1] - l.height).coerceAtLeast(0))
                    cx = l.x + l.width / 2
                    cy = l.y + l.height / 2
                    try { wm.updateViewLayout(t, l) } catch (_: Throwable) { }
                }
                MotionEvent.ACTION_UP -> {
                    cx = l.x + l.width / 2
                    cy = l.y + l.height / 2
                    Cfg.putPos(ctx, idx, cx, cy)
                }
            }
            true
        }
    }

    // ---------- 计数 ----------
    fun inDisplay(x: Int, y: Int): Boolean {
        val l = tvLp ?: return false
        if (tv == null) return false
        return x >= l.x && x <= l.x + l.width && y >= l.y && y <= l.y + l.height
    }

    fun onRawDown(x: Int, y: Int) {
        when (cfg.mode) {
            Modes.FULL -> click()
            Modes.REGION -> if (cfg.hasRegion &&
                x >= cfg.rx && x < cfg.rx + cfg.rw && y >= cfg.ry && y < cfg.ry + cfg.rh) click()
        }
    }

    private fun click() {
        times.add(SystemClock.uptimeMillis())
        updateText()
    }

    private fun prune() {
        val now = SystemClock.uptimeMillis()
        while (times.isNotEmpty() && now - times[0] > 1000) times.removeAt(0)
    }

    /** 最近 1 秒内的点击数 */
    fun cps(): Int {
        prune()
        return times.size
    }

    fun tick() = updateText()

    private fun currentText(): String {
        val n = cps()
        return when {
            cfg.hideText -> ""
            cfg.idleBgOnly && n == 0 -> ""
            else -> cfg.template.replace("{cps}", n.toString())
        }
    }

    private fun updateText() {
        val t = tv ?: return
        val l = tvLp ?: return
        val s = currentText()
        if (s == lastText) return
        lastText = s
        t.text = s
        // 文字长度变化后，窗口围绕中心重新展开
        val cs = t.measureContent()
        if (cs[0] != l.width || cs[1] != l.height) {
            place(t, l, screen())
            try { wm.updateViewLayout(t, l) } catch (_: Throwable) { }
        }
    }

    fun remove() {
        tv?.let { try { wm.removeView(it) } catch (_: Throwable) { } }
        cover?.let { try { wm.removeView(it) } catch (_: Throwable) { } }
        tv = null
        cover = null
        tvLp = null
        coverLp = null
    }
}
