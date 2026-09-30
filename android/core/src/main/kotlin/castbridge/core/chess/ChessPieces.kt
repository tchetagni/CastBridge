package castbridge.core.chess

/**
 * The pieces as vector shapes (SVG path syntax, 100 × 100 box, base at the bottom), drawn by the TV (Canvas), the phone
 * (Compose) and the web page (Path2D): one design everywhere, no image file, crisp at any size.
 * Each piece is a list of layers: [Layer.body] shapes are filled with the piece colour and outlined; [Layer.detail]
 * shapes are lines or small marks in the outline colour.
 */
object ChessPieces {
    data class Layer(val path: String, val body: Boolean)

    private const val BASE = "M20,80 L80,80 L82,90 L18,90 Z"

    val SHAPES: Map<Int, List<Layer>> = mapOf(
        Piece.PAWN to listOf(
            Layer("M50,18 A12,12 0 1 1 49.9,18 Z", true),
            Layer("M40,40 L60,40 L57,46 C57,58 64,68 70,78 L30,78 C36,68 43,58 43,46 Z", true),
            Layer(BASE, true)),
        Piece.ROOK to listOf(
            Layer("M26,16 L36,16 L36,24 L45,24 L45,16 L55,16 L55,24 L64,24 L64,16 L74,16 L74,36 L26,36 Z", true),
            Layer("M32,38 L68,38 L66,72 L34,72 Z", true),
            Layer("M26,72 L74,72 L74,80 L26,80 Z", true),
            Layer(BASE, true),
            Layer("M32,37 L68,37", false)),
        Piece.KNIGHT to listOf(
            Layer("M30,80 L72,80 C74,62 72,40 62,26 C56,18 48,14 44,10 L42,18 C35,21 30,27 26,34 L18,46 C16,51 20,56 26,53 L33,48 C38,48 42,46 46,43 C40,54 30,62 30,80 Z", true),
            Layer(BASE, true),
            Layer("M41,27 A2.8,2.8 0 1 1 40.9,27 Z", false),
            Layer("M60,28 C66,42 68,58 66,76", false)),
        Piece.BISHOP to listOf(
            Layer("M50,4 A5,5 0 1 1 49.9,4 Z", true),
            Layer("M50,14 C62,24 66,36 60,50 L40,50 C34,36 38,24 50,14 Z", true),
            Layer("M38,50 L62,50 L62,56 L38,56 Z", true),
            Layer("M42,56 L58,56 C58,64 64,72 68,78 L32,78 C36,72 42,64 42,56 Z", true),
            Layer(BASE, true),
            Layer("M46,40 L57,27", false)),
        Piece.QUEEN to listOf(
            Layer("M20,26 L31,54 L35,22 L44,52 L50,16 L56,52 L65,22 L69,54 L80,26 L71,70 L29,70 Z", true),
            Layer("M20,26 m-4.5,0 a4.5,4.5 0 1 0 9,0 a4.5,4.5 0 1 0 -9,0 Z", true),
            Layer("M35,22 m-4.5,0 a4.5,4.5 0 1 0 9,0 a4.5,4.5 0 1 0 -9,0 Z", true),
            Layer("M50,16 m-4.5,0 a4.5,4.5 0 1 0 9,0 a4.5,4.5 0 1 0 -9,0 Z", true),
            Layer("M65,22 m-4.5,0 a4.5,4.5 0 1 0 9,0 a4.5,4.5 0 1 0 -9,0 Z", true),
            Layer("M80,26 m-4.5,0 a4.5,4.5 0 1 0 9,0 a4.5,4.5 0 1 0 -9,0 Z", true),
            Layer("M29,70 L71,70 L74,80 L26,80 Z", true),
            Layer(BASE, true)),
        Piece.KING to listOf(
            Layer("M46,4 L54,4 L54,11 L61,11 L61,18 L54,18 L54,26 L46,26 L46,18 L39,18 L39,11 L46,11 Z", true),
            Layer("M50,27 C63,27 78,31 78,44 C78,56 67,63 65,72 L35,72 C33,63 22,56 22,44 C22,31 37,27 50,27 Z", true),
            Layer("M33,72 L67,72 L70,80 L30,80 Z", true),
            Layer(BASE, true),
            Layer("M50,30 L50,70 M30,50 C42,44 58,44 70,50", false)),
    )

    /** Fill and outline colours: cream pieces for White, near-black for Black; the outline of each is the other tone. */
    const val WHITE_FILL = 0xFFF8F3E6.toInt()
    const val WHITE_LINE = 0xFF1B1B1B.toInt()
    const val BLACK_FILL = 0xFF222222.toInt()
    const val BLACK_LINE = 0xFFF0EBDD.toInt()

    /** Board colours (light / dark squares), last move, selection, check, legal-move marks. */
    const val LIGHT = 0xFFEED8B4.toInt()
    const val DARK = 0xFFB0835A.toInt()
    const val LAST_MOVE = 0x80E8D84A.toInt()
    const val SELECTED = 0xA05FB0FF.toInt()
    const val CHECK = 0xD0E0302A.toInt()
    const val HINT = 0x7A1B3B1B

    /** The shapes as JSON (for the web page): {"1":[["M…",1],…],…}. */
    fun json(): String = SHAPES.entries.joinToString(",", "{", "}") { (t, layers) ->
        "\"$t\":" + layers.joinToString(",", "[", "]") { "[\"${it.path}\",${if (it.body) 1 else 0}]" }
    }
}
