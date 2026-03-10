package com.openkustom.lwp

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Create a Dark Cyberpunk Layout
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F111A"))
            gravity = Gravity.CENTER
            setPadding(50, 50, 50, 50)
        }

        // 2. Title Header
        val title = TextView(this).apply {
            text = "OPEN KUSTOM"
            textSize = 32f
            setTextColor(Color.parseColor("#00E5FF"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        // 3. Script Status Label
        val scriptFile = File("/sdcard/OpenKustom/logic.lua")
        val status = TextView(this).apply {
            text = if (scriptFile.exists()) "STATUS: SCRIPT DETECTED" else "STATUS: MISSING SCRIPT"
            setTextColor(if (scriptFile.exists()) Color.GREEN else Color.RED)
            setPadding(0, 40, 0, 80)
        }

        // 4. "Set Wallpaper" Button
        val btnSet = Button(this).apply {
            text = "OPEN WALLPAPER PICKER"
            setBackgroundColor(Color.parseColor("#1A1C2E"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, 
                        ComponentName(this@MainActivity, LuaWallpaperService::class.java))
                }
                startActivity(intent)
            }
        }

        // 5. Build the UI
        root.addView(title)
        root.addView(status)
        root.addView(btnSet)
        setContentView(root)
    }
}
