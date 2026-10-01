package castbridge.sender

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import castbridge.core.lots.LotBudget
import castbridge.core.lots.LotId
import castbridge.core.lots.LotMeta
import castbridge.core.quiz.QuizLotConsumer
import castbridge.core.quiz.QuizLotScopes
import castbridge.core.quiz.QuizThemes
import java.io.File

/**
 * What the lots framework plugs into the Quiz tab (the framework owns downloads and the « Données » screen): [download] fetches
 * one theme (called from a button, may be null until the framework is wired) and [openData] opens the framework's « Données » screen.
 */
object QuizLotHooks {
    @Volatile var download: ((LotId) -> Unit)? = null
    @Volatile var openData: (() -> Unit)? = null
    /** Signature (Ed25519, base64) of a lot's catalog entry, given by the framework with each lot it delivers. */
    @Volatile var signatureOf: (LotMeta) -> String? = { null }
}

/** The quiz lots held by the phone (up to 100 MB for every feature together) and the last catalog seen: everything readable with no Internet. */
object PhoneQuizLots {
    @Volatile private var consumer: QuizLotConsumer? = null
    @Synchronized fun consumer(ctx: Context): QuizLotConsumer = consumer ?: QuizLotConsumer(
        File(ctx.applicationContext.filesDir, "lots/quiz"), publicKeys = castbridge.core.update.UpdateKeys.PUBLIC_KEYS + listOf(BuildConfig.EXTRA_UPDATE_KEY).filter { it.isNotBlank() },
        signatureOf = { QuizLotHooks.signatureOf(it) }, maxBytes = LotBudget.PHONE_MAX_BYTES,
    ).also { consumer = it }

    /** Last server catalog (written by the framework's synchronisation through [QuizThemes.CatalogCache.write]). */
    fun catalogFile(ctx: Context) = File(ctx.applicationContext.filesDir, "lots/quiz-catalog.json")
}

/** « Mes thèmes »: one card per lot, with version, size, freshness, whether the TV has it, and what to do when it is not downloaded. */
@Composable
fun QuizThemesSection(tvLots: List<LotMeta>?) {
    val ctx = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val cached = remember(refresh) { QuizThemes.CatalogCache.read(PhoneQuizLots.catalogFile(ctx)) }
    val lots = remember { PhoneQuizLots.consumer(ctx) }
    val themes = remember(refresh, tvLots) {
        QuizThemes.build(lots.installed(), cached?.first, tvLots, { lots.installedAt(it) }, System.currentTimeMillis())
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Mes thèmes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Les questions sont rangées par thème (classe, niveau, culture). Elles sont téléchargées quand le téléphone a Internet, puis envoyées à la TV, " +
            "qui joue sans Internet avec ce qu'elle a déjà.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (cached != null) "Catalogue : " + QuizThemes.freshnessLabel((System.currentTimeMillis() - cached.second) / 86_400_000L).replace("mis à jour", "vu")
            else "Catalogue : pas encore synchronisé (Internet nécessaire une première fois).", style = MaterialTheme.typography.bodySmall)
        themes.forEach { t ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(t.title, fontWeight = FontWeight.SemiBold)
                    Text(QuizThemes.statusLine(t), style = MaterialTheme.typography.bodySmall)
                    Text(QuizThemes.tvLine(t), style = MaterialTheme.typography.bodySmall,
                        color = if (t.tvBehind) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (t.state == QuizThemes.State.NOT_DOWNLOADED || t.state == QuizThemes.State.UPDATE_AVAILABLE) {
                        val dl = QuizLotHooks.download
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (dl != null) Button(onClick = { dl(LotId(QuizLotScopes.FEATURE, t.scope)); refresh++ }) {
                                Text(if (t.state == QuizThemes.State.UPDATE_AVAILABLE) "Mettre à jour" else "Télécharger")
                            }
                            QuizLotHooks.openData?.let { open -> TextButton(onClick = open) { Text("Ouvrir « Données »") } }
                        }
                    }
                }
            }
        }
        TextButton(onClick = { refresh++ }) { Text("Actualiser") }
    }
}
