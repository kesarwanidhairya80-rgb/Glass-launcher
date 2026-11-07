package com.glass.launcher

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.animation.PathInterpolator
import android.widget.*

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var drawer: LinearLayout
    private lateinit var grid: GridView
    private var apps = listOf<ResolveInfo>()
    private val ease = PathInterpolator(0.2f, 0.9f, 0.25f, 1f)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.setDecorFitsSystemWindows(false)
        loadApps()
        root = FrameLayout(this)
        setContentView(root)
        buildHome()
        buildDrawer()
        setupSwipe()
    }

    private fun loadApps() {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = packageManager.queryIntentActivities(i, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
    }

    private fun glass(radius: Int) = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat()
        setColor(Color.argb(60, 255, 255, 255))
        setStroke(dp(1), Color.argb(90, 255, 255, 255))
    }

    private fun buildHome() {
        val home = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(80), dp(20), dp(40)) }
        val clock = TextClock(this).apply {
            format12Hour = "h:mm"; format24Hour = "HH:mm"
            textSize = 72f; setTextColor(Color.WHITE); typeface = android.graphics.Typeface.create("sans-serif-thin", android.graphics.Typeface.NORMAL)
        }
        val date = TextClock(this).apply { format12Hour = "EEEE, d MMMM"; format24Hour = "EEEE, d MMMM"; setTextColor(Color.WHITE); textSize = 15f }
        home.addView(clock); home.addView(date)
        val dock = LinearLayout(this).apply { background = glass(34); setPadding(dp(12), dp(14), dp(12), dp(14)); gravity = Gravity.CENTER }
        apps.take(4).forEach { dock.addView(icon(it), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
        val lp = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(dp(14), 0, dp(14), dp(34)) }
        root.addView(home); root.addView(dock, lp)
    }

    private fun icon(ri: ResolveInfo): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        addView(ImageView(context).apply { setImageDrawable(ri.loadIcon(packageManager)) }, LinearLayout.LayoutParams(dp(56), dp(56)))
        addView(TextView(context).apply { text = ri.loadLabel(packageManager); setTextColor(Color.WHITE); textSize = 11f; maxLines = 1; gravity = Gravity.CENTER })
        setOnClickListener { v ->
            v.animate().scaleX(.85f).scaleY(.85f).setDuration(90).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(ease).start()
                startActivity(packageManager.getLaunchIntentForPackage(ri.activityInfo.packageName))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }.start()
        }
    }

    private fun buildDrawer() {
        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(70), dp(18), 0)
            setBackgroundColor(Color.argb(150, 10, 12, 40)); visibility = View.GONE
        }
        val search = EditText(this).apply {
            hint = "Search apps"; setHintTextColor(Color.LTGRAY); setTextColor(Color.WHITE)
            background = glass(24); setPadding(dp(18), dp(12), dp(18), dp(12)); setSingleLine()
        }
        grid = GridView(this).apply { numColumns = 4; verticalSpacing = dp(18); setPadding(0, dp(20), 0, dp(20)) }
        fun show(list: List<ResolveInfo>) {
            grid.adapter = object : BaseAdapter() {
                override fun getCount() = list.size
                override fun getItem(p: Int) = list[p]
                override fun getItemId(p: Int) = p.toLong()
                override fun getView(p: Int, v: View?, g: ViewGroup?) = icon(list[p])
            }
        }
        show(apps)
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { show(apps.filter { it.loadLabel(packageManager).toString().contains(s.toString(), true) }) }
            override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
            override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
        })
        drawer.addView(search, -1, -2); drawer.addView(grid, -1, -1)
        root.addView(drawer, -1, -1)
    }

    private fun toggleDrawer(open: Boolean) {
        if (open) {
            drawer.visibility = View.VISIBLE; drawer.translationY = root.height.toFloat()
            drawer.animate().translationY(0f).setDuration(450).setInterpolator(ease).start()
            root.getChildAt(0).setRenderEffect(RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.CLAMP))
        } else {
            drawer.animate().translationY(root.height.toFloat()).setDuration(350).setInterpolator(ease)
                .withEndAction { drawer.visibility = View.GONE }.start()
            root.getChildAt(0).setRenderEffect(null)
        }
    }

    private fun setupSwipe() {
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(a: MotionEvent?, b: MotionEvent, vx: Float, vy: Float): Boolean {
                if (vy < -1200 && drawer.visibility != View.VISIBLE) toggleDrawer(true)
                else if (vy > 1200 && drawer.visibility == View.VISIBLE) toggleDrawer(false)
                return true
            }
        })
        root.setOnTouchListener { _, e -> gd.onTouchEvent(e) }
    }

    override fun onBackPressed() { if (drawer.visibility == View.VISIBLE) toggleDrawer(false) }
}
