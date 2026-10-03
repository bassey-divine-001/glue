package com.macro.gloowall

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var overlayStatus: TextView
    private lateinit var accessibilityStatus: TextView
    private lateinit var overlayButton: Button
    private lateinit var accessibilityButton: Button
    private lateinit var powerButton: TextView
    private lateinit var targetSizeSeekBar: SeekBar
    private var permissionsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    private fun buildScreen() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(30), dp(24), dp(28))
            setBackgroundColor(Color.rgb(16, 24, 25))
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(page)
        }

        val title = TextView(this).apply {
            text = "Gloo Wall Macro"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        page.addView(title)

        val subtitle = TextView(this).apply {
            text = "Grant both permissions to enable the floating target."
            textSize = 15f
            setTextColor(Color.rgb(178, 194, 190))
            setPadding(0, dp(8), 0, dp(22))
        }
        page.addView(subtitle)

        overlayStatus = TextView(this)
        overlayButton = Button(this).apply {
            text = "Allow display over other apps"
            isAllCaps = false
            setOnClickListener {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
        page.addView(permissionCard("Display over other apps", overlayStatus, overlayButton))

        accessibilityStatus = TextView(this)
        accessibilityButton = Button(this).apply {
            text = "Enable accessibility service"
            isAllCaps = false
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        page.addView(permissionCard("Accessibility service", accessibilityStatus, accessibilityButton))

        val targetSizeLabel = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(0, dp(8), 0, 0)
        }
        targetSizeSeekBar = SeekBar(this).apply {
            max = MacroAccessibilityService.MAX_TARGET_SIZE_DP - MacroAccessibilityService.MIN_TARGET_SIZE_DP
            progress = MacroAccessibilityService.getTargetSizeDp(this@MainActivity) -
                MacroAccessibilityService.MIN_TARGET_SIZE_DP
            contentDescription = "Floating target size"
        }
        targetSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val sizeDp = MacroAccessibilityService.MIN_TARGET_SIZE_DP + progress
                targetSizeLabel.text = "Floating target size: $sizeDp dp"
                if (fromUser) MacroAccessibilityService.setTargetSizeDp(this@MainActivity, sizeDp)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        targetSizeLabel.text = "Floating target size: ${MacroAccessibilityService.getTargetSizeDp(this)} dp"
        val targetSizeSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            addView(targetSizeLabel)
            addView(targetSizeSeekBar)
        }
        page.addView(targetSizeSection)

        val powerLabel = TextView(this).apply {
            text = "MACRO POWER"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(178, 194, 190))
            setPadding(0, dp(22), 0, dp(12))
        }
        page.addView(powerLabel)

        powerButton = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 22f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setOnClickListener {
                if (!permissionsReady) return@setOnClickListener
                val shouldEnable = !MacroAccessibilityService.isMacroEnabled(this@MainActivity)
                MacroAccessibilityService.setMacroEnabled(this@MainActivity, shouldEnable)
                updatePowerButton(shouldEnable)
            }
        }
        val powerHolder = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(powerButton, LinearLayout.LayoutParams(dp(184), dp(184)))
        }
        page.addView(powerHolder)

        setContentView(scroll)
        refreshPermissionState()
    }

    private fun permissionCard(title: String, status: TextView, button: Button): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(13), dp(16), dp(10))
            background = roundedBackground(Color.rgb(28, 40, 40), dp(10))
        }
        val heading = TextView(this).apply {
            text = title
            textSize = 17f
            setTextColor(Color.WHITE)
        }
        status.apply {
            textSize = 14f
            setPadding(0, dp(6), 0, 0)
        }
        card.addView(heading)
        card.addView(status)
        card.addView(button)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(12)
        }
        card.layoutParams = params
        return card
    }

    private fun refreshPermissionState() {
        if (!::overlayStatus.isInitialized) return
        val overlayGranted = Settings.canDrawOverlays(this)
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        permissionsReady = overlayGranted && accessibilityEnabled
        if (!permissionsReady && MacroAccessibilityService.isMacroEnabled(this)) {
            MacroAccessibilityService.setMacroEnabled(this, false)
        }

        setPermissionIndicator(overlayStatus, overlayGranted)
        setPermissionIndicator(accessibilityStatus, accessibilityEnabled)
        overlayButton.visibility = if (overlayGranted) View.GONE else View.VISIBLE
        accessibilityButton.visibility = if (accessibilityEnabled) View.GONE else View.VISIBLE
        updatePowerButton(MacroAccessibilityService.isMacroEnabled(this))
    }

    private fun setPermissionIndicator(view: TextView, granted: Boolean) {
        view.text = if (granted) "Enabled" else "Not enabled"
        view.setTextColor(if (granted) Color.rgb(105, 224, 148) else Color.rgb(255, 156, 139))
    }

    private fun updatePowerButton(enabled: Boolean) {
        if (!::powerButton.isInitialized) return
        powerButton.isEnabled = permissionsReady
        powerButton.alpha = if (permissionsReady) 1f else 0.38f
        powerButton.text = if (enabled) "ON" else "OFF"
        powerButton.background = roundedBackground(
            if (enabled) Color.rgb(24, 145, 82) else Color.rgb(181, 61, 55),
            dp(100),
            oval = true
        )
        powerButton.contentDescription = if (enabled) "Turn macro off" else "Turn macro on"
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val expected = ComponentName(this, MacroAccessibilityService::class.java)
        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabledServices) }
        return splitter.any { entry ->
            ComponentName.unflattenFromString(entry)?.equals(expected) == true
        }
    }

    private fun roundedBackground(color: Int, radius: Int, oval: Boolean = false) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = if (oval) 0f else radius.toFloat()
            shape = if (oval) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
