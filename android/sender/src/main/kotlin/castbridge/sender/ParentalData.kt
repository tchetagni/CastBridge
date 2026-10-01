package castbridge.sender

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import castbridge.core.parental.InboxPersistence
import castbridge.core.parental.tab.ParentalLedger
import castbridge.core.parental.tab.Retention
import castbridge.core.parental.tab.TabLock

/** The local copy of the parental data in a private preferences file (excluded from backups, see backup_rules.xml). */
private class PrefsLedger(private val sp: SharedPreferences) : InboxPersistence {
    override fun load(): String? = sp.getString("ledger", null)
    override fun save(text: String) { sp.edit().putString("ledger", text).apply() }
}

/**
 * The phone's local parental data (docs/PARENTAL.md, « Onglet Parental »): the [ParentalLedger] fed from the inbox of reports and from the
 * live report over Wi-Fi. Everything stays on this phone; nothing is sent anywhere. [version] changes whenever the data changed (screens re-read).
 */
object ParentalData {
    private lateinit var ledgerOrNull: ParentalLedger
    private lateinit var sp: SharedPreferences
    var version by mutableIntStateOf(0); private set

    @Synchronized fun ledger(ctx: Context): ParentalLedger {
        if (!::ledgerOrNull.isInitialized) {
            sp = ctx.applicationContext.getSharedPreferences("castbridge_parental_ledger", Context.MODE_PRIVATE)
            ledgerOrNull = ParentalLedger(PrefsLedger(sp), Retention(sp.getInt("retention", Retention.DEFAULT_DAYS)))
        }
        return ledgerOrNull
    }

    /** Takes what the inbox holds (Bluetooth deliveries) into the ledger. Cheap and idempotent: call it whenever the tab shows. */
    fun refresh(ctx: Context) {
        ParentalInbox.init(ctx)
        if (ledger(ctx).absorb(ParentalInbox.inbox.history()) > 0) version++
    }

    fun changed() { version++ }

    fun setRetention(ctx: Context, days: Int) {
        ledger(ctx).applyRetention(Retention(days))
        sp.edit().putInt("retention", days).apply()
        version++
    }
}

/**
 * Session of the Parental tab, shared by the tab and the padlock screen: the parental PIN is asked once, kept in MEMORY only (never written,
 * never logged) and forgotten when the tab locks (a few minutes in the background, see [TabLock]). [readOnly]: opened with the phone's own screen lock
 * because the TV could not check the PIN; the figures are readable but nothing that needs the PIN (purge, TV settings) is offered.
 */
object ParentalSession {
    private val lock = TabLock()
    var open by mutableStateOf(false); private set
    var readOnly by mutableStateOf(false); private set
    private var pin: String? = null

    fun pin(): String? = pin
    fun unlockWithPin(p: String) { pin = p; readOnly = false; lock.unlock(); open = true }
    fun unlockReadOnly() { pin = null; readOnly = true; lock.unlock(); open = true }
    fun lockNow() { pin = null; lock.lock(); open = false }
    fun onBackground() { lock.onBackground(System.currentTimeMillis()) }
    fun onForeground() { if (!lock.onForeground(System.currentTimeMillis())) lockNow() }
    /** True when [typed] is the PIN of this session (the PIN asked again before a purge): compared in memory only. */
    fun confirms(typed: String): Boolean = pin != null && java.security.MessageDigest.isEqual(pin!!.toByteArray(), typed.toByteArray())
}
