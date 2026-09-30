package castbridge.receiver

import android.content.Context
import android.graphics.Typeface

/**
 * Typefaces of the charte (branding/fonts, latin subsets in res/font): Bricolage Grotesque for titles, Inter for the text,
 * Sora for the quiz, Manrope for chess. Loaded once (TvApp); until then, or if a font cannot be read, the system font.
 */
object TvFonts {
    @Volatile var body: Typeface = Typeface.DEFAULT; private set
    @Volatile var bold: Typeface = Typeface.DEFAULT_BOLD; private set
    @Volatile var display: Typeface = Typeface.DEFAULT_BOLD; private set
    @Volatile var quiz: Typeface = Typeface.DEFAULT; private set
    @Volatile var quizBold: Typeface = Typeface.DEFAULT_BOLD; private set
    @Volatile var chess: Typeface = Typeface.DEFAULT; private set
    @Volatile var chessBold: Typeface = Typeface.DEFAULT_BOLD; private set

    fun init(ctx: Context) {
        fun fam(id: Int): Typeface? = runCatching { ctx.resources.getFont(id) }.getOrNull()
        fun bold(t: Typeface?, w: Int): Typeface? = t?.let { if (android.os.Build.VERSION.SDK_INT >= 28) Typeface.create(it, w, false) else Typeface.create(it, Typeface.BOLD) }
        fam(R.font.cb_inter)?.let { body = it; bold = bold(it, 700) ?: bold }
        fam(R.font.cb_bricolage_grotesque)?.let { display = bold(it, 800) ?: display }
        fam(R.font.cb_sora)?.let { quiz = it; quizBold = bold(it, 700) ?: quizBold }
        fam(R.font.cb_manrope)?.let { chess = it; chessBold = bold(it, 700) ?: chessBold }
    }

    fun body(@Suppress("UNUSED_PARAMETER") ctx: Context) = body
    fun display(@Suppress("UNUSED_PARAMETER") ctx: Context) = display
}
