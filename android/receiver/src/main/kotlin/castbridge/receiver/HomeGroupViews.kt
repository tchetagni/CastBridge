package castbridge.receiver

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import castbridge.core.tv.home.HomeEntry
import castbridge.core.tv.home.HomeGrid
import castbridge.core.tv.home.HomeKey

/**
 * Vues de l'accueil en groupes (docs/TV-ACCUEIL.md). Aucune règle ici : la logique (groupes, grille, focus, RETOUR) est dans
 * castbridge.core.tv.home ; ces vues ne font que dessiner. Textes à 28 sp (étiquettes), icône ET libellé français toujours ensemble.
 */
object HomeGroupStyle {
    const val LABEL_SP = 28f
    const val STATUS_SP = 20f
    /** Icône du bouton de groupe (la logique ne connaît pas les ressources). */
    fun icon(groupId: String) = when (groupId) {
        castbridge.core.tv.home.HomeGroups.MEDIA -> R.drawable.ic_cb_bibliotheque
        castbridge.core.tv.home.HomeGroups.LEARN -> R.drawable.ic_cb_apprendre
        castbridge.core.tv.home.HomeGroups.GAMES -> R.drawable.ic_t_games
        castbridge.core.tv.home.HomeGroups.NETWORK -> R.drawable.ic_cb_bluetooth
        else -> R.drawable.ic_cb_administration
    }
}

/** Bouton de groupe : une grande tuile (icône + nom + résumé), contour discret pour la distinguer d'une tuile d'outil. */
class GroupTile(ctx: android.content.Context, val entry: HomeEntry.Group) : IconTile(
    ctx,
    HomeTool(HomeGroupStyle.icon(entry.id), entry.label, entry.tiles.joinToString(" · ") { it.label }, entry.summary, entry.on, entry.warn, entry.id) {},
    TvStyle.dp(ctx, 236), HomeGroupStyle.LABEL_SP, HomeGroupStyle.STATUS_SP, 60, TvStyle.dp(ctx, 232), ringIdle = true,
)

/** Une tuile d'outil, en grande taille, pour la grille. */
class GridTile(ctx: android.content.Context, val tool: HomeTool) :
    IconTile(ctx, tool, TvStyle.dp(ctx, 280), HomeGroupStyle.LABEL_SP, HomeGroupStyle.STATUS_SP, 56, TvStyle.dp(ctx, 200))

/**
 * La grille d'un groupe : en-tête (nom du groupe), 3 colonnes, description de l'outil focalisé en bas. Zone de sécurité 5 %.
 * Les flèches sont rendues à la logique ([castbridge.core.tv.home.HomeGrid.move]) et toujours consommées : le focus ne sort jamais de la grille.
 */
class GridPanel(private val act: Activity, private val container: FrameLayout) {
    private val dp = { v: Int -> TvStyle.dp(act, v) }
    private var tiles: List<GridTile> = emptyList()
    var focusedTileId: String? = null; private set
    val visible get() = container.visibility == View.VISIBLE && container.childCount > 0

    /** [onKey] : une flèche vue d'une tuile (indice courant, touche) ; [onOpen] : OK sur la tuile [index]. */
    fun show(title: String, tools: List<HomeTool>, focusIndex: Int, onKey: (Int, HomeKey) -> Unit, onOpen: (Int) -> Unit) {
        container.removeAllViews()
        val dm = act.resources.displayMetrics
        val padX = (dm.widthPixels * 0.05f).toInt(); val padY = (dm.heightPixels * 0.05f).toInt()
        val root = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xF20A0F1E.toInt()); setPadding(padX, padY, padX, padY) }
        root.addView(TextView(act).apply { text = title; textSize = 34f; typeface = TvFonts.bold; setTextColor(Color.WHITE) })
        val hint = TextView(act).apply { text = "OK : ouvrir   ·   RETOUR : fermer le groupe"; textSize = TvStyle.Type.BODY; setTextColor(TvStyle.TEXT2); setPadding(0, dp(2), 0, dp(10)) }
        root.addView(hint)
        val desc = TextView(act).apply { textSize = TvStyle.Type.BODY; setTextColor(TvStyle.TEXT2); minLines = 2; maxLines = 2 }
        val grid = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        val made = ArrayList<GridTile>()
        tools.chunked(HomeGrid.COLUMNS).forEach { rowTools ->
            val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
            for (c in 0 until HomeGrid.COLUMNS) {
                val t = rowTools.getOrNull(c)
                if (t == null) { row.addView(View(act), LinearLayout.LayoutParams(0, 1, 1f)); continue }
                val index = made.size
                val v = GridTile(act, t).also { made.add(it) }
                v.setOnFocusChangeListener { view, has ->
                    view.animate().scaleX(if (has) 1.06f else 1f).scaleY(if (has) 1.06f else 1f).setDuration(120).start()
                    if (has) { focusedTileId = t.id; desc.text = t.description + (t.status?.let { "  —  $it" } ?: "") }
                }
                v.setOnClickListener { onOpen(index) }
                v.setOnKeyListener { _, code, ev ->
                    val key = when (code) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> HomeKey.LEFT; KeyEvent.KEYCODE_DPAD_RIGHT -> HomeKey.RIGHT
                        KeyEvent.KEYCODE_DPAD_UP -> HomeKey.UP; KeyEvent.KEYCODE_DPAD_DOWN -> HomeKey.DOWN
                        else -> null
                    }
                    if (key == null) false else { if (ev.action == KeyEvent.ACTION_DOWN) onKey(index, key); true }      // consommée même au bord : jamais de fuite de focus
                }
                row.addView(v, LinearLayout.LayoutParams(0, dp(200), 1f).apply { setMargins(dp(10), dp(8), dp(10), dp(8)) })
            }
            grid.addView(row)
        }
        tiles = made
        root.addView(ScrollView(act).apply { isVerticalScrollBarEnabled = false; clipChildren = false; addView(grid) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(desc)
        container.addView(root, FrameLayout.LayoutParams(-1, -1))
        container.visibility = View.VISIBLE
        focus(focusIndex)
    }

    fun focus(index: Int) { tiles.getOrNull(HomeGrid.clamp(index, tiles.size))?.requestFocus() }

    fun hide() { container.removeAllViews(); container.visibility = View.GONE; tiles = emptyList(); focusedTileId = null }
}
