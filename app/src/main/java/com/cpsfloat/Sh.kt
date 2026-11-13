package com.cpsfloat

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object Sh {
    fun alive(): Boolean = try { Shizuku.pingBinder() } catch (e: Throwable) { false }

    fun ready(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) { false }

    /** Shizuku.newProcess 在 13.x 中为非公开方法，这里用反射调用。 */
    fun newProcess(cmd: String): Process {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        return m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
    }

    fun run(cmd: String): String {
        val p = newProcess(cmd)
        val out = p.inputStream.bufferedReader().readText() + p.errorStream.bufferedReader().readText()
        p.waitFor()
        return out
    }
}
