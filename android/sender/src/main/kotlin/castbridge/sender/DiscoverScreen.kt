package castbridge.sender

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Task destinations retain the existing screen IDs, permissions and telemetry. */
@Composable
fun DiscoverScreen(open: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("À découvrir", style = MaterialTheme.typography.headlineLarge)
        Text("Apprendre, jouer et partager, à votre rythme.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(
            Triple(4, "Apprendre", "Formations, langues et révisions"),
            Triple(2, "Jouer", "Quiz, échecs et jeux en famille"),
            Triple(0, "Autres téléviseurs", "Diffuser sur un lecteur compatible DLNA"),
            Triple(5, "Espace parental", "Profils, règles et rapports protégés"),
        ).forEach { (id, title, detail) ->
            Card(Modifier.fillMaxWidth().clickable { open(id) }) {
                ListItem(
                    headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text(detail) },
                    leadingContent = { Icon(when (id) { 4 -> Icons.Filled.MenuBook; 2 -> Icons.Filled.SportsEsports; 0 -> Icons.Filled.Cast; else -> Icons.Filled.Lock }, null) },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        }
    }
}
