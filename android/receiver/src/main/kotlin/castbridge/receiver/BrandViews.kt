package castbridge.receiver

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import castbridge.core.brand.Brand

/**
 * Marque blanche (docs/WHITE-LABEL.md) : les écrans de la TV sont des vues construites en code avec des textes qui nomment « CastBridge ».
 * Pour une marque autre que CastBridge (le seul cas où ce rappel est enregistré, voir [TvApp]), chaque mise en page repasse sur les TextView
 * de la fenêtre et remplace le nom ([Brand.t]). Le test `contains("CastBridge")` rend le passage presque gratuit ; un texte déjà remplacé n'est pas retouché.
 */
object BrandViews : Application.ActivityLifecycleCallbacks {
    private val listeners = java.util.WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener>()

    override fun onActivityResumed(a: Activity) {
        val root = a.window?.decorView ?: return
        if (listeners.containsKey(a)) return
        val l = ViewTreeObserver.OnGlobalLayoutListener { rewrite(root) }
        listeners[a] = l
        root.viewTreeObserver.addOnGlobalLayoutListener(l)
        rewrite(root)
    }

    override fun onActivityDestroyed(a: Activity) { listeners.remove(a) }
    override fun onActivityCreated(a: Activity, b: Bundle?) {}
    override fun onActivityStarted(a: Activity) {}
    override fun onActivityPaused(a: Activity) {}
    override fun onActivityStopped(a: Activity) {}
    override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}

    private fun rewrite(v: View) {
        if (v is TextView && v !is android.widget.EditText) {          // jamais le texte que l'utilisateur tape
            val s = v.text.toString()
            if (s.contains("CastBridge")) { val n = Brand.t(s); if (n != s) v.text = n }     // un texte stylé perd son style : seul cas touché, une marque autre que CastBridge
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) rewrite(v.getChildAt(i))
    }
}
