package castbridge.sender.agent

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import castbridge.core.library.agent.Insight
import castbridge.core.library.agent.InsightFilter
import castbridge.core.library.agent.LibraryAgent
import castbridge.core.library.agent.TvSnapshot
import castbridge.core.tv.TvClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One discreet line in the TV library ("12 fichiers mal nommés", "3 doublons = 4,2 Go", "La clé est pleine à 92 %"). Never a
 * notification, never a pop-up: it only appears inside the library screen, can be snoozed for a week, and is computed locally
 * (no network but the TV, no AI, no fingerprint).
 */
@Composable
fun AssistantBanner(client: TvClient, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    var insight by remember(client.base) { mutableStateOf<Insight?>(null) }
    LaunchedEffect(client.base) {
        AgentStore.init(ctx)
        if (AgentStore.guard.childProfileActive) return@LaunchedEffect
        val now = System.currentTimeMillis()
        val cached = BannerCache.get(client.base, now)
        val all = cached ?: withContext(Dispatchers.IO) {
            runCatching { LibraryAgent(AgentStore.agentContext(folders = false), AgentStore.learned).analyze(TvSnapshot.read(client)).insights }.getOrDefault(emptyList())
        }.also { BannerCache.put(client.base, it, now) }
        insight = InsightFilter.visible(all, AgentStore.settings.hiddenUntil(), now).firstOrNull()
    }
    val i = insight ?: return
    Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(start = 12.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lightbulb, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(i.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onOpen) { Text("Ranger") }
            IconButton({ AgentStore.settings.snooze(i.id, 7); insight = null }) { Icon(Icons.Filled.Close, "Masquer ce conseil pendant 7 jours") }
        }
    }
}

private object BannerCache {
    private var base: String? = null
    private var at = 0L
    private var list: List<Insight> = emptyList()
    fun get(b: String, now: Long): List<Insight>? = if (b == base && now - at < 10 * 60_000L) list else null
    fun put(b: String, l: List<Insight>, now: Long) { base = b; at = now; list = l }
}
