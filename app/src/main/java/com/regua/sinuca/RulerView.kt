package com.regua.sinuca

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot

/** Pontos: 0 canto sup-esq, 1 canto inf-dir, 2 bola branca, 3 direção. */
class RulerView(c: Context) : View(c) {
    var edit = true
        set(v) { field = v; invalidate() }
    private val prefs = c.getSharedPreferences("ruler", 0)
    private val d = resources.displayMetrics.density
    private val pts = Array(4) { PointF(-1f, -1f) }
    private var grab = -1

    private fun paint(color: Int, w: Float, dash: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; strokeWidth = w * d; style = Paint.Style.STROKE
        if (dash) pathEffect = DashPathEffect(floatArrayOf(10f * d, 8f * d), 0f)
    }
    private val tablePaint = paint(0x99FFA000.toInt(), 2f, true)
    private val pocketPaint = paint(0xCCFFFFFF.toInt(), 2f)
    private val pocketLine = paint(0x6600E5FF, 1.2f)
    private val linePaint = paint(0xFFFFFFFF.toInt(), 2.5f)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val def = arrayOf(.16f to .2f, .84f to .88f, .3f to .5f, .5f to .5f)
        for (i in 0..3) {
            val x = prefs.getFloat("x$i", -1f); val y = prefs.getFloat("y$i", -1f)
            if (x in 0f..w.toFloat() && y in 0f..h.toFloat()) pts[i].set(x, y)
            else pts[i].set(w * def[i].first, h * def[i].second)
        }
    }

    private fun save() = prefs.edit().apply { for (i in 0..3) { putFloat("x$i", pts[i].x); putFloat("y$i", pts[i].y) } }.apply()

    override fun onDraw(c: Canvas) {
        val l = minOf(pts[0].x, pts[1].x); val r = maxOf(pts[0].x, pts[1].x)
        val t = minOf(pts[0].y, pts[1].y); val b = maxOf(pts[0].y, pts[1].y)
        val cue = pts[2]
        c.drawRect(l, t, r, b, tablePaint)
        val pockets = listOf(PointF(l, t), PointF((l + r) / 2, t), PointF(r, t), PointF(l, b), PointF((l + r) / 2, b), PointF(r, b))
        for (p in pockets) { c.drawLine(cue.x, cue.y, p.x, p.y, pocketLine); c.drawCircle(p.x, p.y, 16 * d, pocketPaint) }

        var x = cue.x; var y = cue.y
        var dx = pts[3].x - x; var dy = pts[3].y - y
        val len = hypot(dx, dy)
        if (len > 1f) {
            dx /= len; dy /= len
            val inside = x in l..r && y in t..b
            var n = 0
            while (n < 4) {
                var tt = 1e5f
                if (inside) {
                    if (dx > 1e-6f) tt = minOf(tt, (r - x) / dx) else if (dx < -1e-6f) tt = minOf(tt, (l - x) / dx)
                    if (dy > 1e-6f) tt = minOf(tt, (b - y) / dy) else if (dy < -1e-6f) tt = minOf(tt, (t - y) / dy)
                }
                val nx = x + dx * tt; val ny = y + dy * tt
                c.drawLine(x, y, nx, ny, linePaint)
                if (!inside || tt >= 1e5f) break
                if (abs(nx - l) < 1f || abs(nx - r) < 1f) dx = -dx
                if (abs(ny - t) < 1f || abs(ny - b) < 1f) dy = -dy
                x = nx; y = ny; n++
            }
        }
        if (edit) {
            val cols = intArrayOf(0xFFFFA000.toInt(), 0xFFFFA000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFEB3B.toInt())
            for (i in 0..3) { fill.color = cols[i]; fill.alpha = 200; c.drawCircle(pts[i].x, pts[i].y, 14 * d, fill) }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                grab = -1; var best = 80 * d
                for (i in 0..3) { val k = hypot(e.x - pts[i].x, e.y - pts[i].y); if (k < best) { best = k; grab = i } }
            }
            MotionEvent.ACTION_MOVE -> if (grab >= 0) { pts[grab].set(e.x, e.y); invalidate() }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { if (grab >= 0) save(); grab = -1 }
        }
        return true
    }
}
