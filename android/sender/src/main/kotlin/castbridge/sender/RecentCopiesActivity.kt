package castbridge.sender

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import castbridge.core.trust.CopyEntry
import java.text.DateFormat
import java.util.Date

/**
 * « Dernières copies » : les 20 dernières copies vers la TV, réussies ou non, avec la cause et l'action de chaque échec ([CopyJournal], aucune donnée sensible).
 * Ouvert depuis l'écran de la TV du téléphone.
 */
class RecentCopiesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val entries = runCatching { CopyReport.journal(this).recent() }.getOrDefault(emptyList())
        setContent { CastTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { RecentCopies(entries, ::finish) } } }
    }

    companion object { fun open(ctx: Context) = ctx.startActivity(Intent(ctx, RecentCopiesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun RecentCopies(entries: List<CopyEntry>, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Dernières copies vers la TV", style = MaterialTheme.typography.titleLarge)
        if (entries.isEmpty()) Text("Aucune copie pour le moment.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(entries) { e ->
                Column {
                    Text((if (e.ok) "Réussie : " else "Échec : ") + e.name, style = MaterialTheme.typography.bodyMedium, color = if (e.ok) cs.onSurface else cs.error)
                    Text(fmt.format(Date(e.atMs)) + (if (!e.ok) " · étape : ${e.step} · ${e.percent} %" else ""), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    if (e.text.isNotBlank()) Text(e.text, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        TextButton(onClick = onClose) { Text("Fermer") }
    }
}
