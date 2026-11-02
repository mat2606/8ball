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
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

/**
 * A régua (linhas) ocupa a tela toda mas NUNCA recebe toque, então o jogo responde normalmente.
 * Cada ponto é uma janelinha própria (alça) que só captura toque em cima dela.
 */
class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var ruler: RulerView? = null
    private val handles = ArrayList<Pair<View, WindowManager.LayoutParams>>()
    private var modePill: TextView? = null
    private var eyePill: TextView? = null
    private var hidden = false
    private lateinit var rp: WindowManager.LayoutParams
    private val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    private val base = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    private val noTouch = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (ruler != null) return START_STICKY
        startFg()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val d = resources.displayMetrics.density

        val rv = RulerView(this); ruler = rv
        rp = WindowManager.LayoutParams(-1, -1, type, base or noTouch, PixelFormat.TRANSLUCENT)
        rv.onReady = {
            val cols = intArrayOf(0xFFFFA000.toInt(), 0xFFFFA000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFEB3B.toInt())
            for (k in 0..3) addHandle(rv, k, cols[k])
            addPills(d)
            applyState()
        }
        wm.addView(rv, rp)
        return START_STICKY
    }

    private fun addHandle(rv: RulerView, idx: Int, color: Int) {
        val d = resources.displayMetrics.density
        val size = (56 * d).toInt(); val half = size / 2
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = 170; style = Paint.Style.FILL }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFF000000.toInt(); style = Paint.Style.STROKE; strokeWidth = 2 * d }
        val hv = object : View(this) {
            override fun onDraw(c: Canvas) {
                c.drawCircle(width / 2f, height / 2f, 14 * d, fill)
                c.drawCircle(width / 2f, height / 2f, 14 * d, ring)
            }
        }
        val lp = WindowManager.LayoutParams(size, size, type, base, PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = (rv.pts[idx].x - half).toInt(); lp.y = (rv.pts[idx].y - half).toInt()
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0
        hv.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = ox + (e.rawX - sx).toInt(); lp.y = oy + (e.rawY - sy).toInt()
                    wm.updateViewLayout(hv, lp)
                    rv.pts[idx].set((lp.x + half).toFloat(), (lp.y + half).toFloat())
                    rv.invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> rv.save()
            }
            true
        }
        wm.addView(hv, lp)
        handles.add(hv to lp)
    }

    private fun addPills(d: Float) {
        val rv = ruler ?: return
        modePill = makePill((16 * d).toInt(), (110 * d).toInt()) { rv.tabela = !rv.tabela; applyState() }
        eyePill = makePill((16 * d).toInt(), (166 * d).toInt()) { hidden = !hidden; applyState() }
    }

    /** Botão arrastável em formato de pílula; toque simples chama onTap. */
    private fun makePill(x: Int, y: Int, onTap: () -> Unit): TextView {
        val d = resources.displayMetrics.density
        val tv = TextView(this).apply {
            textSize = 13f; gravity = Gravity.CENTER; setTextColor(0xFFFFFFFF.toInt())
            setPadding((14 * d).toInt(), 0, (14 * d).toInt(), 0)
            background = GradientDrawable().apply { cornerRadius = 22 * d; setColor(0xDD202020.toInt()) }
        }
        val lp = WindowManager.LayoutParams(-2, (44 * d).toInt(), type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.START; lp.x = x; lp.y = y
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
