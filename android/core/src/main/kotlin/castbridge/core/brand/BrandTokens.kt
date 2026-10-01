// GÉNÉRÉ par branding/tools/gen_tokens.py depuis branding/design-tokens.json : ne pas modifier à la main.
package castbridge.core.brand

/** Tokens de la charte graphique CastBridge (couleurs ARGB, dp, ms). Pur Kotlin : partagé par :receiver, :sender et les tests. */
object BrandTokens {
    object Dark {
        const val BACKGROUND = 0xFF0A0F1E.toInt()
        const val BACKGROUND_ELEVATED = 0xFF10182E.toInt()
        const val SURFACE = 0xFF151D37.toInt()
        const val SURFACE_HIGH = 0xFF1B2542.toInt()
        const val OUTLINE = 0xFF2A3550.toInt()
        const val TEXT_HIGH = 0xFFF4F6FB.toInt()
        const val TEXT_MEDIUM = 0xFFB7C0D4.toInt()
        const val TEXT_LOW = 0xFF828DA2.toInt()
        const val PRIMARY = 0xFFF5B025.toInt()
        const val ON_PRIMARY = 0xFF171204.toInt()
        const val SECONDARY = 0xFF2E9E6B.toInt()
        const val ON_SECONDARY = 0xFF0A0F1E.toInt()
        const val ACCENT = 0xFFFF8A3D.toInt()
        const val FOCUS_RING = 0xFFFFE1A6.toInt()
    }
    object Light {
        const val BACKGROUND = 0xFFF7F8FC.toInt()
        const val SURFACE = 0xFFFFFFFF.toInt()
        const val SURFACE_VARIANT = 0xFFEDF0F7.toInt()
        const val OUTLINE = 0xFFD7DCE7.toInt()
        const val TEXT_HIGH = 0xFF111827.toInt()
        const val TEXT_MEDIUM = 0xFF3E4A61.toInt()
        const val TEXT_LOW = 0xFF656D7F.toInt()
        const val PRIMARY = 0xFF946219.toInt()
        const val PRIMARY_BRAND = 0xFFB7791F.toInt()
        const val ON_PRIMARY = 0xFFFFFFFF.toInt()
        const val SECONDARY = 0xFF1C7C53.toInt()
        const val ON_SECONDARY = 0xFFFFFFFF.toInt()
        const val ACCENT = 0xFFB05123.toInt()
        const val ACCENT_BRAND = 0xFFE4692E.toInt()
        const val FOCUS_RING = 0xFFB7791F.toInt()
    }
    object Semantic {
        const val SUCCESS_DARK = 0xFF35C08A.toInt()
        const val SUCCESS_LIGHT = 0xFF1C7C53.toInt()
        const val WARNING_DARK = 0xFFF5B025.toInt()
        const val WARNING_LIGHT = 0xFF9A6500.toInt()
        const val ERROR_DARK = 0xFFFF6B6B.toInt()
        const val ERROR_LIGHT = 0xFFC5343A.toInt()
        const val INFO_DARK = 0xFF6CB6FF.toInt()
        const val INFO_LIGHT = 0xFF1668C7.toInt()
    }
    object Castbridge {
        const val PRIMARY = 0xFFF5B025.toInt()
        const val BACKGROUND = 0xFF0A0F1E.toInt()
        const val SECONDARY = 0xFF2E9E6B.toInt()
        const val ACCENT = 0xFFFF8A3D.toInt()
    }
    object CastbridgeTv {
        const val PRIMARY = 0xFFF5B025.toInt()
        const val BACKGROUND = 0xFF0A0F1E.toInt()
    }
    object QuizDesMillions {
        const val PRIMARY = 0xFFFF5C39.toInt()
        const val SECONDARY = 0xFFFFB020.toInt()
        const val ACCENT = 0xFF27C7B0.toInt()
        const val BACKGROUND = 0xFF0B1B1E.toInt()
    }
    object Echecs {
        const val PRIMARY = 0xFF2FA96B.toInt()
        const val SECONDARY = 0xFFF2C14E.toInt()
        const val ACCENT = 0xFFE07B39.toInt()
        const val BACKGROUND = 0xFF0C1B14.toInt()
    }
    object Apprendre {
        const val PRIMARY = 0xFF3B82F6.toInt()
        const val SECONDARY = 0xFFF59E0B.toInt()
        const val ACCENT = 0xFF06B6D4.toInt()
        const val BACKGROUND = 0xFF070D1E.toInt()
        const val SURFACE = 0xFF101D42.toInt()
    }

    const val RADIUS_SM_DP = 8
    const val RADIUS_MD_DP = 12
    const val RADIUS_LG_DP = 16
    const val RADIUS_XL_DP = 24
    const val RADIUS_PILL_DP = 999
    const val SPACE_0_DP = 0
    const val SPACE_1_DP = 4
    const val SPACE_2_DP = 8
    const val SPACE_3_DP = 12
    const val SPACE_4_DP = 16
    const val SPACE_5_DP = 20
    const val SPACE_6_DP = 24
    const val SPACE_8_DP = 32
    const val SPACE_10_DP = 40
    const val SPACE_12_DP = 48
    const val SPACE_16_DP = 64
    const val SPACE_20_DP = 80
    const val SPACE_24_DP = 96
    const val DURATION_FAST_MS = 120L
    const val DURATION_BASE_MS = 200L
    const val DURATION_SLOW_MS = 320L
    val EASING_STANDARD = floatArrayOf(0.2f, 0f, 0f, 1f)
    val EASING_DECELERATE = floatArrayOf(0.05f, 0.7f, 0.1f, 1f)
    val EASING_ACCELERATE = floatArrayOf(0.3f, 0f, 1f, 1f)
    const val FOCUS_RING_WIDTH_DP = 3
    const val FOCUS_RING_OFFSET_DP = 2
    const val FOCUS_SCALE = 1.04f
    const val FOCUS_MOBILE_RING_WIDTH_DP = 2
    const val TV_DISPLAY_SIZE = 72  // px (canevas 1920)
    const val TV_DISPLAY_WEIGHT = 800
    const val TV_HEADLINE_SIZE = 48  // px (canevas 1920)
    const val TV_HEADLINE_WEIGHT = 700
    const val TV_TITLE_SIZE = 40  // px (canevas 1920)
    const val TV_TITLE_WEIGHT = 700
    const val TV_SUBTITLE_SIZE = 32  // px (canevas 1920)
    const val TV_SUBTITLE_WEIGHT = 600
    const val TV_BODY_SIZE = 28  // px (canevas 1920)
    const val TV_BODY_WEIGHT = 400
    const val TV_BUTTON_SIZE = 28  // px (canevas 1920)
    const val TV_BUTTON_WEIGHT = 700
    const val TV_CAPTION_SIZE = 24  // px (canevas 1920)
    const val TV_CAPTION_WEIGHT = 500
    const val MOBILE_DISPLAY_SIZE = 32  // sp
    const val MOBILE_DISPLAY_WEIGHT = 800
    const val MOBILE_HEADLINE_SIZE = 28  // sp
    const val MOBILE_HEADLINE_WEIGHT = 700
    const val MOBILE_TITLE_SIZE = 22  // sp
    const val MOBILE_TITLE_WEIGHT = 700
    const val MOBILE_BODY_SIZE = 16  // sp
    const val MOBILE_BODY_WEIGHT = 400
    const val MOBILE_CAPTION_SIZE = 12  // sp
    const val MOBILE_CAPTION_WEIGHT = 500
    const val TV_MINIMUM_SIZE = 24  // px (canevas 1920)
}
