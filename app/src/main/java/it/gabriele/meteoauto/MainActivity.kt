package it.gabriele.meteoauto

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Schermata sul telefono: stessi dati della versione Android Auto. */
class MainActivity : Activity() {
    private val scope = MainScope()
    private var job: Job? = null
    private lateinit var contenuto: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("meteo", Context.MODE_PRIVATE)
        val pad = (16 * resources.displayMetrics.density).toInt()

        val radice = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                Comuni.elenco.map { it.nome }
            )
            setSelection(Comuni.elenco.indexOf(Comuni.carica(prefs)))
        }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val c = Comuni.elenco[pos]
                Comuni.salva(prefs, c)
                aggiorna(c)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        val bottone = Button(this).apply {
            text = "Aggiorna"
            setOnClickListener { aggiorna(Comuni.carica(prefs)) }
        }

        contenuto = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        radice.addView(spinner)
        radice.addView(bottone)
        radice.addView(contenuto)
        setContentView(ScrollView(this).apply { addView(radice) })
        // Il primo caricamento parte da onItemSelected dopo setSelection
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun aggiorna(c: Comune) {
        job?.cancel()
        contenuto.removeAllViews()
        testo("Caricamento…")
        job = scope.launch {
            try {
                val (m, a) = withContext(Dispatchers.IO) {
                    OpenMeteo.carica(c.lat, c.lon) to runCatching { Allerte.carica() }.getOrNull()
                }
                mostra(c, m, a)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                contenuto.removeAllViews()
                titolo("Errore")
                testo(e.message ?: "Errore di rete")
            }
        }
    }

    private fun mostra(c: Comune, m: Meteo, allerta: Allerta?) {
        contenuto.removeAllViews()
        val ora = SimpleDateFormat("HH:mm", Locale.ITALY).format(Date())
        val a = m.attuale

        allerta?.let {
            titolo("⚠ Allerta meteo" + (it.data?.let { d -> " · $d" } ?: ""))
            testo(it.testo)
        }

        titolo("${c.nome} · ${OpenMeteo.categoria(a.codice)} · ${a.temp.g()}°C")
        testo("Percepita ${a.percepita.g()}°C · umidità ${a.umidita}%")
        testo("${a.pressione.g()} hPa · agg. $ora")
        testo("Vento ${a.vento.g()} km/h ${OpenMeteo.nomeVento(a.direzione)} · raffiche ${a.raffiche.g()} km/h")
        m.onde?.let { testo("Onde %.1f m".format(Locale.ITALY, it)) }

        m.giorni.forEach { g ->
            titolo("${OpenMeteo.giornoBreve(g.data)} · ${OpenMeteo.categoria(g.codice)}")
            testo("${g.tMin.g()}/${g.tMax.g()}°C · vento max ${g.ventoMax.g()} km/h")
            g.fasce.forEach { f -> testo("${f.nome}: ${f.cielo} · ${f.tMedia.g()}°C") }
        }
    }

    private fun titolo(s: String) = contenuto.addView(TextView(this).apply {
        text = s
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 0)
    })

    private fun testo(s: String) = contenuto.addView(TextView(this).apply {
        text = s
        textSize = 15f
    })
}
