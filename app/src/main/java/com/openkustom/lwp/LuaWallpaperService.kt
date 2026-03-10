package com.openkustom.lwp

import android.graphics.*
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.WindowInsets
import android.widget.Toast
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.File

class LuaWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = LuaEngine()

    inner class LuaEngine : Engine() {
        private val globals = JsePlatform.standardGlobals()
        private var topInset = 0
        private val fillPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private val textPaint = Paint().apply { isAntiAlias = true }
        private var lastLoadedContent: String? = null

        private fun showToast(message: String) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            }
        }

        override fun onApplyWindowInsets(insets: WindowInsets) {
            val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
            topInset = systemBars.top
            globals.set("top_inset", LuaValue.valueOf(topInset.toDouble()))
        }

        override fun onTouchEvent(event: MotionEvent) {
            globals.set("touch_x", LuaValue.valueOf(event.x.toDouble()))
            globals.set("touch_y", LuaValue.valueOf(event.y.toDouble()))
            when (event.action) {
                MotionEvent.ACTION_DOWN -> globals.set("is_touching", LuaValue.valueOf(true))
                MotionEvent.ACTION_UP -> globals.set("is_touching", LuaValue.valueOf(false))
            }
            drawFrame()
        }

        private fun drawFrame() {
            val canvas = surfaceHolder.lockCanvas() ?: return
            
            try {
                // 1. CLEAR SCREEN WITH DEFAULT COLOR
                canvas.drawColor(Color.parseColor("#121212"))

                // 2. UPDATE GLOBALS
                globals.set("width", LuaValue.valueOf(canvas.width.toDouble()))
                globals.set("height", LuaValue.valueOf(canvas.height.toDouble()))
                val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
                globals.set("battery", LuaValue.valueOf(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)))

                // 3. DEFINE DRAWING API
                val draw = LuaValue.tableOf()

                // draw.rect(x, y, w, h, hex)
                draw.set("rect", object : VarArgFunction() {
                    override fun invoke(args: org.luaj.vm2.Varargs): org.luaj.vm2.Varargs {
                        fillPaint.color = Color.parseColor(args.checkjstring(5))
                        canvas.drawRect(args.checkdouble(1).toFloat(), args.checkdouble(2).toFloat(),
                            (args.checkdouble(1) + args.checkdouble(3)).toFloat(),
                            (args.checkdouble(2) + args.checkdouble(4)).toFloat(), fillPaint)
                        return LuaValue.NONE
                    }
                })

                // draw.circle(x, y, radius, hex)
                draw.set("circle", object : VarArgFunction() {
                    override fun invoke(args: org.luaj.vm2.Varargs): org.luaj.vm2.Varargs {
                        fillPaint.color = Color.parseColor(args.checkjstring(4))
                        canvas.drawCircle(args.checkdouble(1).toFloat(), args.checkdouble(2).toFloat(),
                            args.checkdouble(3).toFloat(), fillPaint)
                        return LuaValue.NONE
                    }
                })

                // draw.text(text, x, y, size, hex)
                draw.set("text", object : VarArgFunction() {
                    override fun invoke(args: org.luaj.vm2.Varargs): org.luaj.vm2.Varargs {
                        textPaint.apply {
                            textSize = args.optdouble(4, 40.0).toFloat()
                            color = Color.parseColor(args.optjstring(5, "#FFFFFF"))
                        }
                        canvas.drawText(args.checkjstring(1), args.checkdouble(2).toFloat(), args.checkdouble(3).toFloat(), textPaint)
                        return LuaValue.NONE
                    }
                })
                globals.set("draw", draw)

                // 4. LOAD AND EXECUTE SCRIPT
                val scriptFile = File("/sdcard/OpenKustom/logic.lua")
                if (scriptFile.exists()) {
                    val content = scriptFile.readText()
                    if (content != lastLoadedContent) {
                        showToast("Script Updated")
                        lastLoadedContent = content
                    }

                    val chunk = globals.load(content)
                    val result = chunk.call()

                    // 5. IF LUA RETURNS A COLOR, RE-DRAW BACKGROUND AND ELEMENTS
                    if (result.isstring()) {
                        canvas.drawColor(Color.parseColor(result.tojstring()))
                        chunk.call() // Re-run to draw UI elements OVER the new background
                    }
                } else {
                    textPaint.color = Color.RED
                    canvas.drawText("Missing: /sdcard/OpenKustom/logic.lua", 50f, 300f, textPaint)
                }

            } catch (e: Exception) {
                textPaint.color = Color.YELLOW
                textPaint.textSize = 35f
                canvas.drawText("LUA ERR: ${e.message}", 50f, 400f, textPaint)
            } finally {
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
        }

        override fun onVisibilityChanged(visible: Boolean) { if (visible) drawFrame() }
    }
}
