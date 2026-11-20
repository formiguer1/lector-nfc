package com.ejemplo.lectornfc

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.view.Gravity
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity(), NfcAdapter.ReaderCallback {
    private var nfc: NfcAdapter? = null
    private lateinit var texto: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        texto = TextView(this).apply { textSize = 18f; setPadding(48, 48, 48, 48); gravity = Gravity.START }
        setContentView(ScrollView(this).apply { addView(texto) })
        nfc = NfcAdapter.getDefaultAdapter(this)
        texto.text = when {
            nfc == null -> "Este dispositivo no tiene NFC."
            !nfc!!.isEnabled -> "Activa el NFC en Ajustes y vuelve a la app."
            else -> "Acerca una tarjeta NFC…"
        }
    }

    override fun onResume() {
        super.onResume()
        nfc?.enableReaderMode(this, this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V, null)
    }

    override fun onPause() { super.onPause(); nfc?.disableReaderMode(this) }

    override fun onTagDiscovered(tag: Tag) {
        val sb = StringBuilder("Tarjeta detectada\n\n")
        sb.append("UID: ").append(tag.id.joinToString(":") { "%02X".format(it) }).append("\n\n")
        sb.append("Tecnologías:\n")
        tag.techList.forEach { sb.append(" • ").append(it.substringAfterLast('.')).append("\n") }
        Ndef.get(tag)?.let { ndef ->
            try {
                ndef.connect()
                val msg: NdefMessage? = ndef.ndefMessage
                sb.append("\nNDEF (").append(ndef.type).append(", máx ").append(ndef.maxSize).append(" bytes):\n")
                msg?.records?.forEachIndexed { i, r ->
                    val payload = r.payload
                    val contenido = when {
                        r.toUri() != null -> r.toUri().toString()
                        r.tnf == 1.toShort() && String(r.type) == "T" -> {
                            val langLen = payload[0].toInt() and 0x3F
                            String(payload, 1 + langLen, payload.size - 1 - langLen, Charsets.UTF_8)
                        }
                        else -> payload.joinToString(" ") { "%02X".format(it) }
                    }
                    sb.append(" ").append(i + 1).append(". ").append(contenido).append("\n")
                } ?: sb.append(" (vacía)\n")
                ndef.close()
            } catch (e: Exception) { sb.append("\nError leyendo NDEF: ").append(e.message) }
        }
        runOnUiThread { texto.text = sb.toString() }
    }
}
