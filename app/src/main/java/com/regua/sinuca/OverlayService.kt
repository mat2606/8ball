package com.regua.sinuca

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

/**
 * As linhas ficam numa janela que nunca recebe toque. Cada círculo é uma janelinha própria.
 * Todas as janelas usam alpha < 0.8: o Android 12+ bloqueia toques que atravessam janelas mais opacas.
 */
class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var ruler: RulerView? = null
    private val handles = ArrayList<Pair<View, WindowManager.LayoutParams>>()
    private var modePill: TextView? = null
    private var eyePill: TextView? = null
    private var hidden = false
    private val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    private val base = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    private val noTouch = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

    override fun onBind(i: Intent?): IBinder? = null

    private fun newParams(w: Int, h: Int, flags: Int) = WindowManager.LayoutParams(w, h, type, flags, PixelFormat.TRANSLUCENT).apply {
        alpha = 0.79f
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        else if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (ruler != null) return START_STICKY
        startFg()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val d = resources.displayMetrics.density
        val rv = RulerView(this); ruler = rv
        rv.onReady = {
            val cols = intArrayOf(0xFFFFA000.toInt(), 0xFFFFA000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFEB3B.toInt())
            for (k in 0..3) addHandle(rv, k, cols[k])
            modePill = makePill((16 * d).toInt(), (110 * d).toInt()) { rv.tabela = !rv.tabela; applyState() }
            eyePill = makePill((16 * d).toInt(), (166 * d).toInt()) { hidden = !hidden; applyState() }
            applyState()
        }
        wm.addView(rv, newParams(-1, -1, base or noTouch))
        return START_STICKY
    }

    private fun addHandle(rv: RulerView, idx: Int, color: Int) {
        val d = resources.displayMetrics.density
        val size = (48 * d).toInt(); val half = size / 2f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFF000000.toInt(); style = Paint.Style.STROKE; strokeWidth = 2 * d }
        val hv = object : View(this) {
            override fun onDraw(c: Canvas) {
                c.drawCircle(width / 2f, height / 2f, 13 * d, fill)
                c.drawCircle(width / 2f, height / 2f, 13 * d, ring)
            }
        }
        val lp = newParams(size, size, base)
        var offX = 0; var offY = 0 // diferença entre lp.x/y e a posição real na tela
        fun place() {
            lp.x = (rv.pts[idx].x - half).toInt() - offX; lp.y = (rv.pts[idx].y - half).toInt() - offY
            wm.updateViewLayout(hv, lp)
        }
        lp.x = (rv.pts[idx].x - half).toInt(); lp.y = (rv.pts[idx].y - half).toInt()
        hv.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                hv.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val l = IntArray(2); hv.getLocationOnScreen(l)
                offX = l[0] - lp.x; offY = l[1] - lp.y
                if (offX != 0 || offY != 0) place()
            }
        })
        var gx = 0f; var gy = 0f
        hv.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { gx = e.rawX - rv.pts[idx].x; gy = e.rawY - rv.pts[idx].y }
                MotionEvent.ACTION_MOVE -> { rv.pts[idx].set(e.rawX - gx, e.rawY - gy); place(); rv.invalidate() }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> rv.save()
            }
            true
        }
        wm.addView(hv, lp)
        handles.add(hv to lp)
    }

    /** Botão arrastável em formato de pílula; toque simples chama onTap. */
    private fun makePill(x: Int, y: Int, onTap: () -> Unit): TextView {
        val d = resources.displayMetrics.density
        val tv = TextView(this).apply {
            textSize = 13f; gravity = Gravity.CENTER; setTextColor(0xFFFFFFFF.toInt())
            setPadding((14 * d).toInt(), 0, (14 * d).toInt(), 0)
            background = GradientDrawable().apply { cornerRadius = 22 * d; setColor(0xFF202020.toInt()) }
        }
        val lp = newParams(-2, (44 * d).toInt(), base); lp.x = x; lp.y = y
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        tv.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(e.rawX - sx) > 10 * d || abs(e.rawY - sy) > 10 * d) moved = true
                    if (moved) { lp.x = ox + (e.rawX - sx).toInt(); lp.y = oy + (e.rawY - sy).toInt(); wm.updateViewLayout(tv, lp) }
                }
                MotionEvent.ACTION_UP -> if (!moved) onTap()
            }
            true
        }
        wm.addView(tv, lp)
        return tv
    }

    private fun applyState() {
        val rv = ruler ?: return
        rv.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        for ((v, lp) in handles) {
            v.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
            lp.flags = if (hidden) base or noTouch else base
            wm.updateViewLayout(v, lp)
        }
        modePill?.text = if (rv.tabela) "Modo: Tabela" else "Modo: Comum"
        modePill?.visibility = if (hidden) View.GONE else View.VISIBLE
        eyePill?.text = if (hidden) "Mostrar" else "Ocultar"
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("ruler", "Régua", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "ruler").setContentTitle("Régua ativa")
            .setContentText("Toque para abrir e parar").setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(pi).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    override fun onDestroy() {
        ruler?.let { wm.removeView(it) }
        for ((v, _) in handles) wm.removeView(v)
        modePill?.let { wm.removeView(it) }; eyePill?.let { wm.removeView(it) }
        handles.clear(); ruler = null; modePill = null; eyePill = null
        super.onDestroy()
    }
}
