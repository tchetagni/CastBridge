package castbridge.receiver

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import castbridge.core.parental.ParentalPins

/**
 * Views of the parental screens, all built in code like the rest of the TV UI: D-pad friendly, big text (readable at 3 m),
 * every block in a vertical LinearLayout so that no text can overlap another at 1280x720 or 1920x1080.
 * Colors come from [TvStyle] (the charte graphique is applied there).
 */
object ParentalUi {
    const val TITLE_SP = 30f
    const val BODY_SP = 22f
    const val SMALL_SP = 18f

    fun duration(sec: Long): String = if (sec >= 120) "${(sec + 59) / 60} minutes" else "$sec secondes"

    fun text(ctx: Context, s: String, sp: Float = BODY_SP, color: Int = Color.WHITE, bold: Boolean = false) = TextView(ctx).apply {
        text = s; textSize = sp; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /** A focusable button: dark card, yellow-blue frame and inverted text when focused. */
    fun button(ctx: Context, label: String, widthDp: Int = 0, heightDp: Int = 60, sp: Float = 24f, onClick: () -> Unit) = Button(ctx).apply {
        text = label; textSize = sp; isAllCaps = false; isFocusable = true; isFocusableInTouchMode = true
        typeface = Typeface.DEFAULT_BOLD
        fun paint(f: Boolean) {
            background = if (f) TvStyle.rounded(ctx, TvStyle.ACCENT, 10) else TvStyle.rounded(ctx, TvStyle.CARD, 10, TvStyle.MUTED, 1)
            setTextColor(if (f) 0xFF0E1116.toInt() else Color.WHITE)
        }
        paint(false)
        setOnFocusChangeListener { _, f -> paint(f) }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(if (widthDp > 0) TvStyle.dp(ctx, widthDp) else ViewGroup.LayoutParams.MATCH_PARENT, TvStyle.dp(ctx, heightDp)).apply {
            setMargins(TvStyle.dp(ctx, 4), TvStyle.dp(ctx, 4), TvStyle.dp(ctx, 4), TvStyle.dp(ctx, 4))
        }
    }

    /** A list row: a title and a value or explanation under it (never side by side, so nothing overlaps). */
    fun row(ctx: Context, title: String, value: String?, onClick: () -> Unit): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL; isFocusable = true; isFocusableInTouchMode = true; isClickable = true
        val pad = TvStyle.dp(ctx, 14)
        setPadding(pad, pad, pad, pad)
        addView(text(ctx, title, BODY_SP, Color.WHITE, true))
        if (!value.isNullOrEmpty()) addView(text(ctx, value, SMALL_SP, TvStyle.MUTED))
        fun paint(f: Boolean) { background = if (f) TvStyle.rounded(ctx, TvStyle.CARD_FOCUS, 10, TvStyle.ACCENT, 2) else TvStyle.rounded(ctx, TvStyle.CARD, 10) }
        paint(false)
        setOnFocusChangeListener { _, f -> paint(f) }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, TvStyle.dp(ctx, 5), 0, TvStyle.dp(ctx, 5)) }
    }

    /**
     * Numeric keypad for the remote: digits, "Effacer", "Valider". Number keys of the remote work too. [check] returns an error text
     * (shown, the pad empties) or null when the PIN is accepted; it runs off the main thread (the hash is slow on purpose).
     * BACK cancels. With [confirmTwice] the PIN must be typed twice (creation of a PIN).
     */
    fun pinDialog(a: Activity, title: String, hint: String, check: (String) -> String?, onOk: (String) -> Unit) {
        val ctx = a
        val digits = StringBuilder()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            val p = TvStyle.dp(ctx, 20); setPadding(p, p, p, p); setBackgroundColor(TvStyle.BG)
        }
        root.addView(text(ctx, title, 26f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        val msg = text(ctx, hint, SMALL_SP, TvStyle.MUTED).apply { gravity = Gravity.CENTER }
        root.addView(msg)
        val dots = text(ctx, "", 40f, TvStyle.ACCENT, true).apply { gravity = Gravity.CENTER; minHeight = TvStyle.dp(ctx, 56) }
        root.addView(dots)
        var busy = false
        val dialog = AlertDialog.Builder(ctx).setView(root).create()
        fun refresh() { dots.text = if (digits.isEmpty()) "–" else "●".repeat(digits.length) }
        fun push(d: Char) { if (!busy && digits.length < ParentalPins.MAX) { digits.append(d); refresh() } }
        fun back() { if (!busy && digits.isNotEmpty()) { digits.deleteCharAt(digits.length - 1); refresh() } }
        fun submit() {
            if (busy) return
            val pin = digits.toString()
            if (pin.isEmpty()) { msg.text = "Tapez votre code avec les chiffres ci-dessous."; msg.setTextColor(0xFFFFB74D.toInt()); return }
            busy = true; msg.text = "Vérification…"; msg.setTextColor(TvStyle.MUTED)
            Thread {
                val err = runCatching { check(pin) }.getOrElse { "Erreur : ${it.message}" }
                a.runOnUiThread {
                    busy = false
                    if (err == null) { dialog.dismiss(); onOk(pin) }
                    else { digits.setLength(0); refresh(); msg.text = err; msg.setTextColor(0xFFFF8A80.toInt()) }
                }
            }.start()
        }
        refresh()
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "Effacer", "0", "Valider")
        for (r in 0 until 4) {
            val line = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            for (c in 0 until 3) {
                val k = keys[r * 3 + c]
                line.addView(button(ctx, k, widthDp = 120, heightDp = 60, sp = if (k.length > 1) 20f else 28f) {
                    when (k) { "Effacer" -> back(); "Valider" -> submit(); else -> push(k[0]) }
                })
            }
            root.addView(line)
        }
        root.addView(button(ctx, "Annuler", widthDp = 200, heightDp = 52, sp = 20f) { if (!busy) dialog.dismiss() }.also { (it.layoutParams as LinearLayout.LayoutParams).gravity = Gravity.CENTER_HORIZONTAL })
        dialog.setOnKeyListener { _, code, ev ->
            if (ev.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when {
                code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> { push('0' + (code - KeyEvent.KEYCODE_0)); true }
                code in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> { push('0' + (code - KeyEvent.KEYCODE_NUMPAD_0)); true }
                code == KeyEvent.KEYCODE_DEL || code == KeyEvent.KEYCODE_CLEAR -> { back(); true }
                else -> false                                   // BACK closes the dialog, D-pad moves the focus
            }
        }
        dialog.show()
        dialog.window?.setLayout(TvStyle.dp(ctx, 460), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawable(TvStyle.rounded(ctx, TvStyle.BG, 16, TvStyle.MUTED, 1))
    }

    /** Creation of a PIN: typed twice. [save] gets the PIN and returns an error text or null. */
    fun createPin(a: Activity, title: String, save: (String) -> String?, done: () -> Unit) {
        pinDialog(a, title, "Choisissez 4 à 6 chiffres que seuls les parents connaissent (pas 1234, pas 4444).",
            check = { first -> ParentalPins.validateNew(first) }, onOk = { first ->
                pinDialog(a, "Confirmez le code", "Tapez le même code une seconde fois.",
                    check = { second -> if (second != first) "Les deux codes sont différents. Recommencez." else save(first) }, onOk = { done() })
            })
    }

    fun choose(a: Activity, title: String, labels: List<String>, current: Int = -1, onPick: (Int) -> Unit) {
        AlertDialog.Builder(a).setTitle(title).setSingleChoiceItems(labels.toTypedArray(), current) { d, i -> d.dismiss(); onPick(i) }
            .setNegativeButton("Annuler", null).show()
    }

    fun info(a: Activity, title: String, message: String) {
        AlertDialog.Builder(a).setTitle(title).setMessage(message).setPositiveButton("Compris", null).show()
    }
}
