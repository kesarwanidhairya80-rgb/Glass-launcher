package com.glass.launcher

import android.animation.*
import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.*
import android.content.pm.ResolveInfo
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.hardware.*
import android.net.Uri
import android.os.*
import android.view.*
import android.widget.*
import kotlin.math.sqrt

class FX(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    var tx = 0f
    val ripples = ArrayList<FloatArray>()
    init {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { ripples.forEach { it[2] += 0.02f }; ripples.removeAll { it[2] > 1f }; invalidate() }
            start()
        }
    }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, w, h, intArrayOf(0xFF1B1464.toInt(), 0xFF0F7B8A.toInt(), 0xFFB0307A.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        val bx = w / 2 + tx * w * 0.9f
        p.shader = LinearGradient(bx - w * 0.3f, 0f, bx + w * 0.3f, h * 0.6f, intArrayOf(0x00FFFFFF, 0x55FFFFFF, 0x00FFFFFF), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = 6f
        for (r in ripples) {
            p.color = Color.argb(((1f - r[2]) * 140).toInt().coerceIn(0, 255), 255, 255, 255)
            c.drawCircle(r[0], r[1], r[2] * w * 0.6f, p)
        }
    }
}

class MainActivity : Activity(), SensorEventListener {
    private lateinit var root: FrameLayout
    private lateinit var layer: FrameLayout
    private lateinit var fx: FX
    private lateinit var drawer: LinearLayout
    private lateinit var grid: GridView
    private lateinit var island: TextView
    private lateinit var note: EditText
    private lateinit var pet: TextView
    private lateinit var prefs: SharedPreferences
    private var apps = listOf<ResolveInfo>()
    private val dropped = ArrayList<Uri>()
    private val idle = "⬇ Origin Island: drop files here"
    private var query = ""
    private var privOpen = false
    private var lastShake = 0L
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.setDecorFitsSystemWindows(false)
        prefs = getSharedPreferences("glass", MODE_PRIVATE)
        apps = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        root = FrameLayout(this); layer = FrameLayout(this)
        setContentView(root)
        root.addView(layer, -1, -1)
        buildHome(); buildDrawer(); refresh()
    }

    private fun glass(r: Int) = GradientDrawable().apply { cornerRadius = dp(r).toFloat(); setColor(0x33FFFFFF); setStroke(dp(1), 0x55FFFFFF) }
    private fun isPriv(pkg: String) = prefs.getStringSet("private", emptySet())!!.contains(pkg)

    private fun tile(ri: ResolveInfo, small: Boolean = false): View {
        val pkg = ri.activityInfo.packageName
        val (w, h, r) = when (prefs.getInt("shape_$pkg", 0)) { 1 -> Triple(58, 58, 29); 2 -> Triple(58, 76, 22); 3 -> Triple(82, 58, 22); else -> Triple(58, 58, 18) }
        val box = FrameLayout(this).apply {
            background = GradientDrawable().apply { cornerRadius = dp(r).toFloat(); setColor(0xCC1A1D2E.toInt()); setStroke(dp(1), 0x33FFFFFF) }
        }
        val iv = ImageView(this).apply {
            setImageDrawable(ri.loadIcon(packageManager))
            setColorFilter(ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0.35f) }))
        }
        box.addView(iv, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER))
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            addView(box, LinearLayout.LayoutParams(dp(w), dp(h)))
            if (!small) addView(TextView(context).apply { text = ri.loadLabel(packageManager); setTextColor(Color.WHITE); textSize = 11f; maxLines = 1; gravity = Gravity.CENTER })
            setOnClickListener { packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) } }
            setOnLongClickListener { menu(ri); true }
        }
    }

    private fun menu(ri: ResolveInfo) {
        val pkg = ri.activityInfo.packageName
        val priv = isPriv(pkg)
        AlertDialog.Builder(this).setTitle(ri.loadLabel(packageManager))
            .setItems(arrayOf("Change icon shape", if (priv) "Remove from Private Space" else "Move to Private Space")) { _, which ->
                if (which == 0) prefs.edit().putInt("shape_$pkg", (prefs.getInt("shape_$pkg", 0) + 1) % 4).apply()
                else {
                    val s = HashSet(prefs.getStringSet("private", emptySet())!!)
                    if (priv) s.remove(pkg) else s.add(pkg)
                    prefs.edit().putStringSet("private", s).apply()
                }
                refresh()
            }.show()
    }

    private fun petFace(): String {
        val charging = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let {
            val s = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            s == BatteryManager.BATTERY_STATUS_CHARGING || s == BatteryManager.BATTERY_STATUS_FULL
        } ?: false
        val hr = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return if (charging) "😻" else if (hr >= 23 || hr < 6) "😴" else "🐱"
    }

    private fun buildHome() {
        fx = FX(this)
        layer.addView(fx, -1, -1)
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(a: MotionEvent?, b: MotionEvent, vx: Float, vy: Float): Boolean {
                if (vy < -1200 && drawer.visibility != View.VISIBLE) toggleDrawer(true)
                return true
            }
        })
        fx.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) fx.ripples.add(floatArrayOf(e.x, e.y, 0f))
            gd.onTouchEvent(e); true
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(110), dp(22), dp(130)) }
        val clock = TextClock(this).apply {
            format12Hour = "h:mm"; format24Hour = "HH:mm"; textSize = 76f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); pivotY = 0f
        }
        var preset = 0
        clock.setOnLongClickListener {
            preset = (preset + 1) % 3
            clock.scaleY = floatArrayOf(1f, 1.4f, 1.8f)[preset]
            clock.paint.style = Paint.Style.FILL_AND_STROKE
            clock.paint.strokeWidth = floatArrayOf(0f, 2f, 5f)[preset]
            clock.invalidate(); true
        }
        val date = TextClock(this).apply { format12Hour = "EEEE, d MMMM"; format24Hour = "EEEE, d MMMM"; setTextColor(Color.WHITE); textSize = 15f }
        note = EditText(this).apply {
            hint = "Snap note…"; setHintTextColor(0x99FFFFFF.toInt()); setTextColor(Color.WHITE); textSize = 16f
            background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(0x55FFD54A); setStroke(dp(1), 0x88FFD54A.toInt()) }
            setPadding(dp(16), dp(14), dp(16), dp(14)); rotation = -2f; minLines = 2
            setText(prefs.getString("note", ""))
        }
        pet = TextView(this).apply {
            text = petFace(); textSize = 54f; gravity = Gravity.CENTER
            setOnClickListener { v ->
                v.animate().rotationBy(360f).setDuration(500).start()
                text = "😺"; postDelayed({ text = petFace() }, 1200)
            }
        }
        ObjectAnimator.ofFloat(pet, "translationY", 0f, -dp(10).toFloat()).apply {
            duration = 700; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; start()
        }
        col.addView(clock, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(40) })
        col.addView(date)
        col.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        col.addView(note, -1, -2)
        col.addView(pet, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL })
        layer.addView(col, -1, -1)

        val dock = LinearLayout(this).apply { background = glass(34); gravity = Gravity.CENTER; setPadding(dp(12), dp(14), dp(12), dp(14)) }
        apps.take(3).forEach { dock.addView(tile(it, true), LinearLayout.LayoutParams(0, -2, 1f)) }
        dock.addView(TextView(this).apply { text = "⌃"; textSize = 30f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setOnClickListener { toggleDrawer(true) } }, LinearLayout.LayoutParams(0, -2, 1f))
        layer.addView(dock, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(dp(14), 0, dp(14), dp(34)) })

        island = TextView(this).apply {
            text = idle; setTextColor(Color.WHITE); textSize = 13f; gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(0xDD000000.toInt()) }
            setPadding(dp(18), dp(10), dp(18), dp(10))
        }
        island.setOnDragListener { v, e ->
            when (e.action) {
                DragEvent.ACTION_DRAG_ENTERED -> { v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(150).start(); true }
                DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> { v.animate().scaleX(1f).scaleY(1f).setDuration(150).start(); true }
                DragEvent.ACTION_DROP -> {
                    try { requestDragAndDropPermissions(e) } catch (ex: Exception) {}
                    e.clipData?.let { cd -> for (i in 0 until cd.itemCount) cd.getItemAt(i).uri?.let { dropped.add(it) } }
                    island.text = "📎 ${dropped.size} item(s) · tap to share"
                    v.animate().scaleX(1f).scaleY(1f).setDuration(150).start(); true
                }
                else -> true
            }
        }
        island.setOnClickListener {
            if (dropped.isNotEmpty()) startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(dropped)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share"))
        }
        island.setOnLongClickListener { dropped.clear(); island.text = idle; true }
        layer.addView(island, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(40) })
    }

    private fun buildDrawer() {
        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(70), dp(18), 0)
            setBackgroundColor(0xAA0A0C28.toInt()); visibility = View.GONE
        }
        val search = EditText(this).apply {
            hint = "Search apps"; setHintTextColor(Color.LTGRAY); setTextColor(Color.WHITE)
            background = glass(24); setPadding(dp(18), dp(12), dp(18), dp(12)); setSingleLine()
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) { query = s.toString(); refresh() }
                override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
            })
        }
        val lock = TextView(this).apply {
            text = "🔒"; textSize = 24f; setPadding(dp(14), 0, dp(4), 0); gravity = Gravity.CENTER
            setOnClickListener {
                val km = getSystemService(KeyguardManager::class.java)
                if (km.isDeviceSecure) km.createConfirmDeviceCredentialIntent("Private Space", "Verify it's you")?.let { startActivityForResult(it, 7) }
                else Toast.makeText(context, "Set a screen lock first", Toast.LENGTH_SHORT).show()
            }
        }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        bar.addView(search, LinearLayout.LayoutParams(0, -2, 1f)); bar.addView(lock)
        grid = GridView(this).apply { numColumns = 4; verticalSpacing = dp(18); setPadding(0, dp(20), 0, dp(20)) }
        drawer.addView(bar, -1, -2); drawer.addView(grid, -1, -1)
        root.addView(drawer, -1, -1)
    }

    private fun refresh() {
        val list = apps.filter { (privOpen || !isPriv(it.activityInfo.packageName)) && it.loadLabel(packageManager).toString().contains(query, true) }
        grid.adapter = object : BaseAdapter() {
            override fun getCount() = list.size
            override fun getItem(p: Int) = list[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, v: View?, g: ViewGroup?) = tile(list[p])
        }
    }

    private fun toggleDrawer(open: Boolean) {
        val ease = android.view.animation.PathInterpolator(0.2f, 0.9f, 0.25f, 1f)
        if (open) {
            drawer.visibility = View.VISIBLE; drawer.translationY = root.height.toFloat()
            drawer.animate().translationY(0f).setDuration(450).setInterpolator(ease).start()
            layer.setRenderEffect(RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.CLAMP))
        } else {
            privOpen = false; refresh()
            drawer.animate().translationY(root.height.toFloat()).setDuration(350).setInterpolator(ease).withEndAction { drawer.visibility = View.GONE }.start()
            layer.setRenderEffect(null)
        }
    }

    override fun onActivityResult(r: Int, res: Int, d: Intent?) {
        super.onActivityResult(r, res, d)
        if (r == 7 && res == RESULT_OK) { privOpen = true; refresh() }
    }

    override fun onSensorChanged(e: SensorEvent) {
        val x = e.values[0]; val y = e.values[1]; val z = e.values[2]
        fx.tx += ((-x / 9.8f).coerceIn(-1f, 1f) - fx.tx) * 0.1f
        val now = SystemClock.elapsedRealtime()
        if (sqrt(x * x + y * y + z * z) > 20f && now - lastShake > 800) {
            lastShake = now
            repeat(3) { fx.ripples.add(floatArrayOf(fx.width * (0.2f + 0.3f * it), fx.height * 0.5f, 0f)) }
        }
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    override fun onResume() {
        super.onResume()
        val sm = getSystemService(SensorManager::class.java)
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        pet.text = petFace()
    }
    override fun onPause() {
        super.onPause()
        getSystemService(SensorManager::class.java).unregisterListener(this)
        prefs.edit().putString("note", note.text.toString()).apply()
    }
    override fun onNewIntent(i: Intent?) { super.onNewIntent(i); if (drawer.visibility == View.VISIBLE) toggleDrawer(false) }
    override fun onBackPressed() { if (drawer.visibility == View.VISIBLE) toggleDrawer(false) }
}
