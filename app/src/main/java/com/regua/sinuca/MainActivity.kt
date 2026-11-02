package com.regua.sinuca

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        val p = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(p, p, p, p); gravity = Gravity.CENTER_VERTICAL }
        root.addView(TextView(this).apply {
            textSize = 16f
            text = "Régua Sinuca\n\n1. Permita \"exibir sobre outros apps\".\n2. Inicie a régua e abra o jogo.\n3. Bolha ✎/▶: ajustar (arrastar pontos) ou jogar (toques passam para o jogo).\n\nLaranja = cantos da mesa (calibre uma vez)\nBranco = bola branca\nAmarelo = direção da linha"
        })
        fun btn(t: String, f: () -> Unit) = root.addView(Button(this).apply { text = t; setOnClickListener { f() } })
        btn("Permitir sobreposição") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        btn("Iniciar régua") {
            if (Settings.canDrawOverlays(this)) startForegroundService(Intent(this, OverlayService::class.java))
        }
        btn("Parar régua") { stopService(Intent(this, OverlayService::class.java)) }
        setContentView(root)
    }
}
