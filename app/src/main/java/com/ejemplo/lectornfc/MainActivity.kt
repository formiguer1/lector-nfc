package com.ejemplo.lectornfc

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Tarjeta(val uid: String, var nombre: String, var ultima: Long, var veces: Int, var info: String)

class MainActivity : Activity(), NfcAdapter.ReaderCallback {
    private var nfc: NfcAdapter? = null
    private lateinit var estado: TextView
    private lateinit var adapter: ArrayAdapter<String>
    private val tarjetas = mutableListOf<Tarjeta>()
    private val prefs by lazy { getSharedPreferences("tarjetas", Context.MODE_PRIVATE) }
    private val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "ES"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        estado = TextView(this).apply { textSize = 16f; setPadding(40, 40, 40, 24) }
        val titulo = TextView(this).apply {
            text = "Tarjetas leídas (toca para poner nombre, mantén pulsado para borrar)"
            textSize = 13f; setPadding(40, 8, 40, 8)
        }
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        val lista = ListView(this).apply {
            adapter = this@MainActivity.adapter
            setOnItemClickListener { _, _, pos, _ -> renombrar(tarjetas[pos]) }
            setOnItemLongClickListener { _, _, pos, _ -> borrar(tarjetas[pos]); true }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(estado); addView(titulo)
            addView(lista, LinearLayout.LayoutParams(-1, 0, 1f))
        })
        nfc = NfcAdapter.getDefaultAdapter(this)
        estado.text = when {
            nfc == null -> "Este dispositivo no tiene NFC."
            !nfc!!.isEnabled -> "Activa el NFC en Ajustes y vuelve a la app."
            else -> "Acerca una tarjeta NFC…"
        }
        cargar(); refrescar()
    }

    override fun onResume() {
        super.onResume()
        nfc?.enableReaderMode(this, this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V, null)
    }

    override fun onPause() { super.onPause(); nfc?.disableReaderMode(this) }

    override fun onTagDiscovered(tag: Tag) {
        val uid = tag.id.joinToString(":") { "%02X".format(it) }
        val sb = StringBuilder()
        sb.append("Tecnologías: ").append(tag.techList.joinToString(", ") { it.substringAfterLast('.') })
        Ndef.get(tag)?.let { ndef ->
            try {
                ndef.connect()
                ndef.ndefMessage?.records?.forEach { r ->
                    val p = r.payload
                    val c = when {
                        r.toUri() != null -> r.toUri().toString()
                        r.tnf == 1.toShort() && String(r.type) == "T" -> {
                            val l = p[0].toInt() and 0x3F
                            String(p, 1 + l, p.size - 1 - l, Charsets.UTF_8)
                        }
                        else -> p.joinToString(" ") { "%02X".format(it) }
                    }
                    sb.append("\nNDEF: ").append(c)
                }
                ndef.close()
            } catch (_: Exception) {}
        }
        runOnUiThread {
            val t = tarjetas.find { it.uid == uid }
            val ahora = System.currentTimeMillis()
            if (t != null) { t.ultima = ahora; t.veces++; t.info = sb.toString() }
            else tarjetas.add(Tarjeta(uid, "", ahora, 1, sb.toString()))
            guardar(); refrescar()
            val actual = tarjetas.first { it.uid == uid }
            estado.text = "Última leída: ${actual.nombre.ifEmpty { "(sin nombre)" }}\nUID: $uid\n${actual.info}"
            if (actual.nombre.isEmpty()) renombrar(actual)
        }
    }

    private fun renombrar(t: Tarjeta) {
        val campo = EditText(this).apply { setText(t.nombre); hint = "Nombre de la tarjeta"; setSelection(text.length) }
        AlertDialog.Builder(this)
            .setTitle("Nombre para ${t.uid}")
            .setView(campo)
            .setPositiveButton("Guardar") { _, _ -> t.nombre = campo.text.toString().trim(); guardar(); refrescar() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun borrar(t: Tarjeta) {
        AlertDialog.Builder(this)
            .setMessage("¿Borrar ${t.nombre.ifEmpty { t.uid }}?")
            .setPositiveButton("Borrar") { _, _ -> tarjetas.remove(t); guardar(); refrescar() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun refrescar() {
        tarjetas.sortByDescending { it.ultima }
        adapter.clear()
        adapter.addAll(tarjetas.map {
            "${it.nombre.ifEmpty { "(sin nombre)" }}\n${it.uid}\n${fecha.format(Date(it.ultima))} · ${it.veces} lectura(s)"
        })
    }

    private fun guardar() {
        val json = JSONObject()
        tarjetas.forEach {
            json.put(it.uid, JSONObject().put("nombre", it.nombre).put("ultima", it.ultima)
                .put("veces", it.veces).put("info", it.info))
        }
        prefs.edit().putString("datos", json.toString()).apply()
    }

    private fun cargar() {
        val json = JSONObject(prefs.getString("datos", "{}") ?: "{}")
        json.keys().forEach { uid ->
            val o = json.getJSONObject(uid)
            tarjetas.add(Tarjeta(uid, o.optString("nombre"), o.optLong("ultima"), o.optInt("veces", 1), o.optString("info")))
        }
    }
}
