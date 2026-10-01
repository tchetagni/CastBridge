package castbridge.receiver

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import castbridge.core.parental.LockScreenModel
import castbridge.core.parental.ParentalKeys

/**
 * The lock screen: shown instead of a screen that the parental control refuses (games, downloads, settings, a video outside the
 * hours...). It NEVER traps the viewer: BACK and the button "Retour à l'accueil" leave at once, and "Saisir le PIN parental" opens a
 * parent session (the rules are suspended for a while) and brings back the screen that was refused. Not exported.
 */
class ParentalLockActivity : Activity() {
    private var target: String? = null
    /** Whole-TV supervision: the app that was refused (to bring back after the PIN) and why (« apppin » = that app only asked for the PIN). */
    private var targetPkg: String? = null
    private var code: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ParentalHub.init(this)
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "Cet écran est protégé par le contrôle parental."
        target = intent.getStringExtra(EXTRA_TARGET)
        targetPkg = intent.getStringExtra(EXTRA_PKG)
        code = intent.getStringExtra(EXTRA_CODE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            val p = TvStyle.dp(this@ParentalLockActivity, 40); setPadding(p, p, p, p)
            setBackgroundColor(TvStyle.BG)
        }
        root.addView(ParentalUi.text(this, "Accès protégé", 36f, TvStyle.ACCENT, true).apply { gravity = Gravity.CENTER })
        root.addView(ParentalUi.text(this, reason, 26f, Color.WHITE).apply {
            gravity = Gravity.CENTER; setPadding(0, TvStyle.dp(this@ParentalLockActivity, 16), 0, TvStyle.dp(this@ParentalLockActivity, 28))
        })
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(TvStyle.dp(this@ParentalLockActivity, 520), ViewGroup.LayoutParams.WRAP_CONTENT) }
        var first: android.view.View? = null
        for (a in LockScreenModel.actions(reason)) {
            val b = ParentalUi.button(this, a.label, heightDp = 68, sp = 26f) {
                when (a) { LockScreenModel.Action.ENTER_PIN -> askPin(); LockScreenModel.Action.GO_HOME -> goHome() }
            }
            if (first == null) first = b
            column.addView(b)
        }
        root.addView(column)
        setContentView(root)
        first?.requestFocus()
    }

    /** A second refusal while the lock screen is open (another app, another reason): show the new one. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private fun askPin() {
        val e = ParentalHub.engine
        if (!e.hasPin()) { goHome(); return }
        ParentalUi.pinDialog(this, "Code parental", "Saisissez le code pour déverrouiller la TV ${e.config().sessionMin} minutes.",
            check = { pin -> ParentalHub.pinError(e.verifyPin(pin)) }, onOk = {
                val pkg = targetPkg
                // an app set to « Code parental requis » opens for that app only; any other refusal opens a parent session
                if (pkg != null && code == "apppin") e.grantApp(pkg) else e.startSession()
                val cls = target?.let { runCatching { Class.forName(it) }.getOrNull() }
                finish()
                if (pkg != null) {
                    val i = packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg)
                    if (i != null) runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                } else if (cls != null && Activity::class.java.isAssignableFrom(cls)) runCatching { startActivity(Intent(this, cls)) }
            })
    }

    private fun goHome() {
        startActivity(Intent(this, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    /** BACK and HOME are never blocked: BACK leaves to the home. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (ParentalKeys.neverBlocked(keyCode) || keyCode == KeyEvent.KEYCODE_ESCAPE) { goHome(); return true }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        private const val EXTRA_REASON = "reason"
        private const val EXTRA_TARGET = "target"
        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_CODE = "code"

        /** Lock screen in front of another app of the TV (whole-TV supervision). Started from the background: needs « Afficher par-dessus les autres apps ». */
        fun showForApp(ctx: Context, reason: String, pkg: String, code: String) {
            val i = Intent(ctx, ParentalLockActivity::class.java).putExtra(EXTRA_REASON, reason).putExtra(EXTRA_PKG, pkg).putExtra(EXTRA_CODE, code)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            runCatching { ctx.startActivity(i) }
        }

        /** Shows the lock screen over whatever is on the TV. [target] is the screen to bring back after a correct PIN. */
        fun show(ctx: Context, reason: String, target: Class<out Activity>?) {
            val i = Intent(ctx, ParentalLockActivity::class.java).putExtra(EXTRA_REASON, reason)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            target?.let { i.putExtra(EXTRA_TARGET, it.name) }
            if (ctx !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { ctx.startActivity(i) }
        }
    }
}
