package com.openkustom.lwp

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import java.io.File

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F111A"))
            gravity = Gravity.CENTER
            setPadding(60, 60, 60, 60)
        }

        val title = TextView(this).apply {
            text = "OPEN KUSTOM EDITOR"
            textSize = 28f
            setTextColor(Color.parseColor("#00E5FF"))
            setPadding(0, 0, 0, 50)
        }

        // INPUT FIELD FOR TEXT COMPONENTS
        val inputField = EditText(this).apply {
            hint = "Enter text for wallpaper"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1A1C2E"))
        }

        val btnAddText = Button(this).apply {
            text = "INJECT TEXT COMPONENT"
            setOnClickListener {
                val content = inputField.text.toString()
                if (content.isNotEmpty()) {
                    addComponent("text", "{ type=\"text\", content=\"$content\", x=100, y=500, size=60, color=\"#FFFFFF\" }")
                    inputField.text.clear()
                }
            }
        }

        val btnSetWallpaper = Button(this).apply {
            text = "SET LIVE WALLPAPER"
            setPadding(0, 50, 0, 0)
            setOnClickListener {
                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, 
                        ComponentName(this@MainActivity, LuaWallpaperService::class.java))
                }
                startActivity(intent)
            }
        }

        root.addView(title)
        root.addView(inputField)
        root.addView(btnAddText)
        root.addView(btnSetWallpaper)
        setContentView(root)
    }

    private fun addComponent(type: String, luaCode: String) {
        val file = File("/sdcard/OpenKustom/logic.lua")
        if (!file.exists()) {
            file.writeText("local ui = {}\nreturn ui")
        }
        
        val current = file.readText()
        if (current.contains("local ui = {")) {
            val updated = current.replace("local ui = {", "local ui = {\n    $luaCode,")
            file.writeText(updated)
            Toast.makeText(this, "Added $type to wallpaper!", Toast.LENGTH_SHORT).show()
        }
    }
}
