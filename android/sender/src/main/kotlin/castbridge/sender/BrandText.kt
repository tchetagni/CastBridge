package castbridge.sender

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import castbridge.core.brand.Brand

/**
 * Marque blanche (docs/WHITE-LABEL.md) : dans ce paquet, `Text(String)` passe par [Brand.t] avant de dessiner, donc le nom de la marque
 * remplace « CastBridge » / « CastBridge-TV » sans toucher chaque écran. Un fichier qui importe `androidx.compose.material3.*` voit cette fonction
 * (le même paquet l'emporte sur l'import générique) ; pour CastBridge, [Brand.t] rend le texte tel quel.
 * Les `Text(AnnotatedString)` restent ceux de Material 3.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) = M3Text(Brand.t(text), modifier, color, fontSize, fontStyle, fontWeight, fontFamily, letterSpacing, textDecoration, textAlign, lineHeight, overflow, softWrap, maxLines, minLines, onTextLayout, style)

/**
 * Carte (non cliquable) du téléphone : comme `Card` de Material 3, plus, pour une marque qui définit `color.cardBorder`, un liseré 1 dp et une
 * ombre légère en thème clair. Sans ce rôle (CastBridge) rien ne change : mêmes valeurs par défaut que Material 3. Les cartes cliquables
 * (`onClick`) restent celles de Material 3.
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.material3.CardDefaults.shape,
    colors: androidx.compose.material3.CardColors = androidx.compose.material3.CardDefaults.cardColors(),
    elevation: androidx.compose.material3.CardElevation? = null,
    border: androidx.compose.foundation.BorderStroke? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val edge = castbridge.core.brand.BrandPalette.Light.CARD_BORDER
    val lightTheme = androidx.compose.material3.MaterialTheme.colorScheme.background.let { it.red + it.green + it.blue >= 1.5f }
    val branded = edge != null && lightTheme
    androidx.compose.material3.Card(
        modifier, shape, colors,
        elevation ?: if (branded) androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp) else androidx.compose.material3.CardDefaults.cardElevation(),
        border ?: if (branded) androidx.compose.foundation.BorderStroke(1.dp, Color(edge!!)) else null,
        content,
    )
}
