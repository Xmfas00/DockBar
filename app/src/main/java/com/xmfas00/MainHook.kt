package com.xmfas00.dockbar

import android.app.Activity
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.json.JSONObject
import java.io.File

class MainHook : IXposedHookLoadPackage {

    private val targets = setOf("com.mi.android.globallauncher", "com.miui.home")
    private var dockView: View? = null
    private var plateView: View? = null

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName !in targets) return
        XposedBridge.log("DockBar: модуль активен в ${lpparam.packageName}")

        XposedHelpers.findAndHookMethod(Activity::class.java, "onResume", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as Activity
                val root = activity.window?.decorView ?: return
                root.post {
                    val dock = dockView ?: findDock(root)?.also {
                        dockView = it
                        XposedBridge.log("DockBar: док найден ${it.javaClass.name}")
                    }
                    if (dock != null) applyStyle(dock)
                    else XposedBridge.log("DockBar: док не найден")
                }
            }
        })
    }

    private fun findDock(view: View): View? {
        if (view.javaClass.name.contains("Hotseat", true) || view.javaClass.name.contains("HotSeats", true)) return view
        try {
            if (view.id != View.NO_ID && view.resources.getResourceEntryName(view.id).equals("hotseat", true)) return view
        } catch (e: Throwable) { }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findDock(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun applyStyle(dock: View) {
        try {
            val s = loadSettings()
            val density = dock.resources.displayMetrics.density

            if (dock is FrameLayout) {
                val plate = if (plateView != null && plateView!!.parent == dock) plateView!!
                else View(dock.context).also {
                    plateView = it
                    dock.addView(it, 0)
                }

                val w = if (s.dockWidth > 0) (s.dockWidth * density).toInt() else ViewGroup.LayoutParams.MATCH_PARENT
                val lp = FrameLayout.LayoutParams(w, (s.dockHeight * density).toInt())
                lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                lp.bottomMargin = (-s.verticalOffset * density).toInt()
                plate.layoutParams = lp
                plate.isClickable = false
                plate.isFocusable = false

                val miBlur = applyMiBlur(plate, s)
                if (!miBlur) applyAospBlur(plate, s.blurRadius)

                val bg = GradientDrawable()
                bg.setColor(if (miBlur) 0x00000000 else s.bgColor)
                bg.cornerRadius = s.cornerRadius * density
                plate.background = bg

                plate.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, s.cornerRadius * density)
                    }
                }
                plate.clipToOutline = true
            } else {
                val miBlur = applyMiBlur(dock, s)
                if (!miBlur) applyAospBlur(dock, s.blurRadius)
                val bg = GradientDrawable()
                bg.setColor(if (miBlur) 0x00000000 else s.bgColor)
                bg.cornerRadius = s.cornerRadius * density
                dock.background = bg
                dock.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, s.cornerRadius * density)
                    }
                }
                dock.clipToOutline = true
            }
            XposedBridge.log("DockBar: стили применены")
        } catch (e: Throwable) {
            XposedBridge.log("DockBar Error: ${e.message}")
        }
    }

    private fun applyMiBlur(v: View, s: DockSettings): Boolean {
        return try {
            val view = View::class.java
            val density = v.resources.displayMetrics.density

            view.getDeclaredMethod("setPassWindowBlurEnabled", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(v, true)
            view.getDeclaredMethod("setMiViewBlurMode", Int::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(v, 1)
            try {
                view.getDeclaredMethod("setMiBackgroundBlurMode", Int::class.javaPrimitiveType)
                    .apply { isAccessible = true }.invoke(v, 1)
                view.getDeclaredMethod("setMiBackgroundBlurRadius", Int::class.javaPrimitiveType)
                    .apply { isAccessible = true }.invoke(v, s.blurRadius)
            } catch (e: Throwable) { }
            view.getDeclaredMethod("clearMiBackgroundBlendColor")
                .apply { isAccessible = true }.invoke(v)
            view.getDeclaredMethod("addMiBackgroundBlendColor", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(v, s.bgColor, 100)

            v.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, s.cornerRadius * density)
                }
            }
            v.clipToOutline = true

            XposedBridge.log("DockBar: MiBlur применён")
            true
        } catch (e: Throwable) {
            XposedBridge.log("DockBar: MiBlur недоступен ${e.message}")
            false
        }
    }

    private fun applyAospBlur(v: View, radius: Int) {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                val m = View::class.java.getMethod("setBackgroundBlurRadius", Int::class.javaPrimitiveType)
                m.invoke(v, radius)
            } catch (e: Throwable) {
                XposedBridge.log("DockBar: blur error ${e.message}")
            }
        }
    }

    private fun loadSettings(): DockSettings {
        val file = File("/data/local/tmp/my_dock_config.json")
        if (file.exists()) {
            try {
                val json = JSONObject(file.readText())
                return DockSettings(
                    json.optInt("blurRadius", 60),
                    json.optDouble("cornerRadius", 24.0).toFloat(),
                    json.optInt("dockHeight", 80),
                    json.optInt("dockWidth", 0),
                    json.optInt("bgColor", 0x33000000),
                    json.optInt("verticalOffset", 0)
                )
            } catch (e: Exception) { }
        }
        return DockSettings(60, 24f, 80, 0, 0x33000000, 0)
    }
}

data class DockSettings(
    val blurRadius: Int,
    val cornerRadius: Float,
    val dockHeight: Int,
    val dockWidth: Int,
    val bgColor: Int,
    val verticalOffset: Int
)