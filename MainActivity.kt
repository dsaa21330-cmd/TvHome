package com.example.tvhome

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import java.io.File
import java.util.Calendar

class MainActivity : Activity() {

    // الحزم الافتراضية للدوك. غيّرها من الجهاز بضغطة مطولة على أي أيقونة.
    private val defaults = listOf(
        "com.google.android.youtube.tv",
        "com.netflix.ninja",
        "com.amazon.amazonvideo.livingroom",
        "in.startv.hotstar",
        ""
    )
    private val slideshowMs = 5 * 60 * 1000L   // تغيير الخلفية كل 5 دقائق
    private val fadeMs = 1500L

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("dock", MODE_PRIVATE) }
    private lateinit var bgA: ImageView
    private lateinit var bgB: ImageView
    private var front: ImageView? = null
    private lateinit var greeting: TextView
    private lateinit var appLabel: TextView
    private lateinit var dock: LinearLayout
    private var files: List<File> = emptyList()
    private var idx = 0

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, r: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF1B2A34.toInt(), 0xFF3E5C63.toInt())
        )

        bgA = bg(); bgB = bg(); bgB.alpha = 0f
        front = bgA
        root.addView(bgA); root.addView(bgB)

        // تعتيم خفيف للقراءة
        val scrim = View(this)
        scrim.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0x55000000, 0x00000000, 0x00000000, 0xCC000000.toInt())
        )
        root.addView(scrim, ViewGroup.LayoutParams(-1, -1))

        // الزاوية العلوية اليسرى: التحية والساعة والتاريخ
        val topLeft = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        greeting = TextView(this).apply {
            setTextColor(0xCCFFFFFF.toInt()); textSize = 11f; letterSpacing = 0.25f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val clock = TextClock(this).apply {
            format12Hour = "H:mm"; format24Hour = "H:mm"
            setTextColor(Color.WHITE); textSize = 54f
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            includeFontPadding = false
        }
        val date = TextClock(this).apply {
            format12Hour = "EEEE  dd MMMM"; format24Hour = "EEEE  dd MMMM"
            setTextColor(0xCCFFFFFF.toInt()); textSize = 11f; letterSpacing = 0.2f
            isAllCaps = true
        }
        topLeft.addView(greeting); topLeft.addView(clock); topLeft.addView(date)
        root.addView(topLeft, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(56), dp(40), 0, 0)
        })

        // الزاوية العلوية اليمنى: Inputs و Settings
        val topRight = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        topRight.addView(pill("Inputs") { openInputs() })
        topRight.addView(pill("Settings") { startActivity(Intent(Settings.ACTION_SETTINGS)) })
        root.addView(topRight, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp(40), dp(56), 0)
        })

        // أسفل: اسم التطبيق + الدوك
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        appLabel = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 24f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(dp(12), 0, 0, dp(10))
        }
        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(0x33FFFFFF, 26)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        bottom.addView(appLabel); bottom.addView(dock)
        root.addView(bottom, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(40)
        })

        setContentView(root)
    }

    private fun bg() = ImageView(this).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        layoutParams = ViewGroup.LayoutParams(-1, -1)
    }

    private fun pill(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text; setTextColor(Color.WHITE); textSize = 13f
        setPadding(dp(18), dp(9), dp(18), dp(9))
        background = rounded(0x33FFFFFF, 20)
        isFocusable = true; isClickable = true
        setOnClickListener { onClick() }
        setOnFocusChangeListener { v, f ->
            v.background = rounded(if (f) 0x99FFFFFF.toInt() else 0x33FFFFFF, 20)
            v.animate().scaleX(if (f) 1.08f else 1f).scaleY(if (f) 1.08f else 1f).setDuration(120).start()
        }
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10) }
    }

    private fun openInputs() {
        val tries = listOf(
            Intent().setComponent(ComponentName("com.google.android.tvlauncher", "com.google.android.tvlauncher.inputs.InputsPanelActivity")),
            Intent("android.settings.INPUT_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (i in tries) {
            try { startActivity(i); return } catch (_: Exception) { }
        }
    }

    override fun onResume() {
        super.onResume()
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        greeting.text = when { h < 12 -> "GOOD MORNING"; h < 18 -> "GOOD AFTERNOON"; else -> "GOOD EVENING" }
        buildDock()
        loadWallpapers()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
    }

    // ---------- الدوك ----------
    private fun launchIntentFor(pkg: String): Intent? {
        val pm = packageManager
        return pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
    }

    private fun buildDock() {
        dock.removeAllViews()
        val pm = packageManager
        for (i in 0 until 5) {
            val pkg = prefs.getString("slot$i", defaults[i]) ?: defaults[i]
            val intent = launchIntentFor(pkg)
            val tile = FrameLayout(this)
            tile.layoutParams = LinearLayout.LayoutParams(dp(170), dp(96)).apply { marginStart = if (i == 0) 0 else dp(10) }
            tile.background = rounded(0x44FFFFFF, 14)
            tile.clipToOutline = true
            tile.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(14).toFloat()) }
            }
            var name = "Add app"
            if (intent != null) {
                val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    name = pm.getApplicationLabel(info).toString()
                    iv.setImageDrawable(pm.getApplicationBanner(pkg) ?: pm.getApplicationIcon(pkg))
                } catch (_: Exception) { }
                tile.addView(iv, FrameLayout.LayoutParams(-1, -1))
            } else {
                tile.addView(TextView(this).apply {
                    text = "+"; textSize = 32f; setTextColor(0xAAFFFFFF.toInt()); gravity = Gravity.CENTER
                }, FrameLayout.LayoutParams(-1, -1))
            }
            tile.isFocusable = true; tile.isClickable = true
            tile.setOnClickListener { if (intent != null) startActivity(intent) else pickApp(i) }
            tile.setOnLongClickListener { pickApp(i); true }
            val label = name
            tile.setOnFocusChangeListener { v, f ->
                v.animate().scaleX(if (f) 1.1f else 1f).scaleY(if (f) 1.1f else 1f).setDuration(120).start()
                v.foreground = if (f) GradientDrawable().apply {
                    setStroke(dp(3), Color.WHITE); cornerRadius = dp(14).toFloat(); setColor(Color.TRANSPARENT)
                } else null
                v.elevation = if (f) 12f else 0f
                if (f) appLabel.text = label
            }
            dock.addView(tile)
        }
        dock.getChildAt(0).requestFocus()
    }

    private fun pickApp(slot: Int) {
        val pm = packageManager
        val list = mutableListOf<Pair<String, String>>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(q, 0)) {
                val p = ri.activityInfo.packageName
                if (p != packageName && list.none { it.second == p }) list.add(ri.loadLabel(pm).toString() to p)
            }
        }
        list.sortBy { it.first.lowercase() }
        AlertDialog.Builder(this)
            .setTitle("Choose app")
            .setItems(list.map { it.first }.toTypedArray()) { _, w ->
                prefs.edit().putString("slot$slot", list[w].second).apply()
                buildDock()
            }.show()
    }

    // ---------- الخلفيات ----------
    private fun loadWallpapers() {
        val dir = getExternalFilesDir("wallpapers")
        files = dir?.listFiles { f -> f.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp") }
            ?.sortedBy { it.name } ?: emptyList()
        if (files.isNotEmpty()) showNext()
    }

    private fun showNext() {
        if (files.isEmpty()) return
        val f = files[idx++ % files.size]
        Thread {
            val bmp = decode(f)
            runOnUiThread {
                if (bmp != null) crossfade(bmp)
                handler.postDelayed({ showNext() }, slideshowMs)
            }
        }.start()
    }

    private fun decode(f: File): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= 1920) s *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    } catch (_: Throwable) { null }

    private fun crossfade(bmp: Bitmap) {
        val out = front ?: bgA
        val inc = if (out === bgA) bgB else bgA
        inc.setImageBitmap(bmp)
        inc.animate().alpha(1f).setDuration(fadeMs).start()
        out.animate().alpha(0f).setDuration(fadeMs).start()
        front = inc
    }

    override fun onBackPressed() { /* اللانشر لا يُغلق */ }
}
