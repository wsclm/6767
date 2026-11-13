package com.cpsfloat

import android.graphics.Typeface
import java.io.File

object Fonts {
    val presets = listOf(
        "默认" to "sans-serif",
        "细体" to "sans-serif-light",
        "中粗" to "sans-serif-medium",
        "特粗" to "sans-serif-black",
        "窄体" to "sans-serif-condensed",
        "衬线" to "serif",
        "等宽" to "monospace",
        "手写" to "casual",
        "草书" to "cursive",
        "衬线等宽" to "serif-monospace"
    )

    fun typeface(key: String, bold: Boolean): Typeface {
        val base = if (key.startsWith("file:")) {
            val f = File(key.removePrefix("file:"))
            try { if (f.exists()) Typeface.createFromFile(f) else Typeface.DEFAULT } catch (e: Throwable) { Typeface.DEFAULT }
        } else {
            Typeface.create(key, Typeface.NORMAL)
        }
        return Typeface.create(base, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    fun label(key: String): String =
        if (key.startsWith("file:")) "自定义字体文件"
        else presets.firstOrNull { it.second == key }?.first ?: "默认"
}
