package com.regua.sinuca

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var ruler: RulerView? = null
    private var bubble: TextView? = null
    private lateinit var rp: WindowManager.LayoutParams
    private val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (ruler != null) return START_STICKY
        startFg()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val d = resources.displayMetrics.density
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        val rv = RulerView(this); ruler = rv
        rp = WindowManager.LayoutParams(-1, -1, type, baseFlags, PixelFormat.TRANSLUCENT)
        wm.addView(rv, rp)

        val size = (52 * d).toInt()
        val bv = TextView(this).apply {
            text = "▶"; textSize = 20f; gravity = Gravity.CENTER; setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xCC202020.toInt()) }
        }
        bubble = bv
        val bp = WindowManager.LayoutParams(size, size, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
        bp.gravity = Gravity.TOP or Gravity.START; bp.x = (16 * d).toInt(); bp.y = (120 * d).toInt()
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        bv.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = bp.x; oy = bp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(e.rawX - sx) > 10 * d || abs(e.rawY - sy) > 10 * d) moved = true
                    if (moved) { bp.x = ox + (e.rawX - sx).toInt(); bp.y = oy + (e.rawY - sy).toInt(); wm.updateViewLayout(bv, bp) }
                }
                MotionEvent.ACTION_UP -> if (!moved) toggle()
            }
            true
        }
        wm.addView(bv, bp)
        toggle() // começa em modo ajustar? alterna para jogar
        toggle()
        return START_STICKY
    }

    /** Modo ajustar: pontos visíveis e arrastáveis. Modo jogar: toques passam para o jogo. */
    private fun toggle() {
        val rv = ruler ?: return
        rv.edit = !rv.edit
        rp.flags = if (rv.edit) baseFlags else baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        wm.updateViewLayout(rv, rp)
        bubble?.text = if (rv.edit) "▶" else "✎"
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
        ruler?.let { wm.removeView(it) }; bubble?.let { wm.removeView(it) }
        ruler = null; bubble = null
        super.onDestroy()
    }
}
